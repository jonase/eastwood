(ns eastwood.analyze-ns
  (:import (clojure.lang LineNumberingPushbackReader))
  (:require [clojure.string :as string]
            [clojure.pprint :as pp]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [clojure.tools.analyzer.jvm :as analyze-jvm]
            [eastwood.jvm :as janal]))


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


;; analyzer-bindings was copied from library jvm.tools.analyzer and
;; then modified

(defmacro ^:private analyzer-bindings [source-path pushback-reader]
  `{#'*file* (str ~source-path)}
  ;; TBD: Does tools.analyzer.jvm/analyze need any of this?  If so,
  ;; does it belong in its env argument, or in dynamic bindings set up
  ;; before calling it?
;;  `(merge
;;     {Compiler/LOADER (RT/makeClassLoader)
;;      Compiler/SOURCE_PATH (str ~source-path)
;;      Compiler/SOURCE (str ~source-path)
;;      Compiler/METHOD nil
;;      Compiler/LOCAL_ENV nil
;;      Compiler/LOOP_LOCALS nil
;;      Compiler/NEXT_LOCAL_NUM 0
;;      RT/CURRENT_NS @RT/CURRENT_NS
;;      Compiler/LINE_BEFORE (.getLineNumber ~pushback-reader)
;;      Compiler/LINE_AFTER (.getLineNumber ~pushback-reader)
;;      RT/UNCHECKED_MATH @RT/UNCHECKED_MATH}
;;     ~(when (RT-members 'WARN_ON_REFLECTION)
;;        `{(field RT ~'WARN_ON_REFLECTION) @(field RT ~'WARN_ON_REFLECTION)})
;;     ~(when (Compiler-members 'COLUMN_BEFORE)
;;        `{Compiler/COLUMN_BEFORE (.getColumnNumber ~pushback-reader)})
;;     ~(when (Compiler-members 'COLUMN_AFTER)
;;        `{Compiler/COLUMN_AFTER (.getColumnNumber ~pushback-reader)})
;;     ~(when (RT-members 'DATA_READERS)
;;        `{RT/DATA_READERS @RT/DATA_READERS}))
  )


(defn all-ns-names-set []
  (set (map str (all-ns))))


(defn pre-analyze-debug [out form *ns* opt]
  (when (or (:debug-forms opt) (:debug-all opt))
    (println (format "dbg pre-analyze #%d *ns*=%s"
                     (count out) (str *ns*)))
    (println "    form before macroexpand:")
    (pp/pprint form)
    (println "    form before macroexpand, with metadata:")
    (binding [*print-meta* true] (pr form))
    (println "\n    --------------------")
    (let [exp (macroexpand form)]
      (println "    form after macroexpand:")
      (pp/pprint exp)
      (println "    form after macroexpand, with metadata:")
      (binding [*print-meta* true] (pr exp)))
    (println "\n    --------------------")))


(defn post-analyze-debug [out form expr-analysis *ns* opt]
  (when (or (:debug-progress opt) (:debug-all opt))
    (println (format "dbg anal'd %d *ns*=%s"  ; aliases=%s"
                     (count out)
                     (str *ns*)
                     ;;(ns-aliases *ns*)
                     ))))


(defn namespace-changes-debug [old-nss opt]
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


;; TBD: Hack alert.  I am looking at the pre-macroexpanded form and
;; assuming if the first symbol is 'ns', then this is clojure.core/ns.
;; A more robust way from looking at post-analyzed result is tricker.
;; Maybe everything should just be eval'd all of them time, not only
;; ns statements?

(defn ns-form?
  "Keep this really simple-minded for now.  It will miss ns forms
  nested inside of other forms."
  [form expr-analysis]
  (and (list? form)
       (= 'ns (first form))))


;; analyze-file was copied from library jvm.tools.analyzer and then
;; modified

;; TBD: I think we need to to eval for side effects, e.g. changing the
;; namespace, importing Java classes, etc.  If it sets the namespace,
;; though, should that effect the value of env, too?

(defn analyze-file
  "Takes a file path and optionally a pushback reader.
  Returns a vector of maps representing the ASTs of the forms in the
  target file.

  Options:
  - :reader  a pushback reader to use to read the namespace forms
  - :opt     a map of analyzer options

    - :debug-progress If true, print simple progress messages as
                      analysis proceeds.
    - :debug-ns If true, print all namespaces that exist according
                to (all-ns) before analysis begins, and then only when
                that set of namespaces changes after each form is
                analyzed.
    - :debug-forms If true, print forms just before analysis, both
                   before and after macroexpanding them.
    - :debug-all If true, enable all of the above :debug-* options.
    - :eval If :all (the default), call eval on all forms read before
            reading the next form.  If :ns-only, only eval forms that
            appear to be (ns ...) forms.  If :none, do not eval any
            forms (not expected to be useful).

  eg. (analyze-file \"my/ns.clj\" :opt {:debug-all true})"
  [source-path & {:keys [reader opt] 
                  :or {reader (LineNumberingPushbackReader.
                               (io/reader (io/resource source-path)))}}]
  (let [debug-ns (or (:debug-ns opt) (:debug-all opt))
        eval-opt (or (:eval opt) :all)]
    (when debug-ns
      (println (format "all-ns before (analyze-file \"%s\") begins:"
                       source-path))
      (pp/pprint (sort (all-ns-names-set))))
    ;; HACK ALERT: Make a new binding of the current *ns* before doing
    ;; the below.  If we eval a form that changes the namespace, I
    ;; want it to go back to the original before returning.
    (binding [*ns* *ns*]
      (let [eof (reify)
            ^LineNumberingPushbackReader 
            pushback-reader (if (instance? LineNumberingPushbackReader reader)
                              reader
                              (LineNumberingPushbackReader. reader))]
        (with-bindings (analyzer-bindings source-path pushback-reader)
          (loop [nss (if debug-ns (all-ns-names-set))
                 form (read pushback-reader nil eof)
                 out []]
            (if (identical? form eof)
              out
              (let [_ (pre-analyze-debug out form *ns* opt)
                    ;; TBD: analyze-jvm/empty-env uses *ns*.  Is that
                    ;; what is needed here?  Is there some way to call
                    ;; empty-env once and then update it as needed as
                    ;; forms are analyzed?
                    env (analyze-jvm/empty-env)
                    expr-analysis (janal/analyze form env)]
                (post-analyze-debug out form expr-analysis *ns* opt)
                (when (or (= :all eval-opt)
                          (and (= :ns-only eval-opt)
                               (ns-form? form expr-analysis)))
                  (eval form))
                (let [new-nss (if debug-ns (namespace-changes-debug nss opt))]
                  (recur new-nss (read pushback-reader nil eof)
                         (conj out expr-analysis)))))))))))


;; analyze-ns was copied from library jvm.tools.analyzer and then
;; modified (TBD: maybe doesn't need any modification)

(defn analyze-ns
  "Takes a LineNumberingPushbackReader and a namespace symbol.
  Returns a vector of maps, with keys :op, :env. If expressions
  have children, will have :children entry.

  Options:
  - :reader  a pushback reader to use to read the namespace forms
  - :opt     a map of analyzer options
    - same as analyze-file.  See there.

  eg. (analyze-ns 'my-ns :opt {} :reader (pb-reader-for-ns 'my.ns))"
  [source-nsym & {:keys [reader opt] :or {reader (pb-reader-for-ns source-nsym)}}]
  (let [source-path (munge-ns source-nsym)]
    (analyze-file source-path :reader reader :opt opt)))
