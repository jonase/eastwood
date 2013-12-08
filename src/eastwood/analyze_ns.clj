(ns eastwood.analyze-ns
  (:refer-clojure :exclude [macroexpand-1])
  (:import (clojure.lang LineNumberingPushbackReader))
  (:require [clojure.string :as string]
            [clojure.pprint :as pp]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [clojure.tools.reader :as tr]
            [clojure.tools.analyzer.jvm :as ana.jvm]
            [clojure.tools.analyzer.utils :refer [maybe-var]]
            [clojure.tools.analyzer :as ana :refer [analyze] :rename {analyze -analyze}]))

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

(defn pre-analyze-debug [out form *ns* opt]
  (let [print-normally? (or (contains? (:debug opt) :all)
                            (contains? (:debug opt) :forms))
        pprint? (or (contains? (:debug opt) :all)
                    (contains? (:debug opt) :forms-pprint))]
  (when (or print-normally? pprint?)
    (println (format "dbg pre-analyze #%d *ns*=%s"
                     (count out) (str *ns*)))
    (when pprint?
      (println "    form before macroexpand:")
      (pp/pprint form))
    (when print-normally?
      (println "    form before macroexpand, with metadata:")
      (binding [*print-meta* true] (pr form)))
    (println "\n    --------------------")
    (let [exp (macroexpand form)]
      (when pprint?
        (println "    form after macroexpand:")
        (pp/pprint exp))
      (when print-normally?
        (println "    form after macroexpand, with metadata:")
        (binding [*print-meta* true] (pr exp))))
    (println "\n    --------------------"))))


(defn post-analyze-debug [out _form _form-analysis *ns* opt]
  (when (or (contains? (:debug opt) :progress)
            (contains? (:debug opt) :all))
    (println (format "dbg anal'd %d *ns*=%s"
                     (count out)
                     (str *ns*)))))


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


;; TBD: Hack alert.  I am looking at the pre-macroexpanded form and
;; assuming if the first symbol is 'ns', then this is clojure.core/ns.
;; A more robust way from looking at post-analyzed result is tricker.
;; Maybe everything should just be eval'd all of them time, not only
;; ns statements?

(defn ns-form?
  "Keep this really simple-minded for now.  It will miss ns forms
  nested inside of other forms."
  [form _form-analysis]
  (and (list? form)
       (= 'ns (first form))))


(defn macroexpand-1
  [form env]
  (if (seq? form)
    (let [op (first form)]
      (if-not (ana.jvm/specials op)
        (let [v (maybe-var op env)
              local? (-> env :locals (get op))]
          (if (and (not local?) (:macro (meta v)))
           (apply v form (:locals env) (rest form))
           (ana.jvm/macroexpand-1 form env)))
        (ana.jvm/macroexpand-1 form env)))
    (ana.jvm/macroexpand-1 form env)))

(defn analyze
  [form env]
  (binding [ana/macroexpand-1 macroexpand-1
            ana/create-var    ana.jvm/create-var
            ana/parse         ana.jvm/parse]
    (ana.jvm/run-passes (-analyze form env))))

(defn analyze-form [form env]
  (try
    (let [form-analysis (analyze form env)]
      {:analyze-exception nil :analysis form-analysis})
    (catch Exception e
      {:analyze-exception e :analysis nil})))

;; analyze-file was copied from library jvm.tools.analyzer and then
;; modified

;; We need to eval forms for side effects, e.g. changing the
;; namespace, importing Java classes, etc.  If it sets the namespace,
;; though, should that effect the value of env, too?  Perhaps by
;; calling empty-env in the form reading loop below, we achieve that.

(defn analyze-file
  "Takes a file path and optionally a pushback reader.
  Returns a vector of maps with keys :form and :ast (representing the
  ASTs of the forms in the target file).

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
    - :eval If :all (the default), call eval on all forms read before
            reading the next form.  If :ns-only, only eval forms that
            appear to be (ns ...) forms.  If :none, do not eval any
            forms (not expected to be useful).

  eg. (analyze-file \"my/ns.clj\" :opt {:debug-all true})"
  [source-path & {:keys [reader opt] 
                  :or {reader (LineNumberingPushbackReader.
                               (io/reader (io/resource source-path)))}}]
  (let [debug-ns (or (contains? (:debug opt) :ns)
                     (contains? (:debug opt) :all))
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
        (binding [*file* (str source-path)]
          (loop [nss (if debug-ns (all-ns-names-set))
                 form (tr/read pushback-reader nil eof)
                 out []]
            (if (identical? form eof)
              {:analyze-exception nil :analyze-results out}
              (let [_ (pre-analyze-debug out form *ns* opt)
                    ;; TBD: ana.jvm/empty-env uses *ns*.  Is that
                    ;; what is needed here?  Is there some way to call
                    ;; empty-env once and then update it as needed as
                    ;; forms are analyzed?
                    env (ana.jvm/empty-env)
                    form-analysis (analyze-form form env)]
                (post-analyze-debug out form form-analysis *ns* opt)
                (when (or (= :all eval-opt)
                          (and (= :ns-only eval-opt)
                               (ns-form? form form-analysis)))
                  (.set clojure.lang.Compiler/LOADER (clojure.lang.RT/makeClassLoader))
                  (eval form))
                (let [new-nss (if debug-ns (namespace-changes-debug nss opt))]
                  (if-let [e (:analyze-exception form-analysis)]
                    {:analyze-exception e :analyze-results out}
                    (recur new-nss (tr/read pushback-reader nil eof)
                           (conj out {:form form
                                      :ast (:analysis form-analysis)}))))))))))))


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
  (let [source-path (munge-ns source-nsym)
        {:keys [analyze-results analyze-exception]} 
        (analyze-file source-path :reader reader :opt opt)]
    {:analyze-results {:source (slurp (io/resource source-path))
                       :namespace source-nsym
                       :forms (mapv :form analyze-results)
                       :asts (mapv :ast analyze-results)}
     :analyze-exception analyze-exception}))
