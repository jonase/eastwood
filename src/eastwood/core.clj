(ns eastwood.core
  (:require [clojure.java.io :as io]
            [eastwood.analyze-ns :as analyze]
            [eastwood.util :as util]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.repl :as repl]
            ;; The latest version of tools.namespace (0.2.4 as of this
            ;; writing) has function find-namespaces-in-dir in
            ;; namespace c.t.n.find, but that namespace does not exist
            ;; in older versions of tools.namespace.  If we continue
            ;; to use the deprecated version in namespace c.t.n, then
            ;; it will avoid the problem of not being able to find
            ;; namespace c.t.n.find when running Eastwood on a project
            ;; that uses an older version of tools.namespace.
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.dir :as dir]
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
  {
   :misplaced-docstrings misc/misplaced-docstrings
   :deprecations deprecated/deprecations
   :redefd-vars misc/redefd-vars
   :def-in-def misc/def-in-def
   :wrong-arity misc/wrong-arity
   :suspicious-test typos/suspicious-test
   :suspicious-expression typos/suspicious-expression
   :unused-ret-vals unused/unused-ret-vals
   :unused-ret-vals-in-try unused/unused-ret-vals-in-try
   :unused-private-vars unused/unused-private-vars
   :unused-fn-args unused/unused-fn-args
   :unused-namespaces unused/unused-namespaces
   :naked-use misc/naked-use
   :keyword-typos typos/keyword-typos
   })

(def ^:private default-linters
  #{
    :misplaced-docstrings
    :deprecations
    :redefd-vars
    :def-in-def
    :wrong-arity
    :suspicious-test
    :suspicious-expression
    :unused-ret-vals
    :unused-ret-vals-in-try
    ;;:unused-private-vars  ; not yet updated to tools.analyzer(.jvm)
    ;; :unused-fn-args      ; updated, but don't use it by default
    ;; :unused-namespaces   ; updated, but don't use it by default
    ;;:naked-use            ; not yet updated to tools.analyzer(.jvm)
    ;; :keyword-typos       ; updated, but don't use it by default
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
    (when (contains? dat :form)
      (println (format "    (:form dat)="))
      (util/pprint-ast-node (:form dat)))
    (pst exc nil)))

(defn handle-bad-dot-form [ns-sym opts ^Throwable exc]
  (let [dat (ex-data exc)
        {:keys [form]} dat
        msg (.getMessage exc)]
    (println (format "Java interop calls should be of the form TBD, but found this instead (line %s):"
                     (-> form first meta :line)))
    (binding [*print-level* 7
              *print-length* 50]
      (pp/pprint form))
    :no-more-details-needed))

(defn handle-bad-tag [ns-sym opts ^Throwable exc]
  (let [dat (ex-data exc)
        {:keys [tag-kind ast]} dat
        msg (.getMessage exc)]
    (cond
     (or (and (= tag-kind :return-tag) (= (:op ast) :var))
         (and (= tag-kind :tag)        (= (:op ast) :var))
         (and (= tag-kind :tag)        (= (:op ast) :invoke))
         (and (= tag-kind :tag)        (= (:op ast) :const)))
     (let [form (:form ast)
           form (if (= (:op ast) :invoke)
                  (first form)
                  form)
           tag (get ast tag-kind)]
       (println (format "A function, macro, protocol method, var, etc. named %s has been used here:"
                        form))
       (util/pprint-ast-node (meta form))
       (println (format "Wherever it is defined, or where it is called, it has a type of %s"
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
     (and (re-find #"method name must be a symbol, had:" msg)
          (contains? dat :form))
     (handle-bad-dot-form ns-sym opts exc)
     
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

(defn lint-ns [ns-sym linters opts warning-count exception-count]
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
              (swap! exception-count inc)
              (show-exception ns-sym opts result))
            (do
              (swap! warning-count inc)
              (pp/pprint result)))
          (println))
        (when print-time?
          (println (format "Linter %s took %.1f millisec" linter time-msec)))))
    (when exception
      (swap! exception-count inc)
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

(defn unknown-ns-keywords [namespaces known-ns-keywords desc]
  (let [keyword-set (set (filter keyword? namespaces))
        unknown-ns-keywords (set/difference keyword-set known-ns-keywords)]
    (if (empty? unknown-ns-keywords)
      nil
      {:err :unknown-ns-keywords,
       :msg
       (with-out-str
         (println (format "The following keywords appeared in the namespaces specified after %s :"
                          desc))
         (println (format "    %s" (seq unknown-ns-keywords)))
         (println (format "The only keywords allowed in this list of namespaces are: %s"
                          (seq known-ns-keywords))))})))

(defn nss-in-dirs [dir-name-strs]
  (let [tracker (apply dir/scan-all (track/tracker) dir-name-strs)]
    (:clojure.tools.namespace.track/load tracker)))

(defn replace-ns-keywords [namespaces source-paths test-paths]
  (mapcat (fn [x]
            (if (keyword? x)
              (case x
                :source-paths source-paths
                :test-paths test-paths
                ;;:force-order []
                )
              [x]))
          namespaces))

;; If you do not specify :namespaces in the options, it defaults to
;; the same as if you specified [:source-paths :test-paths].  If you
;; specify a list of namespaces explicitly, perhaps mingled with
;; occurrences of :source-paths and/or :test-paths, then the
;; namespaces will be linted in the order you specify, even if this
;; violates dependency order according to the ns statement contents.
;; No warning will be detected or printed about this.

;; TBD: It would be nice if the default behavior would instead be to
;; put the specified namespaces into an order that honors all declared
;; dependencies between namespaces.  If this is implemented, it might
;; also be nice (perhaps only for debugging purposes) to implement a
;; keyword :force-order that preserves the specified namespace order
;; regardless of dependencies.

;; TBD: Abort with an easily understood error message if a namespace
;; is given that cannot be found.

(defn opts->namespaces [opts]
  (let [namespaces (distinct (or (:namespaces opts)
                                 [:source-paths :test-paths]))
        excluded-namespaces (set (:exclude-namespaces opts))]
    ;; Return an error if any keywords appear in the namespace lists
    ;; that are not recognized.
    (or
     (unknown-ns-keywords namespaces #{:source-paths :test-paths}
                          ":namespaces")
     (unknown-ns-keywords excluded-namespaces #{:source-paths :test-paths}
                          ":exclude-namespaces")
     ;; If keyword :source-paths occurs in namespaces or
     ;; excluded-namespaces, replace it with all namespaces found in
     ;; the directories in (:source-paths opts), in an order that
     ;; honors dependencies, and similarly for :test-paths.
     ;; nss-in-dirs traverses part of the file system, so only call it
     ;; once for each of :source-paths and :test-paths, and only if
     ;; needed.
     (let [source-paths (if (some #(= % :source-paths)
                                  (concat namespaces excluded-namespaces))
                          (nss-in-dirs (:source-paths opts)))
           test-paths (if (some #(= % :test-paths)
                                (concat namespaces excluded-namespaces))
                        (nss-in-dirs (:test-paths opts)))
           namespaces (replace-ns-keywords namespaces source-paths test-paths)
           namespaces (distinct namespaces)
           excluded-namespaces (set (replace-ns-keywords excluded-namespaces
                                                         source-paths
                                                         test-paths))
           namespaces (remove excluded-namespaces namespaces)]
       {:err nil, :namespaces namespaces}))))

(defn opts->linters [opts available-linters default-linters]
  (let [linters (set (or (:linters opts)
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
    (if (seq linters-unavailable)
      {:err :unknown-linter,
       :msg
       (with-out-str
         (println (format "The following requested or excluded linters are unknown: %s"
                          (seq linters-unavailable)))
         (println (format "Known linters are: %s"
                          (seq (sort known-linters)))))}
      ;; else
      {:err nil, :linters linters})))

(defn run-eastwood [opts]
  (binding [*out* (java.io.PrintWriter. *out* true)]
    (println (format "== Eastwood %s Clojure %s JVM %s"
                     (eastwood-version)
                     (clojure-version)
                     (get (System/getProperties) "java.version")))
    (let [{:keys [err msg namespaces]} (opts->namespaces opts)]
      (when err
        (print msg)
        (flush)
        (System/exit 1))
      (let [{:keys [err msg linters]} (opts->linters opts available-linters
                                                     default-linters)
            warning-count (atom 0)
            exception-count (atom 0)]
        (when err
          (print msg)
          (flush)
          (System/exit 1))
        (when (contains? (:debug opts) :all)
          (println (format "Namespaces to be linted:"))
          (doseq [n namespaces]
            (println (format "    %s" n))))
        (when (seq linters)
          (doseq [namespace namespaces]
            (try
              (lint-ns namespace linters opts warning-count exception-count)
              (catch RuntimeException e
                (println "Linting failed:")
                (pst e nil)))))
        (when (or (> @warning-count 0)
                  (> @exception-count 0))
          (println (format "== Warnings: %d (not including reflection warnings)  Exceptions thrown: %d"
                           @warning-count @exception-count))
          (flush)
          (System/exit 1))
        ;; Eastwood does not use future, pmap, or clojure.shell/sh now
        ;; (at least not yet), but it may evaluate code that does when
        ;; linting a project.  Call shutdown-agents to avoid the
        ;; 1-minute 'hang' that would otherwise occur.
        (shutdown-agents)))))
