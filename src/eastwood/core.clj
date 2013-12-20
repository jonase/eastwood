(ns eastwood.core
  (:require [clojure.java.io :as io]
            [eastwood.analyze-ns :as analyze]
            [eastwood.util :as util]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.repl :as repl]
            [clojure.tools.namespace :as clj-ns]
            [eastwood.linters.misc :as misc]
            [eastwood.linters.deprecated :as deprecated]
            [eastwood.linters.unused :as unused]
            [eastwood.linters.typos :as typos])
  (:import [java.io PushbackReader]
           [clojure.lang LineNumberingPushbackReader]))


(def eastwood-url "https://github.com/jonase/eastwood")

(def ^:dynamic *eastwood-version*
  {:major 0, :minor 0, :incremental 3, :qualifier ""})

(defn eastwood-version []
  (let [{:keys [major minor incremental qualifier]} *eastwood-version*]
    (str major "." minor "." incremental
         (if (and qualifier (not= qualifier ""))
           (str "-" qualifier)
           ""))))

(defmacro timeit
  "Evaluates expr and returns a vector containing the expression's
return value followed by the time it took to evaluate in millisec."
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr
         elapsed-msec# (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
     [ret# elapsed-msec#]))

(def ^:private available-linters
  {:naked-use misc/naked-use
   :misplaced-docstrings misc/misplaced-docstrings
   :def-in-def misc/def-in-def
   :redefd-vars misc/redefd-vars
   :deprecations deprecated/deprecations
   :unused-fn-args unused/unused-fn-args
   :unused-private-vars unused/unused-private-vars
   :unused-namespaces unused/unused-namespaces
   :unused-ret-vals unused/unused-ret-vals
   :unused-ret-vals-in-try unused/unused-ret-vals-in-try
   :keyword-typos typos/keyword-typos})

(def ^:private default-linters
  #{;;:naked-use
    :misplaced-docstrings
    :def-in-def
    :redefd-vars
    :deprecations
    ;; :unused-fn-args    ; updated, but don't use it by default
    ;;:unused-private-vars
    ;; :unused-namespaces ; updated, but don't use it by default
    :unused-ret-vals
    :unused-ret-vals-in-try
    :keyword-typos
    })

(defn- lint [exprs kw]
  (try
    (doall ((available-linters kw) exprs))
    (catch Throwable e
      [e])))

;; Copied from clojure.repl/pst then slightly modified to print to
;; *out*, not *err*, and to use depth nil to print all stack frames.
(defn pst
  "Prints a stack trace of the exception, to the depth requested (the
entire stack trace if depth is nil).  Does not print ex-data."
  [^Throwable e depth]
  (println (str (-> e class .getSimpleName) " "
                (.getMessage e)))
  (let [st (.getStackTrace e)
        cause (.getCause e)]
    (doseq [el (remove #(#{"clojure.lang.RestFn" "clojure.lang.AFn"}
                         (.getClassName ^StackTraceElement %))
                       st)]
      (println (str \tab (repl/stack-element-str el))))
    (when cause
      (println "Caused by:")
      (pst cause (if depth
                   (min depth
                        (+ 2 (- (count (.getStackTrace cause))
                                (count st)))))))))

(defn maybe-unqualified-java-class-name? [x]
  (if-not (or (symbol? x) (string? x))
    false
    (let [^String x (if (symbol? x) (str x) x)]
      (and (>= (count x) 1)
           (== (.indexOf x ".") -1)   ; no dots
           (Character/isJavaIdentifierStart ^Character (nth x 0))
           (= (subs x 0 1)
              (str/upper-case (subs x 0 1))))))) ; first char is upper-case

(defn misplaced-primitive-tag? [x]
  (cond
   (= x clojure.core/byte)    {:prim-name "byte",    :supported-as-ret-hint false}
   (= x clojure.core/short)   {:prim-name "short",   :supported-as-ret-hint false}
   (= x clojure.core/int)     {:prim-name "int",     :supported-as-ret-hint false}
   (= x clojure.core/long)    {:prim-name "long",    :supported-as-ret-hint true}
   (= x clojure.core/boolean) {:prim-name "boolean", :supported-as-ret-hint false}
   (= x clojure.core/char)    {:prim-name "char",    :supported-as-ret-hint false}
   (= x clojure.core/float)   {:prim-name "float",   :supported-as-ret-hint false}
   (= x clojure.core/double)  {:prim-name "double",  :supported-as-ret-hint true}
   :else nil))

(defn print-ex-data-details [ns-sym opts ^Throwable exc]
  (let [dat (ex-data exc)
        msg (.getMessage exc)]
    ;; Print useful info about the exception so we might more
    ;; quickly learn how to enhance it.
    (println (format "Got exception with extra ex-data:"))
    (println (format "    msg='%s'" msg))
    (println (format "    (keys dat)=%s" (keys dat)))
    (when (contains? dat :tag-kind)
      (println (format "    (:tag-kind dat)=%s" (:tag-kind dat))))
    (when (contains? dat :ast)
      (println (format "     (:op ast)=%s" (-> dat :ast :op)))
      (when (contains? (:ast dat) :form)
        (println (format "    (class (-> dat :ast :form))=%s (-> dat :ast :form)="
                         (class (-> dat :ast :form))))
        (util/pprint-ast-node (-> dat :ast :form)))
      (util/pprint-ast-node (-> dat :ast)) )
    (pst exc nil)))

(defn handle-no-matching-arity-for-fn [ns-sym opts dat]
  (let [{:keys [arity fn]} dat
        {:keys [arglists form var]} fn]
    (println (format "Function on var %s called on line %d
with %s args, but it is only known to take one of the following args:"
                     var (-> form meta :line) arity))
    (println (format "    %s"
                     (str/join "\n    " arglists)))))

(defn handle-bad-tag [ns-sym opts ^Throwable exc]
  (let [dat (ex-data exc)
        {:keys [tag-kind ast]} dat
        msg (.getMessage exc)]
    (cond
     (or (and (= tag-kind :return-tag) (= (:op ast) :var))
         (and (= tag-kind :tag)        (= (:op ast) :invoke))
         (and (= tag-kind :tag)        (= (:op ast) :const)))
     (let [form (:form ast)
           form (if (= (:op ast) :invoke)
                  (first form)
                  form)
           tag (get ast tag-kind)]
       (println (format "A function, macro, protocol method, etc. named %s has been used here:"
                        form))
       (util/pprint-ast-node (meta form))
       (println (format "Wherever it is defined, or where it is called, it has a return type of %s"
                        tag))
       (cond
        (maybe-unqualified-java-class-name? tag)
        (do
          (println (format
"This appears to be a Java class name with no package path.
Library tools.analyzer, on which Eastwood relies, cannot analyze such files.
If this definition is easy for you to change, we recommend you prepend it with
a full package path name, e.g. java.net.URI
Otherwise import the class by adding a line like this to your ns statement:
    (:import (java.net URI))"))
          :no-more-details-needed)

        (misplaced-primitive-tag? tag)
        (let [{:keys [prim-name supported-as-ret-hint]} (misplaced-primitive-tag? tag)
              form (if (var? form)
                     (name (.sym ^clojure.lang.Var form))
                     form)
              good-prim-name (if supported-as-ret-hint
                               prim-name
                               "long")]
          (println (format
"It has probably been defined with a primitive return type tag on the var name,
like this:
    (defn ^%s %s [args] ...)" prim-name form))
          (println (format
"Clojure 1.5.1 does not handle such tags correctly, and gives no error messages."))
          (when-not supported-as-ret-hint
            (println (format
"Also, it only supports return type hints of long and double, not %s" prim-name)))
          (println (format
"Library tools.analyzer, on which Eastwood relies, cannot analyze such files.
Type hints for unsupported types (other than long or double) should be deleted.
For supported type hints, it should be just before the arg vector, like this:
    (defn %s ^%s [args] ...)" form good-prim-name))
          (println (format
"or if there are multiple arities defined, like this:
    (defn %s (^%s [arg1] ...) (^%s [arg1 arg2] ...))" form good-prim-name good-prim-name))
          :no-more-details-needed)
        
        :else
        (do
          (println (format "dbgx tag=%s (class tag)=%s (str tag)='%s' boolean?=%s long?=%s"
                           tag
                           (class tag)
                           (str tag)
                           (= tag clojure.core/boolean)
                           (= tag clojure.core/long)))
          :show-more-details)))
     
     (and (= tag-kind :tag)
          (#{:local :binding} (:op ast)))
     (let [{:keys [form tag]} ast]
       (println (format "Local name '%s' has been given a type tag '%s' here:"
                        form tag))
       (util/pprint-ast-node (meta tag))
       (cond
        (maybe-unqualified-java-class-name? tag)
        (do
          (println (format
"This appears to be a Java class name with no package path.
Library tools.analyzer, on which Eastwood relies, cannot analyze such files.
Either prepend it with a full package path name, e.g. java.net.URI
Otherwise, import the Java class, e.g. add a line like this to the ns statement:
    (:import (java.net URI))"))
          :no-more-details-needed)

        (symbol? tag)
        (do
          (println (format
"This is a symbol, but does not appear to be a Java class.  Whatever it
is, library tools.analyzer, on which Eastwood relies, cannot analyze
such files.

Cases like this have been seen in some Clojure code that used the
library test.generative.  That library uses tag metadata in an unusual
way that might be changed to avoid this.  See
http://dev.clojure.org/jira/browse/TGEN-5 for details if you are
curious.

If you are not using test.generative, and are able to provide the code
that you used that gives this error to the Eastwood developers for
further investigation, please file an issue on the Eastwood Github
page at %s"
          eastwood-url))
          :no-more-details-needed)

        (sequential? tag)
        (do
          (println (format
"This appears to be a Clojure form to be evaluated.
Library tools.analyzer, on which Eastwood relies, cannot analyze such files.

If you have this expression in your source code, it is recommended to
replace it with a constant type tag if you can, or create an issue on
the Eastwood project Github page with details of your situation for
possible future enhancement to Eastwood: %s

If you do not see any expression like this in your source code, cases
like this have been seen in programs that used the library
test.generative.  That library uses tag metadata in an unusual way
that might be changed to avoid this.  See
http://dev.clojure.org/jira/browse/TGEN-5 for details if you are
curious." eastwood-url))
          :no-more-details-needed)

        :else
        (do
          (println (format "dbgx for case tag-kind=:tag :op :local tag=%s (class form)=%s (sequential? form) form="
                           (class form) (sequential? form) tag))
          (util/pprint-ast-node form)
          :show-more-details)))

     :else
     (do
       (print-ex-data-details ns-sym opts exc)
       :show-more-details))))

(defn handle-ex-data [ns-sym opts ^Throwable exc]
  (let [dat (ex-data exc)
        msg (.getMessage exc)]
    (cond
     (= msg "No matching arity found for function: ")
     (handle-no-matching-arity-for-fn ns-sym opts dat)
     (contains? dat :tag-kind)
     (handle-bad-tag ns-sym opts exc)
     :else
     (do
       (print-ex-data-details ns-sym opts exc)
       :show-more-details))))

(defn show-exception [ns-sym opts e]
  (if (ex-data e)
    (handle-ex-data ns-sym opts e)
    (do
      (pst e nil)
      :show-more-details)))

(defn lint-ns [ns-sym linters opts]
  (println "== Linting" ns-sym "==")
  (let [[{:keys [analyze-results exception exception-phase exception-form]}
         analyze-time-msec]
        (timeit (analyze/analyze-ns ns-sym :opt opts))
        print-time? (or (contains? (:debug opts) :all)
                        (contains? (:debug opts) :time))]
    (when print-time?
      (println (format "Analysis took %.1f millisec" analyze-time-msec)))
    (doseq [linter linters]
      (let [[results time-msec] (timeit (lint analyze-results linter))]
        (doseq [result results]
          (if (instance? Throwable result)
            (do
              (println (format "Exception thrown by linter %s on namespace %s"
                               linter ns-sym))
              (show-exception ns-sym opts result))
            (pp/pprint result))
          (println))
        (when print-time?
          (println (format "Linter %s took %.1f millisec" linter time-msec)))))
    (when exception
      (println "Exception thrown during phase" exception-phase
               "of linting namespace" ns-sym)
      (when (= (show-exception ns-sym opts exception) :show-more-details)
        (println "\nThe following form was being processed during the exception:")
        (binding [*print-level* 7
                  *print-length* 50]
          (pp/pprint exception-form))
        (println "\nShown again with metadata for debugging:")
        (util/pprint-ast-node exception-form))
      (println
"\nAn exception was thrown while analyzing namespace" ns-sym "
Lint results may be incomplete.  If there are compilation errors in
your code, try fixing those.  If not, check above for info on the
exception."))))

;; TBD: Think about what to do with exception in this
;; function.  Probably just return it to the caller in a map
;; containing it and the current ret value on different keys.
(defn lint-ns-noprint [ns-sym linters opts]
  (let [{:keys [exception analyze-results]}
        (analyze/analyze-ns ns-sym :opt opts)]
    (mapcat #(lint analyze-results %) linters)))

(defn run-eastwood [opts]
  ;; Note: Preserve order of (:namespaces opts) if specified, in case
  ;; it is important.
  (binding [*out* (java.io.PrintWriter. *out* true)]
    (let [namespaces (distinct
                      (or (:namespaces opts)
                          (mapcat #(-> % io/file clj-ns/find-namespaces-in-dir)
                                  (concat (:source-paths opts) (:test-paths opts)))))
          excluded-namespaces (set (:exclude-namespaces opts))
          namespaces (remove excluded-namespaces namespaces)
          linters (set (or (:linters opts)
                           default-linters))
          excluded-linters (set (:exclude-linters opts))
          add-linters (set (:add-linters opts))
          linters-requested (-> (set/difference linters excluded-linters)
                                (set/union add-linters))
          known-linters (set (keys available-linters))
          linters-unavailable (set/difference (set/union linters-requested
                                                         excluded-linters)
                                              known-linters)
          linters (set/intersection linters-requested known-linters)]
      (println (format "== Eastwood %s Clojure %s JVM %s"
                       (eastwood-version)
                       (clojure-version)
                       (get (System/getProperties) "java.version")))
      (when (seq linters-unavailable)
        (println (format "The following requested or excluded linters are unknown: %s"
                         (seq linters-unavailable)))
        (println (format "Known linters are: %s"
                         (seq (sort known-linters)))))
      (when (seq linters)
        (doseq [namespace namespaces]
          (try
            (lint-ns namespace linters opts)
            (catch RuntimeException e
              (println "Linting failed:")
              (pst e nil))))))))
