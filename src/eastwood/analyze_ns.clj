(ns eastwood.analyze-ns
  (:refer-clojure :exclude [macroexpand-1])
  (:import (java.net URL)
           (java.io File))
  (:require [clojure.string :as string]
            [clojure.pprint :as pp]
            [eastwood.util :as util]
            [eastwood.passes :as pass]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [eastwood.copieddeps.dep10.clojure.tools.reader :as tr]
            [eastwood.copieddeps.dep10.clojure.tools.reader.reader-types :as rts]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.move :as move]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer
             [ast :as ast]
             [env :as env]
             [passes :refer [schedule]]]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.passes.trim :refer [trim]]
            [eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm :as ana.jvm]
            [eastwood.copieddeps.dep2.clojure.tools.analyzer.passes.jvm
             [emit-form :refer [emit-form]]
             [warn-on-reflection :refer [warn-on-reflection]]]
            [eastwood.reporting-callbacks :as reporting]))

;; uri-for-ns, pb-reader-for-ns were copied from library
;; jvm.tools.analyzer, then later probably diverged from each other.

(defn ^:private ns-resource-name
  "clojure.java.io/resource and Java in general expects components of
a resource path name to be separated by '/' characters, regardless of
the value of File/separator for the platform."
  [ns-sym extension]
  (-> (name ns-sym)
      (string/replace "." "/")
      (string/replace "-" "_")
      (str extension)))

(defn ^java.net.URL uri-for-ns
  "Returns a URI representing the namespace. Throws an
  exception if URI not found."
  [ns-sym]
  (let [rsrc-path-clj (ns-resource-name ns-sym ".clj")
        rsrc-path-cljc (ns-resource-name ns-sym ".cljc")
        uri-clj (io/resource rsrc-path-clj)
        uri-cljc (io/resource rsrc-path-cljc)
        uri (or uri-clj uri-cljc)]
    (when-not uri
      (throw (Exception. (str "No file found for namespace " ns-sym))))
    uri))

(defn pb-reader-for-ns
  "Returns an IndexingReader for namespace ns-sym"
  [ns-sym]
  (let [uri (uri-for-ns ns-sym)]
    (rts/indexing-push-back-reader (java.io.PushbackReader. (io/reader uri))
                                   1 (#'move/ns-file-name ns-sym))))

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

(defn pre-analyze-debug [asts form _env ns opt]
  (when (util/debug? :ns opt)
    (let [print-normally? (util/debug? :forms opt)
          pprint? (util/debug? :forms-pprint opt)
          debug-cb (util/make-msg-cb :debug opt)]
      (when (or print-normally? pprint?)
        (debug-cb (format "dbg pre-analyze #%d ns=%s (meta ns)=%s"
                          (count asts) (str ns) (meta ns)))
        (when pprint?
          (debug-cb "    form before macroexpand:")
          (debug-cb (with-out-str (pp/pprint form))))
        (when print-normally?
          (debug-cb "    form before macroexpand, with metadata (some elided for brevity):")
          (debug-cb (with-out-str (util/pprint-meta-elided form))))
        (debug-cb "\n    --------------------")
        (if (dont-expand-twice? form)
          (when print-normally?
            (debug-cb "    form is gen-interface, so avoiding macroexpand on it"))
          (let [exp (macroexpand form)]
            (when pprint?
              (debug-cb "    form after macroexpand:")
              (debug-cb (with-out-str (pp/pprint exp))))
            (when print-normally?
              (debug-cb "    form after macroexpand, with metadata (some elided for brevity):")
              (debug-cb (with-out-str (util/pprint-meta-elided exp))))))
        (debug-cb "\n    --------------------")))))

(def ^:dynamic *forms-read-writer* (io/writer "forms-read.txt"))
(def ^:dynamic *forms-analyzed-writer* (io/writer "forms-analyzed.txt"))
(def ^:dynamic *forms-emitted-writer* (io/writer "forms-emitted.txt"))

(defn- debug-forms [form ast]
  (binding [*out* *forms-read-writer*]
    (util/pprint-form form))
  (binding [*out* *forms-analyzed-writer*]
    (util/pprint-form (:form ast)))
  (binding [*out* *forms-emitted-writer*]
    (util/pprint-form (emit-form ast))))

(defn- debug-forms-new-file [filename]
  (let [s (format "\n\n== Analyzing file '%s'\n" filename)]
    (binding [*out* *forms-read-writer*]
      (println s))
    (binding [*out* *forms-analyzed-writer*]
      (println s))
    (binding [*out* *forms-emitted-writer*]
      (println s))))

(defn post-analyze-debug [filename asts form ast ns opt]
  (when (util/debug? :ns opt)
    (let [show-ast? (util/debug? :ast opt)
          cb (:callback opt)
          debug-cb (util/make-msg-cb :debug opt)]
      (when (or show-ast? (util/debug? :progress opt))
        (debug-cb (format "dbg anal'd %d ns=%s%s"
                          (count asts) (str ns)
                          (if show-ast? " ast=" ""))))
      (when show-ast?
         (util/pprint-ast-node ast))
      ;; TBD: Change this to macroexpand form, at least if
      ;; dont-expand-twice? returns false.
      (when (util/debug? :compare-forms opt)
        (debug-forms form ast)))))

(defn begin-file-debug [filename ns opt]
  (when (and (util/debug? :ns opt)
             (util/debug? :compare-forms opt))
    (debug-forms-new-file filename)))

(defn before-analyze-file-debug [source-path opt]
  (when (util/debug? :ns opt)
    (let [debug-cb (util/make-msg-cb :debug opt)]
      (debug-cb (format "all-ns before (analyze-file \"%s\") begins:"
                        source-path))
      (debug-cb (with-out-str (pp/pprint (sort (all-ns-names-set))))))))

(defn eastwood-wrong-tag-handler [t ast]
  (let [tag (if (= t :name/tag)
              (-> ast :name meta :tag)
            (get ast t))]
;;    (println (format "\nWrong tag: t=%s %s (%s) in %s"
;;                     t tag (class tag)
;;                     (:name ast)))
;;    (println (format "  op=%s form=%s" (:op ast) (:form ast)))
;;    (println (meta (:form ast)))
;;    (pp/pprint (:form ast))
;;    (util/pprint-ast-node ast)
;;    (flush)

    ;; Key/value pairs to be merged into ast for later code to find
    ;; and issue warnings.  We use a different map key for each
    ;; different value of t, because if we used the same map key, only
    ;; the last one merged in would remain, the way tools.analyzer.jvm
    ;; uses this return value.

    ;; Remember the value of 'tag' calculated here in the map, since
    ;; it seems that some later tools.analyzer(.jvm) pass may be
    ;; changing the values of the keys :tag :o-tag etc. that existed
    ;; when this function was called.
    (case t
      :name/tag {:eastwood/name-tag tag}
      :tag {:eastwood/tag tag}
      :o-tag {:eastwood/o-tag tag}
      :return-tag {:eastwood/return-tag tag})))

;; eastwood-passes is a cut-down version of run-passes in
;; tools.analyzer.jvm.  It eliminates phases that are not needed for
;; linting, and which can cause analysis to throw exceptions, where in
;; Eastwood we would prefer to give linter warnings.

(def eastwood-passes
  "Set of passes that will be run by default on the AST by #'run-passes"
  ;; Doing clojure.core/eval in analyze+eval already generates
  ;; reflection warnings from Clojure.  Doing it in tools.analyzer
  ;; also leads to duplicate warnings.
  (disj ana.jvm/default-passes #'warn-on-reflection #'trim))

(def scheduled-eastwood-passes
  (schedule eastwood-passes))

(defn ^:dynamic run-passes
  "Function that will be invoked on the AST tree immediately after it has been constructed,
   by default set-ups and runs the default passes declared in #'default-passes"
  [ast]
  (scheduled-eastwood-passes ast))

(def eastwood-passes-opts
  (merge ana.jvm/default-passes-opts
         {:validate/wrong-tag-handler eastwood-wrong-tag-handler}))

;; TBD: Consider changing how the functions called within
;; eastwood-ast-additions are defined so that they can be added to
;; eastwood-passes above instead.

(defn eastwood-ast-additions [ast ast-idx]
  (-> ast
      (pass/add-path [ast-idx])
      pass/add-ancestors))

(defn wrapped-exception? [result]
  (if (instance? eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm.ExceptionThrown result)
    (.e ^eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm.ExceptionThrown result)))


(defn asts-with-eval-exception
  "tools.analyzer.jvm/analyze+eval returns an AST with a :result key
being a specific class of Exception object, if an exception occurred
while eval'ing the corresponding expression.  This is easy to check at
the top level of an AST, but since analyze+eval recurses into
top-level do forms, analyzing and eval'ing each independently,
checking whether any of them threw an exception during eval requires
recursing into ASTs with :op equal to :do"
  [ast]
  (filter #(wrapped-exception? (:result %))
          (ast/nodes ast)))


(defn remaining-forms [pushback-reader forms]
  (let [eof (reify)
        reader-opts {:read-cond :allow :features #{:clj} :eof eof}]
    (loop [forms forms]
      (let [form (tr/read reader-opts pushback-reader)]
        (if (identical? form eof)
          forms
          (recur (conj forms form)))))))

(defn- replace-path-in-compiler-error
  [msg cwd]
  (let [[match pre _ path
         line-col post] (re-matches #"((Reflection|Boxed math) warning), (.*?)(:\d+:\d+)(.*)"
                                    msg)
        url (and match (io/resource path))
        inf (and url (util/file-warn-info url cwd))]
    (if inf
      ;; The filename:line:col should be first in the output
      (str (:uri-or-file-name inf) line-col ": " pre post)
      msg)))

(defn- do-eval-output-callbacks [out-msgs-str err-msgs-str cwd]
  (when (not= out-msgs-str "")
    (doseq [s (string/split-lines out-msgs-str)]
      (println s)))
  (when (not= err-msgs-str "")
    (doseq [s (string/split-lines err-msgs-str)]
      (println (replace-path-in-compiler-error s cwd)))))


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
  (let [eof (reify)
        reader-opts {:read-cond :allow :features #{:clj} :eof eof}]
    (before-analyze-file-debug source-path opt)

    ;; If we eval a form that changes *ns*, I want it to go back to
    ;; the original before returning.
    (binding [*ns* *ns*
              *file* (str source-path)]
      (env/with-env (ana.jvm/global-env)
        (begin-file-debug *file* *ns* opt)
        (loop [forms []
               asts []]
          (let [form (tr/read reader-opts reader)]
            (if (identical? form eof)
              {:forms forms, :asts asts, :exception nil}
              (let [cur-env (env/deref-env)
                    _ (pre-analyze-debug asts form cur-env *ns* opt)
                    [exc ast]
                    (try
                      (let [{:keys [val out err]}
                            (util/with-out-str2
                              (binding [ana.jvm/run-passes run-passes]
                                (ana.jvm/analyze+eval
                                 form (ana.jvm/empty-env)
                                 {:passes-opts eastwood-passes-opts})))]
                        (do-eval-output-callbacks out err (:cwd opt))
                        [nil val])
                      (catch Exception e
                        [e nil]))]
                (if exc
                  {:forms nil
                   :asts asts, :exception exc, :exception-phase :analyze+eval,
                   :exception-form form}
                  (if-let [first-exc-ast (first (asts-with-eval-exception ast))]
                    {:forms (remaining-forms reader (conj forms form)),
                     :asts asts,
                     :exception (wrapped-exception? (:result first-exc-ast)),
                     :exception-phase :eval,
                     :exception-form (if-let [f (first (:raw-forms first-exc-ast))]
                                       f
                                       (:form first-exc-ast))}
                    (do
                      (post-analyze-debug source-path asts form ast *ns* opt)
                      (recur (conj forms form)
                             (conj asts
                                   (eastwood-ast-additions
                                    ast (count asts)))))))))))))))


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
  (let [source-path (#'move/ns-file-name source-nsym)
        m (analyze-file source-path :reader reader :opt opt)]
    (assoc (dissoc m :forms :asts)
           :analyze-results {:source (slurp (uri-for-ns source-nsym))
                             :namespace source-nsym
                             :exeption-form (:exception-form m)
                             :forms (:forms m)
                             :asts (:asts m)})))
