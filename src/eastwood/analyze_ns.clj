(ns eastwood.analyze-ns
  (:refer-clojure :exclude [macroexpand-1])
  (:require [clojure.string :as string]
            [clojure.pprint :as pp]
            [eastwood.util :as util]
            [eastwood.passes :refer [propagate-def-name add-partly-resolved-forms reflect-validated]]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [eastwood.copieddeps.dep10.clojure.tools.reader :as tr]
            [eastwood.copieddeps.dep10.clojure.tools.reader.reader-types :as rts]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer :as ana :refer [analyze] :rename {analyze -analyze}]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer
             [ast :refer [postwalk prewalk cycling]]
             [utils :as utils]
             [env :as env]
             [passes :refer [schedule]]]
            [eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm :as ana.jvm]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.passes
             [source-info :refer [source-info]]
             [cleanup :refer [cleanup]]
             [elide-meta :refer [elide-meta]]
             [warn-earmuff :refer [warn-earmuff]]
             [collect-closed-overs :refer [collect-closed-overs]]
             [uniquify :refer [uniquify-locals]]]
            [eastwood.copieddeps.dep2.clojure.tools.analyzer.passes.jvm
             [box :refer [box]]
             [collect :refer [collect]]
             [constant-lifter :refer [constant-lift]]
             [clear-locals :refer [clear-locals]]
             [classify-invoke :refer [classify-invoke]]
             [validate :refer [validate]]
             [infer-tag :refer [infer-tag ensure-tag]]
             [validate-loop-locals :refer [validate-loop-locals]]
             [warn-on-reflection :refer [warn-on-reflection]]
             [emit-form :refer [emit-form]]]))

;; munge-ns, uri-for-ns, pb-reader-for-ns were copied from library
;; jvm.tools.analyzer, then later probably diverged from each other.

(defn ^:private munge-ns [ns-sym]
  (-> (name ns-sym)
      (string/replace "." "/")
      (string/replace "-" "_")
      (str ".clj")))

(defn uri-for-ns
  "Returns a URI representing the namespace. Throws an
  exception if URI not found."
  [ns-sym]
  (let [source-path (munge-ns ns-sym)
        uri (io/resource source-path)]
    (when-not uri
      (throw (Exception. (str "No file found for namespace " ns-sym))))
    uri))

(defn pb-reader-for-ns
  "Returns an IndexingReader for namespace ns-sym"
  [ns-sym]
  (let [uri (uri-for-ns ns-sym)]
    (rts/indexing-push-back-reader (java.io.PushbackReader. (io/reader uri))
                                   1 (munge-ns ns-sym))))

(defn all-ns-names-set []
  (set (map str (all-ns))))

(defn gen-interface-form? [form]
  (and (seq? form)
       (contains? #{'gen-interface 'clojure.core/gen-interface}
          (first form))))

;; Avoid macroexpand'ing a gen-interface form more than once, since it
;; causes an exception to be thrown.
(defn dont-expand-twice? [form]
  (gen-interface-form? form))

(defn pre-analyze-debug [asts form _env ns {:keys [debug] :as opt}]
  (let [print-normally? (some #{:all :forms} debug)
        pprint? (some #{:all :forms-pprint} debug)]
    (when (or print-normally? pprint?)
      (println (format "dbg pre-analyze #%d ns=%s (meta ns)=%s"
                       (count asts) (str ns) (meta ns)))
      (when pprint?
        (println "    form before macroexpand:")
        (pp/pprint form))
      (when print-normally?
        (println "    form before macroexpand, with metadata (some elided for brevity):")
        (util/pprint-meta-elided form))
      (println "\n    --------------------")
      (if (dont-expand-twice? form)
        (when print-normally?
          (println "    form is gen-interface, so avoiding macroexpand on it"))
        (let [exp (macroexpand form)]
          (when pprint?
            (println "    form after macroexpand:")
            (pp/pprint exp))
          (when print-normally?
            (println "    form after macroexpand, with metadata (some elided for brevity):")
            (util/pprint-meta-elided exp))))
      (println "\n    --------------------"))))

(defn post-analyze-debug [asts form ast ns {:keys [debug] :as opt}]
  (let [show-ast? (some #{:all :ast} debug)]
    (when (or show-ast? (some #{:progress} debug))
      (println (format "dbg anal'd %d ns=%s%s"
                       (count asts) (str ns)
                       (if show-ast? " ast=" ""))))
    (when show-ast?
      (util/pprint-ast-node ast))
    (when (:record-forms? opt)
      ;; TBD: Change this to macroexpand form, at least if
      ;; dont-expand-twice? returns false.
      (binding [*out* (:forms-read-wrtr opt)]
        (util/pprint-form form))
      (binding [*out* (:forms-emitted-wrtr opt)]
        (util/pprint-form (:form ast))))))

(defn begin-file-debug [filename ns opt]
  (when (:record-forms? opt)
    (binding [*out* (:forms-read-wrtr opt)]
      (println (format "\n\n== Analyzing file '%s'\n" filename)))
    (binding [*out* (:forms-emitted-wrtr opt)]
      (println (format "\n\n== Analyzing file '%s'\n" filename)))))

;; eastwood-passes is a cut-down version of run-passes in
;; tools.analyzer.jvm.  It eliminates phases that are not needed for
;; linting, and which can cause analysis to fail for code that we
;; would prefer to give linter warnings for, rather than throw an
;; exception.

(def eastwood-passes
  "Set of passes that will be run by default on the AST by #'run-passes"
  #{
    ;; Doing clojure.core/eval in analyze+eval already generates
    ;; reflection warnings from Clojure.  Doing it in tools.analyzer
    ;; also leads to duplicate warnings.
    ;;#'warn-on-reflection
    #'warn-earmuff

    #'uniquify-locals

    #'source-info
    #'elide-meta
    #'constant-lift

    #'clear-locals
    #'collect-closed-overs
    #'collect

    #'box

    #'validate-loop-locals
    #'validate
    #'infer-tag

    #'classify-invoke})

(def scheduled-eastwood-passes
  (schedule eastwood-passes))

(defn ^:dynamic run-passes
  "Function that will be invoked on the AST tree immediately after it has been constructed,
   by default set-ups and runs the default passes declared in #'default-passes"
  [ast]
  (scheduled-eastwood-passes ast))

(defn wrapped-exception? [result]
  (if (instance? eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm.ExceptionThrown result)
    (.e ^eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm.ExceptionThrown result)))

(defn remaining-forms [pushback-reader forms]
  (let [eof (reify)]
    (loop [forms forms]
      (let [form (tr/read pushback-reader nil eof)]
        (if (identical? form eof)
          forms
          (recur (conj forms form)))))))

(defn analyze-file
  "Takes a file path and optionally a pushback reader.  Returns a map
  with at least the following keys:

  :forms - a sequence of forms as read in, with any forms within a
      top-level do, or do forms nested within a top-level do,
      'flattened' to the top level themselves.  This sequence will
      include all forms in the file, as far as the file could be
      successfully read, even if an exception was thrown earlier than
      that during analysis or evaluation of the forms.

  :asts - a sequence of ASTs of the forms that were successfully
      analyzed without exception.  They correspond one-to-one with
      forms in the :forms sequence.

  :exception - nil if no exception occurred.  An Exception object if
      an exception was thrown during analysis, emit-form, or eval.

  If :exception is not nil, then the following keys will also be part
  of the returned map:

  :exception-phase - If an exception was thrown, this is a keyword
      indicating in what portion of analyze-file's operation this
      exception occurred.  Always :analyze+eval or :eval

  :exception-form - If an exception was thrown, the current form being
      processed when the exception occurred.

  Options:
  - :reader  a pushback reader to use to read the namespace forms
  - :opt     a map of analyzer options
    - :debug A set of keywords.
      - :all Enable all of the following debug messages.
      - :progress Print simple progress messages as analysis proceeds.
      - :ns Print all namespaces that exist according to (all-ns)
            before analysis begins, and then only when that set of
            namespaces changes after each form is analyzed.
      - :forms Print forms just before analysis, both before and after
               macroexpanding them.
      - :forms-pprint Pretty-print forms just before analysis, both
                      before and after macroexpanding them.
      - :ast Print complete ASTs just after analysis of each form.

  eg. (analyze-file \"my/ns.clj\" :opt {:debug-all true})"
  [source-path & {:keys [reader opt]}]
  (let [debug-ns (some #{:ns :all} (:debug opt))
        eof (reify)]
    (when debug-ns
      (println (format "all-ns before (analyze-file \"%s\") begins:"
                       source-path))
      (pp/pprint (sort (all-ns-names-set))))
    ;; If we eval a form that changes *ns*, I want it to go back to
    ;; the original before returning.
    (binding [*ns* *ns*
              *file* (str source-path)]
      (env/with-env (ana.jvm/global-env)
        (begin-file-debug *file* *ns* opt)
        (loop [forms []
               asts []]
          (let [form (tr/read reader nil eof)]
            (if (identical? form eof)
              {:forms forms, :asts asts, :exception nil}
              (let [cur-env (env/deref-env)
                    _ (pre-analyze-debug asts form cur-env *ns* opt)
                    [exc ast]
                    (try
                      (binding [ana.jvm/run-passes run-passes]
                        [nil (ana.jvm/analyze+eval form (ana.jvm/empty-env) {})])
                      (catch Exception e
                        [e nil]))]
                (if exc
                  {:forms (remaining-forms reader (conj forms form)),
                   :asts asts, :exception exc, :exception-phase :analyze+eval,
                   :exception-form form}
                  (if-let [e (wrapped-exception? (:result ast))]
                    {:forms (remaining-forms reader (conj forms form)),
                     :asts asts, :exception e, :exception-phase :eval,
                     :exception-form form}
                    (do
                      (post-analyze-debug asts form ast *ns* opt)
                      (recur (conj forms form)
                             (conj asts
                                   (add-partly-resolved-forms ast))))))))))))))


(defn analyze-ns
  "Takes an IndexingReader and a namespace symbol.
  Returns a map of results of analyzing the namespace.  The map
  contains these keys:

  :analyze-results - The value associated with this key is itself a
      map with the following keys:
      :namespace - The source-nsym argument to this fn
      :source - A string containing the source read in from the
          namespace's source file.
      :forms - See analyze-file docs for details
      :asts - See analyze-file
  :exception, :exception-phase, :exception-form - See analyze-file

  Options:
  - :reader  a pushback reader to use to read the namespace forms
  - :opt     a map of analyzer options
    - same as analyze-file.  See there.

  eg. (analyze-ns 'my-ns :opt {} :reader (pb-reader-for-ns 'my.ns))"
  [source-nsym & {:keys [reader opt] :or {reader (pb-reader-for-ns source-nsym)}}]
  (let [source-path (munge-ns source-nsym)
        {:keys [analyze-results] :as m}
        (analyze-file source-path :reader reader :opt opt)]
    (assoc (dissoc m :forms :asts)
      :analyze-results {:source (slurp (io/resource source-path))
                        :namespace source-nsym
                        :forms (:forms m)
                        :asts (:asts m)})))
