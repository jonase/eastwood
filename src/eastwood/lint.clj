(ns eastwood.lint
  (:require [clojure.java.io :as io]
            [eastwood.analyze-ns :as analyze]
            [eastwood.util :as util]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.file :as file]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.track :as track]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.dir :as dir]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.move :as move]
            [eastwood.linters.misc :as misc]
            [eastwood.linters.deprecated :as deprecated]
            [eastwood.linters.unused :as unused]
            [eastwood.linters.typetags :as typetags]
            [eastwood.linters.typos :as typos])
  (:import [java.io File PushbackReader]
           [clojure.lang LineNumberingPushbackReader]))


(def eastwood-url "https://github.com/jonase/eastwood")

(def ^:dynamic *eastwood-version*
  {:major 0, :minor 2, :incremental 1, :qualifier "SNAPSHOT"})

(defn eastwood-version []
  (let [{:keys [major minor incremental qualifier]} *eastwood-version*]
    (str major "." minor "." incremental
         (if (and qualifier (not= qualifier ""))
           (str "-" qualifier)
           ""))))


(defmulti error-msg
  "Given a map describing an Eastwood error result, which should
always have at least the keys :err and :err-data, return a string
describing the error."
  :err)


(declare file-warn-info)

(defn replace-path-in-compiler-error
  [msg opt]
  (let [[match pre _ path
         line-col post] (re-matches #"((Reflection|Boxed math) warning), (.*?)(:\d+:\d+)(.*)"
                                    msg)
        url (and match (io/resource path))
        inf (and url (file-warn-info url (:cwd opt)))]
    (if inf
      ;; The filename:line:col should be first in the output
      (str (:uri-or-file-name inf) line-col ": " pre post)
      msg)))


(defn make-default-msg-cb [wrtr]
  (fn default-msg-cb [info]
    (binding [*out* wrtr]
      (println (:msg info))
      (flush))))


(defn make-default-eval-msg-cb
  ([wrtr]
     (make-default-eval-msg-cb wrtr {}))
  ([wrtr opt]
     (fn default-msg-cb [info]
       (let [orig-msg (:msg info)
             msg (if (= :eval-err (:kind info))
                   (replace-path-in-compiler-error orig-msg opt)
                   orig-msg)]
         (binding [*out* wrtr]
           (println msg)
           (flush))))))


(defn make-default-dirs-scanned-cb [wrtr]
  (fn default-dirs-scanned-cb [info]
    (binding [*out* wrtr]
      (println "Directories scanned for source files:")
      (print " ")
      (doseq [d (:dirs-scanned info)]
        (print " ")
        (print (:uri-or-file-name d)))
      (println)
      (flush))))


(def empty-ordered-lint-warning-map-v1
  (util/ordering-map [:linter
                      :msg
                      :file
                      :line
                      :column]))

(def empty-ordered-lint-warning-map-v2
  (util/ordering-map [:file
                      :line
                      :column
                      :linter
                      :msg
                      :uri-or-file-name]))

;; Use the option :warning-format :map-v1 to get linter warning maps
;; as they were generated in Eastwood 0.1.0 thru 0.1.4, intended only
;; for comparing output from later versions against those versions
;; more easily.

(def last-cwd-shown (atom nil))

(defn make-default-lint-warning-cb [wrtr]
  (fn default-lint-warning-cb [info]
    (binding [*out* wrtr]
      (let [warning-format (or (-> info :opt :warning-format)
                               :location-list-v1)
            i (case warning-format
                :map-v1 (into empty-ordered-lint-warning-map-v1
                              (select-keys (:warn-data info)
                                           [:linter :msg :file :line :column]))
                :map-v2 (into empty-ordered-lint-warning-map-v2
                              (select-keys (:warn-data info)
                                           [:linter :msg :file :line :column
                                            :uri-or-file-name
                                            ;; :uri
                                            ;; :namespace-sym
                                            ]))
                :location-list-v1 (:warn-data info))]
        (if (= warning-format :location-list-v1)
          (do
            (let [cwd (-> info :opt :cwd)]
              (when (not= cwd @last-cwd-shown)
                (reset! last-cwd-shown cwd)
                (println (format "Entering directory `%s'" cwd))))
            (println (format "%s:%s:%s: %s: %s"
                             (-> i :uri-or-file-name str)
                             ;; Emacs compilation-mode default regex's
                             ;; do not recognize warning lines with
                             ;; nil instead of decimal numbers for
                             ;; line/col number.  Make up values if we
                             ;; don't know them.
                             (or (-> i :line) "1")
                             (or (-> i :column) "1")
                             (name (-> i :linter))
                             (-> i :msg))))
          (do
            (pp/pprint i)
            (println)
            (flush)))))))

(defn make-default-debug-ast-cb [wrtr]
  (fn default-debug-ast-cb [info]
    (binding [*out* wrtr]
      (util/pprint-ast-node (:ast info))
      (flush))))

(defn make-default-form-cb [wrtr]
  (fn [{:keys [event form]}]
    (binding [*out* wrtr]
      (case event
        :begin-file (println (format "\n\n== Analyzing file '%s'\n" form))
        :form (util/pprint-form form)))))


(defn assert-debug-form-cb-has-proper-keys [info]
  (util/assert-keys info [:event :opt])
  (case (:event info)
    :form (util/assert-keys info [:form])
    :begin-file (util/assert-keys info [:filename])))


(defn assert-cb-has-proper-keys [info]
  (case (:kind info)
    :error     (util/assert-keys info [:msg :opt])
    :dirs-scanned (util/assert-keys info [:dirs-scanned :opt])
    :lint-warning (util/assert-keys info [:warn-data :opt])
    :note      (util/assert-keys info [:msg :opt])
    :eval-out  (util/assert-keys info [:msg :opt])
    :eval-err  (util/assert-keys info [:msg :opt])
    :debug     (util/assert-keys info [:msg :opt])
    :debug-ast (util/assert-keys info [:ast :opt])
    :debug-form-read    (assert-debug-form-cb-has-proper-keys info)
    :debug-form-emitted (assert-debug-form-cb-has-proper-keys info)))


(defn make-eastwood-cb [{:keys [error dirs-scanned lint-warning note
                                eval-out eval-err
                                debug debug-ast
                                debug-form-read debug-form-emitted]}]
  (fn eastwood-cb [info]
    (assert-cb-has-proper-keys info)
    (case (:kind info)
      :error     (error info)
      :dirs-scanned (dirs-scanned info)
      :lint-warning (lint-warning info)
      :note      (note info)
      :eval-out  (eval-out info)
      :eval-err  (eval-err info)
      :debug     (debug info)
      :debug-ast (debug-ast info)
      :debug-form-read    (when debug-form-read
                            (debug-form-read info))
      :debug-form-emitted (when debug-form-emitted
                            (debug-form-emitted info)))))


(defmacro timeit
  "Evaluates expr and returns a vector containing the expression's
return value followed by the time it took to evaluate in millisec."
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr
         elapsed-msec# (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
     [ret# elapsed-msec#]))


;; Note: Linters below with nil for a value, e.g. :no-ns-form-found,
;; can be enabled/disabled from the opt map like other linters, but
;; they are a bit different in their implementation as they have no
;; separate function to call on each namespace.  They are done very
;; early, and are not specific to a namespace.

(def linter-info
  [
   {:name :no-ns-form-found,          :enabled-by-default true,
    :fn nil}
   {:name :non-clojure-file,          :enabled-by-default false,
    :fn nil}
   {:name :misplaced-docstrings,      :enabled-by-default true,
    :fn misc/misplaced-docstrings}
   {:name :deprecations,              :enabled-by-default true,
    :fn deprecated/deprecations}
   {:name :redefd-vars,               :enabled-by-default true,
    :fn misc/redefd-vars}
   {:name :def-in-def,                :enabled-by-default true,
    :fn misc/def-in-def}
   {:name :wrong-arity,               :enabled-by-default true,
    :fn misc/wrong-arity}
   {:name :bad-arglists,              :enabled-by-default true,
    :fn misc/bad-arglists}
   {:name :local-shadows-var,         :enabled-by-default true,
    :fn misc/local-shadows-var}
   {:name :suspicious-test,           :enabled-by-default true,
    :fn typos/suspicious-test}
   {:name :suspicious-expression,     :enabled-by-default true,
    :fn typos/suspicious-expression}
   {:name :constant-test,             :enabled-by-default true,
    :fn typos/constant-test}
   {:name :unused-ret-vals,           :enabled-by-default true,
    :fn unused/unused-ret-vals}
   {:name :unused-ret-vals-in-try,    :enabled-by-default true,
    :fn unused/unused-ret-vals-in-try}
   {:name :unused-private-vars,       :enabled-by-default false,
    :fn unused/unused-private-vars}
   {:name :unused-fn-args,            :enabled-by-default false,
    :fn unused/unused-fn-args}
   {:name :unused-locals,             :enabled-by-default false,
    :fn unused/unused-locals}
   {:name :unused-namespaces,         :enabled-by-default false,
    :fn unused/unused-namespaces}
   {:name :unused-meta-on-macro,      :enabled-by-default true,
    :fn unused/unused-meta-on-macro}
   {:name :unlimited-use,             :enabled-by-default true,
    :fn misc/unlimited-use}
   {:name :wrong-ns-form,             :enabled-by-default true,
    :fn misc/wrong-ns-form}
   {:name :wrong-tag,                 :enabled-by-default true,
    :fn typetags/wrong-tag}
   {:name :keyword-typos,             :enabled-by-default false,
    :fn typos/keyword-typos}
   {:name :non-dynamic-earmuffs,      :enabled-by-default false,
    :fn misc/non-dynamic-earmuffs}
   ])


(def linter-name->fn (into {} (for [{:keys [name fn]} linter-info]
                                [name fn])))

(def default-linters
  (->> linter-info
       (filter :enabled-by-default)
       (map :name)))


(defn- lint [exprs kw opt]
  (if-let [lint-fn (linter-name->fn kw)]
    (try
      (doall (lint-fn exprs opt))
      (catch Throwable e
        [e]))))

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
  (condp = x
   clojure.core/byte    {:prim-name "byte",    :supported-as-ret-hint false}
   clojure.core/short   {:prim-name "short",   :supported-as-ret-hint false}
   clojure.core/int     {:prim-name "int",     :supported-as-ret-hint false}
   clojure.core/long    {:prim-name "long",    :supported-as-ret-hint true}
   clojure.core/boolean {:prim-name "boolean", :supported-as-ret-hint false}
   clojure.core/char    {:prim-name "char",    :supported-as-ret-hint false}
   clojure.core/float   {:prim-name "float",   :supported-as-ret-hint false}
   clojure.core/double  {:prim-name "double",  :supported-as-ret-hint true}
   nil))

(defn print-ex-data-details [ns-sym opts ^Throwable exc]
  (let [error-cb (util/make-msg-cb :error opts)
        dat (ex-data exc)
        msg (.getMessage exc)]
    ;; Print useful info about the exception so we might more
    ;; quickly learn how to enhance it.
    (error-cb (format "Got exception with extra ex-data:"))
    (error-cb (format "    msg='%s'" msg))
    (error-cb (format "    (keys dat)=%s" (keys dat)))
    (when (contains? dat :ast)
      (error-cb (format "     (:op ast)=%s" (-> dat :ast :op)))
      (when (contains? (:ast dat) :form)
        (error-cb (format "    (class (-> dat :ast :form))=%s (-> dat :ast :form)="
                         (class (-> dat :ast :form))))
        (error-cb (with-out-str (util/pprint-form (-> dat :ast :form)))))
      (error-cb (with-out-str (util/pprint-form (-> dat :ast)))))
    (when (contains? dat :form)
      (error-cb (format "    (:form dat)="))
      (error-cb (with-out-str (util/pprint-form (:form dat)))))
    (util/pst exc nil error-cb)))

(defn handle-values-of-env [ns-sym opts ^Throwable exc]
  (let [error-cb (util/make-msg-cb :error opts)
        dat (ex-data exc)
        {:keys [form]} dat]
    (error-cb (format "Eastwood cannot analyze code that uses the values of &env in a macro expansion."))
    (error-cb (format "See https://github.com/jonase/eastwood#explicit-use-of-clojure-environment-env"))
    :show-more-details))

(defn handle-bad-dot-form [ns-sym opts ^Throwable exc]
  (let [error-cb (util/make-msg-cb :error opts)
        dat (ex-data exc)
        {:keys [form]} dat]
    (error-cb (format "Java interop calls should be of the form TBD, but found this instead (line %s):"
                      (-> form first meta :line)))
    (error-cb (with-out-str
                ;; TBD: Replace this binding with util/pprint-form call?
                (binding [*print-level* 7
                          *print-length* 50]
                  (pp/pprint form))))
    :no-more-details-needed))

(defn handle-bad-tag [ns-sym opts ^Throwable exc]
  (let [error-cb (util/make-msg-cb :error opts)
        dat (ex-data exc)
        ast (:ast dat)]
    (cond
     (#{:var :invoke :const} (:op ast))
     (let [form (:form ast)
           form (if (= (:op ast) :invoke)
                  (first form)
                  form)
           tag (or (-> form meta :tag)
                   (:tag ast))]
       (error-cb (format "A function, macro, protocol method, var, etc. named %s has been used here:"
                         form))
       (error-cb (with-out-str (util/pprint-form (meta form))))
       (error-cb (format "Wherever it is defined, or where it is called, it has a type of %s"
                         tag))
       (cond
        (maybe-unqualified-java-class-name? tag)
        (do
          (error-cb (format
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
          (error-cb (format
"It has probably been defined with a primitive return type tag on the var name,
like this:
    (defn ^%s %s [args] ...)" prim-name form))
          (error-cb (format
"Clojure 1.5.1 and 1.6.0 do not handle such tags as you probably expect.
They silently treat this as a tag of the *function* named clojure.core/%s"
prim-name))
          (when-not supported-as-ret-hint
            (error-cb (format
"Also, it only supports return type hints of long and double, not %s" prim-name)))
          (error-cb (format
"Library tools.analyzer, on which Eastwood relies, cannot analyze such files.
If you wish the function to have a primitive return type, this is only
supported for types long and double, and the type tag must be given just
before the argument vector, like this:
    (defn %s ^%s [args] ...)" form good-prim-name))
          (error-cb (format
"or if there are multiple arities defined, like this:
    (defn %s (^%s [arg1] ...) (^%s [arg1 arg2] ...))" form good-prim-name good-prim-name))
          (error-cb (format
"If you wish to use a primitive type tag on the Var name, Clojure will
only use that if the function is called and its return value is used
as an argument in a Java interop call.  In such situations, the type
tag can help choose a Java method and often avoid reflection.  If that
is what you want, you must specify the tag like so:
    (defn ^{:tag '%s} %s [args] ...)" prim-name form))
          :no-more-details-needed)
        
        :else
        (do
          (error-cb (format "dbgx tag=%s (class tag)=%s (str tag)='%s' boolean?=%s long?=%s"
                           tag
                           (class tag)
                           (str tag)
                           (= tag clojure.core/boolean)
                           (= tag clojure.core/long)))
          :show-more-details)))
     
     (#{:local :binding} (:op ast))
     (let [form (:form ast)
           tag (-> form meta :tag)]
       (error-cb (format "Local name '%s' has been given a type tag '%s' here:"
                         form tag))
       (error-cb (with-out-str (util/pprint-form (meta tag))))
       (cond
        (maybe-unqualified-java-class-name? tag)
        (do
          (error-cb (format
"This appears to be a Java class name with no package path.
Library tools.analyzer, on which Eastwood relies, cannot analyze such files.
Either prepend it with a full package path name, e.g. java.net.URI
Otherwise, import the Java class, e.g. add a line like this to the ns statement:
    (:import (java.net URI))"))
          :no-more-details-needed)

        (symbol? tag)
        (do
          (error-cb (format
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
          (error-cb (format
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
          (error-cb (format "dbgx for case :op %s tag=%s (class form)=%s (sequential? form)=%s form="
                           (:op ast) tag (class form) (sequential? form)))
          (error-cb (with-out-str (util/pprint-form form)))
          :show-more-details)))

     :else
     (do
       (print-ex-data-details ns-sym opts exc)
       :show-more-details))))

(defn show-exception [ns-sym opts ^Throwable exc]
  (let [dat (ex-data exc)
        msg (.getMessage exc)]
    (cond
     (re-find #" cannot be cast to clojure\.lang\.Compiler\$LocalBinding" msg)
     (handle-values-of-env ns-sym opts exc)

     (and (re-find #"method name must be a symbol, had:" msg)
          (contains? dat :form))
     (handle-bad-dot-form ns-sym opts exc)
     
     (re-find #"Class not found: " msg)
     (handle-bad-tag ns-sym opts exc)

     :else
     (do
       (if dat
         (print-ex-data-details ns-sym opts exc)
         (util/pst exc nil (util/make-msg-cb :error opts)))
       :show-more-details))))


(defn ^java.net.URI to-uri [x]
  (cond (instance? java.net.URI x) x
        (instance? java.io.File x) (.toURI ^java.io.File x)
        (instance? java.net.URL x) (.toURI ^java.net.URL x)
        (string? x) (.toURI (File. ^String x))
        :else (assert false)))


(defn file-warn-info [f cwd-file]
  (let [uri (to-uri f)
        ;; file-or-nil will be nil if uri is a URI like the following,
        ;; which cannot be converted to a File:
        ;; #<URI jar:file:/Users/jafinger/.m2/repository/org/clojure/clojure/1.6.0/clojure-1.6.0.jar!/clojure/test/junit.clj>
        file-or-nil (try (File. uri)
                         (catch IllegalArgumentException e
                           nil))
        uri-or-rel-file-str
        (if (nil? file-or-nil)
          uri
          (let [file-str (str file-or-nil)
                cwd-str (if cwd-file (str cwd-file File/separator) "")]
            (if (.startsWith file-str cwd-str)
              (subs file-str (count cwd-str))
              file-str)))]
    {:uri uri
     :uri-or-file-name uri-or-rel-file-str}))


(defn namespace-info [ns-sym cwd-file]
  (let [uri (to-uri (analyze/uri-for-ns ns-sym))]
    (merge
     {:namespace-sym ns-sym}
     (file-warn-info uri cwd-file))))


(defn lint-ns [ns-sym linters opts warning-count exception-count]
  (let [cb (:callback opts)
        error-cb (util/make-msg-cb :error opts)
        note-cb (util/make-msg-cb :note opts)
        ns-info (namespace-info ns-sym (:cwd opts))]
    (note-cb (str "== Linting " ns-sym " =="))
    (let [[{:keys [analyze-results exception exception-phase exception-form]}
           analyze-time-msec]
          (timeit (analyze/analyze-ns ns-sym :opt opts))
          print-time? (util/debug? #{:time} opts)]
      (when print-time?
        (note-cb (format "Analysis took %.1f millisec" analyze-time-msec)))
      (doseq [linter linters]
        (let [[results time-msec] (timeit (lint analyze-results linter opts))]
          (doseq [result results]
            (if (instance? Throwable result)
              (do
                (error-cb (format "Exception thrown by linter %s on namespace %s"
                                  linter ns-sym))
                (swap! exception-count inc)
                (show-exception ns-sym opts result))
              (do
                (swap! warning-count inc)
                (cb {:kind :lint-warning,
                     :warn-data (merge result ns-info)
                     :opt opts}))))
          (when print-time?
            (note-cb (format "Linter %s took %.1f millisec"
                             linter time-msec)))))
      (when exception
        (swap! exception-count inc)
        (error-cb (str "Exception thrown during phase " exception-phase
                       " of linting namespace " ns-sym))
        (when (= (show-exception ns-sym opts exception) :show-more-details)
          (error-cb "\nThe following form was being processed during the exception:")
          ;; TBD: Replace this binding with util/pprint-form variation
          ;; that does not print metadata?
          (error-cb (with-out-str (binding [*print-level* 7
                                            *print-length* 50]
                                    (pp/pprint exception-form))))
          (error-cb "\nShown again with metadata for debugging (some metadata elided for brevity):")
          (error-cb (with-out-str (util/pprint-form exception-form))))
        (error-cb
         (str "\nAn exception was thrown while analyzing namespace " ns-sym " 
Lint results may be incomplete.  If there are compilation errors in
your code, try fixing those.  If not, check above for info on the
exception."))
        exception))))


;; If an exception occurs during analyze, re-throw it.  This will
;; cause any test written that calls lint-ns-noprint to fail, unless
;; it expects the exception.
(defn lint-ns-noprint [ns-sym linters opts]
  (let [lint-warnings (atom [])
        warning-count (atom 0)
        exception-count (atom 0)
        cb (fn cb [info]
             (case (:kind info)
               :lint-warning (swap! lint-warnings conj (:warn-data info))
               (:eval-out :eval-err) (println (:msg info))
               :default))
        opts (assoc opts :callback cb)
        exception (lint-ns ns-sym linters opts warning-count exception-count)]
    (if exception
      (throw exception)
      @lint-warnings)))


(defn unknown-ns-keywords [namespaces known-ns-keywords desc]
  (let [keyword-set (set (filter keyword? namespaces))
        unknown-ns-keywords (set/difference keyword-set known-ns-keywords)]
    (if (empty? unknown-ns-keywords)
      nil
      {:err :unknown-ns-keywords,
       :err-data {:for-option desc
                  :unknown-ns-keywords unknown-ns-keywords
                  :allowed-ns-keywords known-ns-keywords}})))


(defmethod error-msg :unknown-ns-keywords [err-info]
  (let [{:keys [for-option unknown-ns-keywords allowed-ns-keywords]}
        (:err-data err-info)]
    (with-out-str
      (println (format "The following keywords appeared in the namespaces specified after %s :"
                       for-option))
      (println (format "    %s" (seq unknown-ns-keywords)))
      (println (format "The only keywords allowed in this list of namespaces are: %s"
                       (seq allowed-ns-keywords))))))


(defn filename-to-ns [fname]
  (-> fname
      (str/replace-first #"\.clj$" "")
      (str/replace "_" "-")
      (str/replace File/separator ".")
      symbol))

(defn ns-to-filename [namespace]
  (str (-> namespace
           str
           (str/replace "-" "_")
           (str/replace "." File/separator))
       ".clj"))

(defn filename-namespace-mismatches [dir-name-strs]
  (let [files-by-dir (into {} (for [dir-name-str dir-name-strs]
                                [dir-name-str (:clojure-files
                                               (#'dir/find-files [dir-name-str]))]))
        fd-by-dir (util/map-vals (fn [files]
                                   (#'file/files-and-deps files))
                                 files-by-dir)]
    (into
     {}
     (for [[dir fd] fd-by-dir,
           [f namespace] (:filemap fd)
           :let [fname (str f)
                 fname (if (.startsWith fname dir)
                         (subs fname (inc (count dir))) ; inc to get rid of a separator
                         fname)
                 desired-ns (filename-to-ns fname)
                 desired-fname (ns-to-filename namespace)]
           :when (not= fname desired-fname)]
       [fname {:dir dir, :namespace namespace,
               :recommended-fname desired-fname,
               :recommended-namespace desired-ns}]))))

(defn canonical-filename
  "Returns the canonical file name for the given file name.  A
canonical file name is platform dependent, but is both absolute and
unique.  See the Java docs for getCanonicalPath for some more details,
and the examples below.

    http://docs.oracle.com/javase/7/docs/api/java/io/File.html#getCanonicalPath%28%29

Examples:

Context: A Linux or Mac OS X system, where the current working
directory is /Users/jafinger/clj/dolly

user=> (ns/canonical-filename \"README.md\")
\"/Users/jafinger/clj/dolly/README.md\"

user=> (ns/canonical-filename \"../../Documents/\")
\"/Users/jafinger/Documents\"

user=> (ns/canonical-filename \"../.././clj/../Documents/././\")
\"/Users/jafinger/Documents\"

Context: A Windows 7 system, where the current working directory is
C:\\Users\\jafinger\\clj\\dolly

user=> (ns/canonical-filename \"README.md\")
\"C:\\Users\\jafinger\\clj\\dolly\\README.md\"

user=> (ns/canonical-filename \"..\\..\\Documents\\\")
\"C:\\Users\\jafinger\\Documents\"

user=> (ns/canonical-filename \"..\\..\\.\\clj\\..\\Documents\\.\\.\\\")
\"C:\\Users\\jafinger\\Documents\""
  [fname]
  (let [^java.io.File f (if (instance? java.io.File fname)
                          fname
                          (java.io.File. ^String fname))]
    (.getCanonicalPath f)))


(defn nss-in-dirs [dir-name-strs opt warning-count]
  (let [dir-name-strs (map canonical-filename dir-name-strs)
        mismatches (filename-namespace-mismatches dir-name-strs)]
    (if (seq mismatches)
      {:err :namespace-filename-mismatch
       :err-data {:mismatches mismatches}}
      (let [tracker (if (seq dir-name-strs)
                      (apply dir/scan-all (track/tracker) dir-name-strs)
                      ;; Use empty tracker if dir-name-strs is empty.
                      ;; Calling dir/scan-all will use complete Java
                      ;; classpath if called with an empty sequence.
                      (track/tracker))
            files-no-ns-form-found
            (when (some #{:no-ns-form-found} (:enabled-linters opt))
              (let [tfiles (-> tracker
                               :eastwood.copieddeps.dep9.clojure.tools.namespace.dir/files
                               set)
                    tfilemap (-> tracker
                                 :eastwood.copieddeps.dep9.clojure.tools.namespace.file/filemap
                                 keys
                                 set)]
                (set/difference tfiles tfilemap)))]
        {:err nil
         :dirs (map #(file-warn-info % (:cwd opt)) dir-name-strs)
         :non-clojure-files
         (:eastwood.copieddeps.dep9.clojure.tools.namespace.dir/non-clojure-files
          tracker)
         :no-ns-form-found-files files-no-ns-form-found
         :namespaces
         (:eastwood.copieddeps.dep9.clojure.tools.namespace.track/load
          tracker)}))))


(defmethod error-msg :namespace-filename-mismatch [err-info]
  (let [{:keys [mismatches]} (:err-data err-info)]
    (with-out-str
      (println "The following file(s) contain ns forms with namespaces that do not correspond
with their file names:")
      (doseq [[fname {:keys [dir namespace recommended-fname recommended-namespace]}]
              mismatches]
        (println (format "Directory: %s" dir))
        (println (format "    File                 : %s" fname))
        (println (format "    has namespace        : %s" namespace))
        (if (= namespace recommended-namespace)
          ;; Give somewhat clearer message in this case
          (println (format "    should be in file    : %s" recommended-fname))
          (do
            (println (format "    should have namespace: %s" recommended-namespace))
            (println (format "    or should be in file : %s" recommended-fname)))))
      (println "
No other linting checks will be performed until these problems have
been corrected.

The 'should have namespace' and 'should be in file' messages above are
merely suggestions.  It may be better in your case to rename both the
file and namespace to avoid name collisions."))))


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
;; violates dependency order according to the ns form contents.  No
;; warning will be detected or printed about this.

;; TBD: It would be nice if the default behavior would instead be to
;; put the specified namespaces into an order that honors all declared
;; dependencies between namespaces.  If this is implemented, it might
;; also be nice (perhaps only for debugging purposes) to implement a
;; keyword :force-order that preserves the specified namespace order
;; regardless of dependencies.

;; TBD: Abort with an easily understood error message if a namespace
;; is given that cannot be found.

(defn opts->namespaces [opts warning-count]
  (let [namespaces1 (distinct (:namespaces opts))
        sp-included? (some #{:source-paths} namespaces1)
        tp-included? (some #{:test-paths} namespaces1)
        excluded-namespaces (set (:exclude-namespaces opts))]
    ;; Return an error if any keywords appear in the namespace lists
    ;; that are not recognized.
    (or
     (unknown-ns-keywords namespaces1 #{:source-paths :test-paths}
                          ":namespaces")
     (unknown-ns-keywords excluded-namespaces #{:source-paths :test-paths}
                          ":exclude-namespaces")
     ;; If keyword :source-paths occurs in namespaces1 or
     ;; excluded-namespaces, replace it with all namespaces found in
     ;; the directories in (:source-paths opts), in an order that
     ;; honors dependencies, and similarly for :test-paths.
     ;; nss-in-dirs traverses part of the file system, so only call it
     ;; once for each of :source-paths and :test-paths, and only if
     ;; needed.
     (let [all-ns (concat namespaces1 excluded-namespaces)
           sp (if (some #{:source-paths} all-ns)
                (nss-in-dirs (:source-paths opts) opts warning-count))
           tp (if (some #{:test-paths} all-ns)
                (nss-in-dirs (:test-paths opts) opts warning-count))]
       (cond
        (:err sp) sp
        (:err tp) tp
        :else
        (let [source-paths (:namespaces sp)
              test-paths (:namespaces tp)
              namespaces (replace-ns-keywords namespaces1
                                              source-paths test-paths)
              namespaces (distinct namespaces)
              excluded-namespaces (set (replace-ns-keywords excluded-namespaces
                                                            source-paths
                                                            test-paths))
              namespaces (remove excluded-namespaces namespaces)]
          {:err nil,
           :namespaces namespaces,
           :dirs (distinct (concat (if sp-included? (:dirs sp))
                                   (if tp-included? (:dirs tp))))
           :no-ns-form-found-files
           (concat
            (if sp-included? (:no-ns-form-found-files sp))
            (if tp-included? (:no-ns-form-found-files tp)))
           :non-clojure-files (concat
                               (if sp-included? (:non-clojure-files sp))
                               (if tp-included? (:non-clojure-files tp)))}))))))


(defn opts->linters [opts linter-name->fn default-linters]
  (let [linters (set (:linters opts))
        excluded-linters (set (:exclude-linters opts))
        add-linters (set (:add-linters opts))
        linters-requested (-> (set/difference linters excluded-linters)
                              (set/union add-linters))
        known-linters (set (keys linter-name->fn))
        unknown-linters (set/difference (set/union linters-requested
                                                   excluded-linters)
                                        known-linters)
        linters (set/intersection linters-requested known-linters)]
    (if (and (seq unknown-linters)
             (not (:disable-linter-name-checks opts)))
      {:err :unknown-linter,
       :err-data {:unknown-linters unknown-linters
                  :known-linters known-linters}}
      ;; else
      {:err nil, :linters linters})))


(defmethod error-msg :unknown-linter [err-info]
  (let [{:keys [unknown-linters known-linters]} (:err-data err-info)]
    (with-out-str
      (println (format "The following requested or excluded linters are unknown: %s"
                       (seq unknown-linters)))
      (println (format "Known linters are: %s"
                       (seq (sort known-linters)))))))


(defmethod error-msg :exception-thrown [err-info]
  (let [{:keys [unanalyzed-namespaces last-namespace]} (:err-data err-info)]
    ;; Don't report that we stopped analyzing early if we stop on the
    ;; last namespace (it is especially bad form to print the long
    ;; message if only one namespace was being linted).
    (if (seq unanalyzed-namespaces)
      (format "
Stopped analyzing namespaces after %s
due to exception thrown.  %d namespaces left unanalyzed.

If you wish to force continuation of linting after an exception in one
namespace, make the option map key :continue-on-exception have the
value true.

WARNING: This can cause exceptions to be thrown while analyzing later
namespaces that would not otherwise occur.  For example, if a function
is defined in the namespace where the first exception occurs, after
the exception, it will never be evaluated.  If the function is then
used in namespaces analyzed later, it will be undefined, causing
error.
"
            last-namespace
            (count unanalyzed-namespaces))

      "
Exception thrown while analyzing last namespace.
"
      )))


(defn eastwood-core
  "Lint a sequence of namespaces using a specified collection of linters.

Prerequisites:
+ eastwood.lint namespace is in your classpath
+ TBD: Eastwood resources directory is in your classpath
+ eastwood.lint namespace and its dependencies have been loaded.

Arguments:
+ TBD: to be documented

Side effects:
+ Reads source files, analyzes them, generates Clojure forms from
  analysis results, and eval's those forms (which if there are bugs in
  tools.analyzer or tools.analyzer.jvm, may not be identical to the
  original forms read.  If require'ing your source files launches the
  missiles, so will this.
+ Does create-ns on all namespaces specified, even if an exception
  during linting causes this function to return before reading all of
  them.  See the code for why.
+ Should not print output to any output files/streams/etc., unless
  this occurs due to eval'ing the code being linted.

Return value:
+ TBD
"
  [opts]
  (let [warning-count (atom 0)
        exception-count (atom 0)
        cb (:callback opts)
        {:keys [linters] :as m1} (opts->linters opts linter-name->fn
                                                default-linters)
        opts (assoc opts :enabled-linters linters)
        opts (assoc opts :warning-enable-config
                    (util/init-warning-enable-config opts))

        {:keys [namespaces dirs no-ns-form-found-files
                non-clojure-files] :as m2}
          (opts->namespaces opts warning-count)]
    (when (seq dirs)
      (cb {:kind :dirs-scanned, :dirs-scanned dirs, :opt opts}))
    (when (some #{:no-ns-form-found} (:enabled-linters opts))
      (doseq [f no-ns-form-found-files]
        (swap! warning-count inc)
        (cb {:kind :lint-warning,
             :warn-data (let [inf (file-warn-info f (:cwd opts))]
                          (merge
                           {:linter :no-ns-form-found
                            :msg (format "No ns form was found in file '%s'.  It will not be linted."
                                         (:uri-or-file-name inf))}
                           inf))
             :opt opts})))
    (when (some #{:non-clojure-file} (:enabled-linters opts))
      (doseq [f non-clojure-files]
        (swap! warning-count inc)
        (cb {:kind :lint-warning,
             :warn-data (let [inf (file-warn-info f (:cwd opts))]
                          (merge
                           {:linter :non-clojure-file
                            :msg (format "Non-Clojure file '%s'.  It will not be linted."
                                         (:uri-or-file-name inf))}
                           inf))
             :opt opts})))
    (cond
     (:err m1) m1
     (:err m2) m2
     :else
     (let [error-cb (util/make-msg-cb :error opts)
           debug-cb (util/make-msg-cb :debug opts)
           continue-on-exception? (:continue-on-exception opts)
           stopped-on-exc (atom false)]
       (when (util/debug? #{:ns} opts)
         (debug-cb (format "Namespaces to be linted:"))
         (doseq [n namespaces]
           (debug-cb (format "    %s" n))))
       ;; Create all namespaces to be analyzed.  This can help in some
       ;; (unusual) situations, such as when namespace A requires B,
       ;; so Eastwood analyzes B first, but eval'ing B assumes that
       ;; A's namespace has been created first because B contains
       ;; (alias 'x 'A)
       (doseq [n namespaces]
         (create-ns n))
       (when (seq (:enabled-linters opts))
         (loop [namespaces namespaces]
           (when-first [namespace namespaces]
             (let [e (try
                       (lint-ns namespace (:enabled-linters opts) opts
                                warning-count exception-count)
                       (catch RuntimeException e
                         (error-cb "Linting failed:")
                         (util/pst e nil error-cb)
                         e))]
               (if (or continue-on-exception?
                       (not (instance? Throwable e)))
                 (recur (next namespaces))
                 (reset! stopped-on-exc
                         {:exception e
                          :last-namespace namespace
                          :unanalyzed-namespaces (next namespaces)}))))))
       (merge
        {:err nil
         :warning-count @warning-count
         :exception-count @exception-count}
        (if @stopped-on-exc
          {:err :exception-thrown
           :err-data @stopped-on-exc}))))))


;; Test Eastwood for a while with messages being written to file
;; "east-out.txt", to see if I catch everything that was going to
;; *out* with callback functions or return values.

;; Use the java.io.PrintWriter shown below to write messages to the
;; same place as Eastwood does in version 0.1.4.

(defn make-default-cb [opts]
  (let [;;wrtr (io/writer "east-out.txt")   ; see comment above
        wrtr (java.io.PrintWriter. *out* true)
        warn-wrtr (if (contains? opts :out)
                    (io/writer (:out opts))
                    wrtr)
        default-msg-cb (make-default-msg-cb wrtr)
        eval-out-err-msg-cb (make-default-eval-msg-cb wrtr opts)
        default-dirs-scanned-cb (make-default-dirs-scanned-cb wrtr)
        default-lint-warning-cb (make-default-lint-warning-cb warn-wrtr)
        default-debug-ast-cb (make-default-debug-ast-cb wrtr)
        
        [form-read-cb form-emitted-cb]
        (if (util/debug? #{:compare-forms} opts)
          [ (make-default-form-cb (io/writer "forms-read.txt"))
            (make-default-form-cb (io/writer "forms-emitted.txt")) ]
          [])]
    (make-eastwood-cb {:error default-msg-cb
                       :dirs-scanned default-dirs-scanned-cb
                       :lint-warning default-lint-warning-cb
                       :note default-msg-cb
                       :eval-out eval-out-err-msg-cb
                       :eval-err eval-out-err-msg-cb
                       :debug default-msg-cb
                       :debug-ast default-debug-ast-cb
                       :debug-form-read form-read-cb
                       :debug-form-emitted form-emitted-cb})))


(defn last-options-map-adjustments [opts]
  (let [opts (update-in opts [:debug] set)
        opts (merge {:cwd (.getCanonicalFile (io/file "."))
                     :linters default-linters
                     :namespaces [:source-paths :test-paths]}
                    opts)
        ;; special case 'merge': If _neither_ of :source-paths or
        ;; :test-paths were specified in the options map, then set
        ;; _one_ of them to a list of all the directories on the
        ;; classpath.  If either is present, leave them both as is.
        ;; Both of these should always have a value if invoked from
        ;; Leiningen command line, so this is only for when invoked
        ;; directly, e.g. from the REPL, intended as a convenience.
        opts (if (or (contains? opts :source-paths)
                     (contains? opts :test-paths))
               opts
               (assoc opts :source-paths
                      (#'eastwood.copieddeps.dep9.clojure.tools.namespace.dir/dirs-on-classpath)))
        ;; The following value is equivalent to (merge {:callback ...}
        ;; opts), but it does not calculate the value unless needed.
        opts (if (contains? opts :callback)
               opts
               (assoc opts :callback (make-default-cb opts)))]
    opts))


(defn eastwood [opts]
  ;; Use caller-provided :cwd and :callback values if provided
  (let [opts (last-options-map-adjustments opts)
        _ (when (util/debug? #{:options} opts)
            (println "\nOptions map after filling in defaults:")
            (pp/pprint (into (sorted-map) opts)))
        error-cb (util/make-msg-cb :error opts)
        note-cb (util/make-msg-cb :note opts)
        debug-cb (util/make-msg-cb :debug opts)
        _ (do
            (note-cb (format "== Eastwood %s Clojure %s JVM %s"
                             (eastwood-version)
                             (clojure-version)
                             (get (System/getProperties) "java.version")))
            (when (util/debug? #{:compare-forms} opts)
              (debug-cb "Writing files forms-read.txt and forms-emitted.txt")))
        {:keys [err warning-count exception-count] :as ret}
        (eastwood-core opts)]
    (when err
      (error-cb (error-msg ret)))
    (when (number? warning-count)
      (note-cb (format "== Warnings: %d (not including reflection warnings)  Exceptions thrown: %d"
                       warning-count exception-count)))
    (if (or err (and (number? warning-count)
                     (or (> warning-count 0) (> exception-count 0))))
      {:some-warnings true}
      {:some-warnings false})))


(defn eastwood-from-cmdline [opts]
  (let [ret (eastwood opts)]
    (if (:some-warnings ret)
      ;; Exit with non-0 exit status for the benefit of any shell
      ;; scripts invoking Eastwood that want to know if there were no
      ;; errors, warnings, or exceptions.
      (System/exit 1)
      ;; Eastwood does not use future, pmap, or clojure.shell/sh now
      ;; (at least not yet), but it may evaluate code that does when
      ;; linting a project.  Call shutdown-agents to avoid the
      ;; 1-minute 'hang' that would otherwise occur.
      (shutdown-agents))))
