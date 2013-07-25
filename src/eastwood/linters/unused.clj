(ns eastwood.linters.unused
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.reader.edn :as edn]
            [eastwood.util :as util]))


;; Unused private vars
(defn- private-defs [exprs]
  (->> (mapcat util/ast-nodes exprs)
       (filter #(and (= :def (:op %))
                     (-> % :var meta :private)
                     (-> % :var meta :macro not))) ;; skip private macros
       (map :var)))

(defn- var-freq [exprs]
  (->> (mapcat util/ast-nodes exprs)
       (filter #(contains? #{:var :the-var} (:op %)))
       (map :var)
       frequencies))

(defn unused-private-vars [{:keys [asts]}]
  (let [pdefs (private-defs asts)
        vfreq (var-freq asts)]
    (for [pvar pdefs
          :when (nil? (vfreq pvar))]
      {:linter :unused-private-vars
       :msg (format "Private var %s is never used" pvar)
       :line (-> pvar :env :line)})))



;; Unused fn args

(defn- ignore-arg?
  "Return logical true for args that should never be warned about as
unused, such as &form and &env that are 'hidden' args of macros.

By convention, _ is never reported as unused, and neither is any arg
with a name that begins with _.  This gives eastwood users a way to
selectively disable such warnings if they wish."
  [arg]
  (or (contains? #{'&env '&form} arg)
      (.startsWith (name arg) "_")))

(defn- params [fn-method]
  (let [params (:params fn-method)]
    (set (map #(select-keys % [:form :name]) params))))

(defn- used-locals [exprs]
  (set
   (->> exprs
        (filter (util/op= :local))
        (map #(select-keys % [:form :name])))))

(defn- unused-fn-args* [fn-expr]
  (reduce set/union
          (for [method (:methods fn-expr)]
            (let [args (params method)]
              (set/difference args (used-locals (util/ast-nodes (:body method))))))))

(defn unused-fn-args [{:keys [asts]}]
  (let [fn-exprs (->> asts
                      (map util/enhance-extend-invocations)
                      (mapcat util/ast-nodes)
                      (filter (util/op= :fn)))]
    (for [expr fn-exprs
          :let [unused (->> (unused-fn-args* expr)
                            (map :form)
                            (remove ignore-arg?)
                            set)]
          :when (not-empty unused)]
      {:linter :unused-fn-args
       :msg (format "Function args [%s] of (or within) %s are never used"
                    (str/join " " (map (fn [sym]
                                         (if-let [l (-> sym meta :line)]
                                           (format "%s (line %s)" sym l)
                                           sym))
                                       unused))
                    (-> expr :env :name))
       :line (-> expr :env :name meta :line)})))


;; Unused namespaces

;; If require is called outside of an ns form, on an argument that is
;; not a constant, the #(-> % :expr :form) step below can introduce
;; nil values into the sequence.  For now, just filter those out to
;; avoid warnings about namespace 'null' not being used.  Think about
;; if there is a better way to handle such expressions.  Examples are
;; in core namespace clojure.main, and contrib library namespaces
;; clojure.tools.namespace.reload and clojure.test.generative.runner.
(defn required-namespaces [exprs]
  (->> (mapcat util/ast-nodes exprs)
       (filter #(and (= (:op %) :invoke)
                     (let [v (-> % :fn :var)]
                       (or (= v #'clojure.core/require)
                           (= v #'clojure.core/use)))))
       (mapcat :args)
       (map #(-> % :expr :form))
       (remove nil?)
       (map #(if (coll? %) (first %) %))
       (remove keyword?)
       (into #{})))

(defn unused-namespaces [{:keys [asts]}]
  (let [curr-ns (-> asts first :statements first :args first :expr :form)
        required (required-namespaces asts)
        used (set (map #(-> % .ns .getName) (keys (var-freq asts))))]
    (for [ns (set/difference required used)]
      {:linter :unused-namespaces
       :msg (format "Namespace %s is never used in %s" ns curr-ns)})))



;; TBD: It would be good to recognize analyzed expressions that result
;; from things like (+ x y) in the source code.  These cannot be
;; recognized as they are below, since something like the Clojure
;; compiler's inlining is done to them by the analyzer.  Figure out
;; how to recognize them and test it.

(defn make-val-unused-action-map [rsrc-name]
  (let [sym-info-map (edn/read-string (slurp (io/resource rsrc-name)))]
    (into {}
          (for [[sym properties] sym-info-map]
            [(resolve sym)
             (cond (:macro properties) nil
                   (:pure-fn properties) :pure-fn
                   (:pure-fn-if-fn-args-pure properties) :pure-fn-if-fn-args-pure
                   (:warn-if-ret-val-unused properties) :warn-if-ret-val-unused
                   :else nil)]))))


(def ^:dynamic *warning-type-if-ret-val-unused* {})


(defn unused-ret-vals [{:keys [asts]}]
  (binding [*warning-type-if-ret-val-unused* (make-val-unused-action-map
                                              "var-info.edn")]
    (let [unused-ret-val-exprs (->> asts
                                    (mapcat util/ast-nodes)
                                    (mapcat :statements))
          should-use-ret-val-exprs
          (->> unused-ret-val-exprs
               (filter #(and (= :invoke (:op %))
                             (contains? % :fn)
                             (contains? (:fn %) :var)))
               (filter #(get
                         ;; sym-info/should-use-ret-val-vars
                         *warning-type-if-ret-val-unused*
                         (get-in % [:fn :var]))))]
      (doall
       (for [stmt should-use-ret-val-exprs]
         (let [v (get-in stmt [:fn :var])
               action (get *warning-type-if-ret-val-unused* v)]
           (case action
             :pure-fn
             {:linter :unused-ret-vals
              :msg (format "Pure function call return value is discarded: %s"
                           (:form stmt))}
             :pure-fn-if-fn-args-pure
             {:linter :unused-ret-vals
              :msg (format "Return value is discarded for a function that only has side effects if the functions passed to it as args have side effects: %s"
                           (:form stmt))}
             :warn-if-ret-val-unused
             {:linter :unused-ret-vals
              :msg (format "Should use return value of expression, but it is discarded: %s"
                           (:form stmt))})))))))


;; TODO: Unused locals
