(ns eastwood.analyze-ns
  (:refer-clojure :exclude [macroexpand-1])
  (:import (clojure.lang LineNumberingPushbackReader))
  (:require [clojure.string :as string]
            [clojure.pprint :as pp]
            [eastwood.util :as util]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [clojure.tools.reader :as tr]
            [clojure.tools.analyzer.jvm :as ana.jvm]
            [clojure.tools.analyzer.passes.jvm.emit-form :refer [emit-form]]
            [clojure.tools.analyzer.utils :refer [resolve-var]]
            [clojure.tools.analyzer.ast :refer [postwalk prewalk cycling]]
            [clojure.tools.analyzer :as ana :refer [analyze] :rename {analyze -analyze}]
            [clojure.tools.analyzer.passes.source-info :refer [source-info]]
            [clojure.tools.analyzer.passes.cleanup :refer [cleanup]]
            [clojure.tools.analyzer.passes.elide-meta :refer [elide-meta]]
            [clojure.tools.analyzer.passes.constant-lifter :refer [constant-lift]]
            [clojure.tools.analyzer.passes.warn-earmuff :refer [warn-earmuff]]
            [clojure.tools.analyzer.passes.add-binding-atom :refer [add-binding-atom]]
            [clojure.tools.analyzer.passes.uniquify :refer [uniquify-locals]]
            [clojure.tools.analyzer.passes.jvm.box :refer [box]]
            [clojure.tools.analyzer.passes.jvm.annotate-branch :refer [annotate-branch]]
            [clojure.tools.analyzer.passes.jvm.annotate-methods :refer [annotate-methods]]
            [clojure.tools.analyzer.passes.jvm.fix-case-test :refer [fix-case-test]]
            [clojure.tools.analyzer.passes.jvm.classify-invoke :refer [classify-invoke]]
            [clojure.tools.analyzer.passes.jvm.validate :refer [validate]]
            [clojure.tools.analyzer.passes.jvm.infer-tag :refer [infer-tag]]
            [clojure.tools.analyzer.passes.jvm.annotate-tag :refer [annotate-tag]]
            [clojure.tools.analyzer.passes.jvm.validate-loop-locals :refer [validate-loop-locals]]
            [clojure.tools.analyzer.passes.jvm.analyze-host-expr :refer [analyze-host-expr]]))

;; munge-ns, uri-for-ns, pb-reader-for-ns were copied from library
;; jvm.tools.analyzer verbatim

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

(defn ^LineNumberingPushbackReader
  pb-reader-for-ns
  "Returns a LineNumberingPushbackReader for namespace ns-sym"
  [ns-sym]
  (let [uri (uri-for-ns ns-sym)]
    (LineNumberingPushbackReader. (io/reader uri))))

(defn all-ns-names-set []
  (set (map str (all-ns))))

;; Hack alert.  I am looking at the pre-macroexpanded form and
;; assuming if the first symbol is 'ns', then this is clojure.core/ns.
;; A more robust way from looking at post-analyzed result is tricker.

(defn ns-form?
  "Keep this really simple-minded for now.  It will miss ns forms
  nested inside of other forms."
  [form]
  (and (sequential? form)
       (= 'ns (first form))))

(defn do-form? [form]
  (and (sequential? form)
       (= 'do (first form))))

(defn gen-interface-form? [form]
  (and (sequential? form)
       (contains? #{'gen-interface 'clojure.core/gen-interface}
                  (first form))))

;; Avoid macroexpand'ing a gen-interface form more than once, since it
;; causes an exception to be thrown.
(defn dont-expand-twice? [form]
  (gen-interface-form? form))

(defn pre-analyze-debug [at-top-level? asts form env ns opt]
  (let [print-normally? (or (contains? (:debug opt) :all)
                            (contains? (:debug opt) :forms))
        pprint? (or (contains? (:debug opt) :all)
                    (contains? (:debug opt) :forms-pprint))]
  (when (or print-normally? pprint?)
    (println (format "dbg pre-analyze #%d at-top-level?=%s ns=%s"
                     (count asts) at-top-level? (str ns)))
    (when pprint?
      (println "    form before macroexpand:")
      (pp/pprint form))
    (when print-normally?
      (println "    form before macroexpand, with metadata:")
      (binding [*print-meta* true] (pr form)))
    (println "\n    --------------------")
    (if (dont-expand-twice? form)
      (when print-normally?
        (println "    form is gen-interface, so avoiding macroexpand on it"))
      (let [exp (macroexpand form)]
        (when pprint?
          (println "    form after macroexpand:")
          (pp/pprint exp))
        (when print-normally?
          (println "    form after macroexpand, with metadata:")
          (binding [*print-meta* true] (pr exp)))))
    (println "\n    --------------------"))))

(defn post-analyze-debug [at-top-level? asts _form _analysis _exception ns opt]
  (when (or (contains? (:debug opt) :progress)
            (contains? (:debug opt) :all))
    (println (format "dbg anal'd %d at-top-level?=%s ns=%s"
                     (count asts) at-top-level? (str ns)))))

(defn pre-eval-debug [at-top-level? asts form ns opt desc]
  (when (or (contains? (:debug opt) :all)
            (contains? (:debug opt) :eval))
    (println (format "Form about to be eval'd with %d ast's *warn-on-reflection*=%s ns=%s (%s):"
                     (count asts) *warn-on-reflection* ns desc))
    (util/pprint-ast-node form)))

(defn namespace-changes-debug [old-nss _opt]
  (let [new-nss (all-ns-names-set)
        added (set/difference new-nss old-nss)
        removed (set/difference old-nss new-nss)]
    (when (not= 0 (count added))
      (println (format "New namespaces added recently: %s"
                       (seq added))))
    (when (not= 0 (count removed))
      (println (format "Namespaces removed recently: %s"
                       (seq removed))))
    new-nss))

(defn macroexpand-1
  [form env]
  (if (seq? form)
    (let [op (first form)]
      (if-not (ana.jvm/specials op)
        (let [v (resolve-var op env)
              local? (-> env :locals (get op))]
          (if (and (not local?) (:macro (meta v)))
           (apply v form (:locals env) (rest form))
           (ana.jvm/macroexpand-1 form env)))
        (ana.jvm/macroexpand-1 form env)))
    (ana.jvm/macroexpand-1 form env)))

(defn create-var
  [sym {:keys [ns]}]
  (if-let [v (find-var (symbol (str ns) (name sym)))]
    (doto v
      (reset-meta! (if (bound? v)
                     (merge (meta sym) (meta v))
                     (meta sym))))
    (intern ns sym)))

;; run-passes is a cut-down version of run-passes in
;; tools.analyzer.jvm.  It eliminates phases that are not needed for
;; linting, and which can cause analysis to fail for code that we
;; would prefer to give linter warnings for, rather than throw an
;; exception.

(defn run-passes
  [ast]
  (-> ast

    uniquify-locals
    add-binding-atom

    (prewalk (fn [ast]
               (-> ast
                 warn-earmuff
                 source-info
                 elide-meta
                 annotate-methods
                 fix-case-test)))

    ((fn analyze [ast]
       (-> ast
         (postwalk
          (fn [ast]
            (-> ast
              annotate-tag
              analyze-host-expr
              infer-tag
              validate
              classify-invoke)))
         (prewalk
          (comp box
             (validate-loop-locals analyze))))))

    (prewalk cleanup)))

(defn analyze
  [form env]
  (binding [ana/macroexpand-1 macroexpand-1
            ana/create-var    create-var
            ana/parse         ana.jvm/parse
            ana/var?          var?]
    (run-passes (-analyze form env))))

(defn analyze-form [form env]
  (try
    (let [form-analysis (analyze form env)]
      {:exception nil :analysis form-analysis})
    (catch Exception e
      {:exception e :analysis nil})))

(defn deftype-classnames [analysis]
  (let [a (atom #{})]
    (postwalk analysis
              (fn [{:keys [op class-name]}]
                (when (= :deftype op)
                  (swap! a conj class-name))))
    @a))

(defn remaining-forms [pushback-reader forms]
  (let [eof (reify)]
    (loop [forms forms]
      (let [form (tr/read pushback-reader nil eof)]
        (if (identical? form eof)
          forms
          (recur (conj forms form)))))))

;; analyze-file was copied from library jvm.tools.analyzer and has
;; been modified heavily since then.

;; We need to eval forms for side effects, e.g. changing the
;; namespace, importing Java classes, etc.  If it sets the namespace,
;; though, should that effect the value of env, too?  Perhaps by
;; calling empty-env in the form reading loop below, we achieve that.

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
      exception occurred.  One of:
      :analyze - during analysis of the form, i.e. the call to
          analyze-form
      :emit-form - after form analysis completed and an AST was
          constructed, but during conversion of that AST back into a
          form, i.e. the call to emit-form
      :eval-form - after form analysis and conversion back into a
          form, but during evaluation of that form, i.e. the call to
          eval
      :eval-ns - during eval of a top level 'ns' form.  For such
          forms, neither analysis nor conversion back into a form are
          performed.  The form originally read in is evaluated.

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
    - :eval If true (the default), call eval on all forms read before
            reading the next form.

  eg. (analyze-file \"my/ns.clj\" :opt {:debug-all true})"
  [source-path & {:keys [reader opt]
                  :or {reader (LineNumberingPushbackReader.
                               (io/reader (io/resource source-path)))}}]
  (let [debug-ns (or (contains? (:debug opt) :ns)
                     (contains? (:debug opt) :all))
        nss (if debug-ns (atom (all-ns-names-set)))
        eval? (get opt :eval true)
        eof (reify)
        ^LineNumberingPushbackReader
        pbrdr (if (instance? LineNumberingPushbackReader reader)
                reader
                (LineNumberingPushbackReader. reader))
        loaded-namespaces (atom (loaded-libs))]
    (when debug-ns
      (println (format "all-ns before (analyze-file \"%s\") begins:"
                       source-path))
      (pp/pprint (sort (all-ns-names-set))))
    ;; HACK ALERT: Make a new binding of the current *ns* before doing
    ;; the below.  If we eval a form that changes the namespace, I
    ;; want it to go back to the original before returning.
    (binding [*ns* *ns*
              *file* (str source-path)]
      (loop [forms []
             asts []
             unanalyzed-forms []]
        (let [at-top-level? (empty? unanalyzed-forms)
              [form unanalyzed-forms] (if at-top-level?
                                        [(tr/read pbrdr nil eof) []]
                                        [(first unanalyzed-forms)
                                         (rest unanalyzed-forms)])
              done? (and at-top-level?
                         (identical? form eof))
              top-level-ns-form? (and (not done?) at-top-level? (ns-form? form))]
          (if done?
            {:forms forms, :asts asts, :exception nil}
            (if-let [eval-ns-exc
                     (when (and eval? top-level-ns-form?)
                       (try
                         (.set clojure.lang.Compiler/LOADER (clojure.lang.RT/makeClassLoader))
                         (pre-eval-debug at-top-level? asts form *ns* opt "top level ns")
                         (eval form)
                         (swap! loaded-namespaces into (disj (loaded-libs) (ns-name *ns*)))
                         nil  ; return no exception
                         (catch Exception e
                           e)))]
              {:forms (remaining-forms
                       pbrdr (into forms (concat [form] unanalyzed-forms))),
               :asts asts, :exception eval-ns-exc,
               :exception-phase :eval-ns, :exception-form form}
              (let [env (ana.jvm/empty-env)
                    expanded (if (or top-level-ns-form?
                                     (dont-expand-twice? form))
                               form
                               (macroexpand-1 form env))]
                (if (and (not top-level-ns-form?) (do-form? expanded))
                  (recur forms asts (concat (rest expanded) unanalyzed-forms))
                  (let [_ (pre-analyze-debug at-top-level? asts form env *ns* opt)
                        {:keys [analysis exception]} (analyze-form form env)]
                    (post-analyze-debug at-top-level? asts form analysis exception
                                        *ns* opt)
                    (if exception
                      {:forms (remaining-forms
                               pbrdr
                               (into forms (concat [form] unanalyzed-forms))),
                       :asts asts, :exception exception,
                       :exception-phase :analyze, :exception-form form}
                      (if-let [[exc-phase exc]
                               (when (and eval? (not top-level-ns-form?))
                                 (when (seq (deftype-classnames analysis))
                                   (.set clojure.lang.Compiler/LOADER (clojure.lang.RT/makeClassLoader)))
                                 (when (not (@loaded-namespaces (ns-name *ns*)))
                                   (try
                                     (let [f (emit-form analysis)]
                                       (try
                                         (pre-eval-debug at-top-level? asts f *ns* opt "not top level ns")
                                         (eval f)
                                         nil   ; no exception
                                         (catch Exception e
                                           [:eval-form e])))
                                     (catch Exception e
                                       [:emit-form e]))))]
                        {:forms (remaining-forms
                                 pbrdr
                                 (into forms (concat [form] unanalyzed-forms))),
                         :asts asts, :exception exc,
                         :exception-phase exc-phase, :exception-form form}
                        (do
                          (when debug-ns
                            (reset! nss (namespace-changes-debug @nss opt)))
                          (recur (conj forms form)
                                 (conj asts analysis)
                                 unanalyzed-forms))))))))))))))


;; analyze-ns was copied from library jvm.tools.analyzer and then
;; modified

(defn analyze-ns
  "Takes a LineNumberingPushbackReader and a namespace symbol.
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
