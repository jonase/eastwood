(ns eastwood.linters.unused
  (:require [clojure.set :as set]
            [clojure.string :as str]
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
       (filter #(= :var (:op %)))
       (map :var)
       frequencies))
  
(defn unused-private-vars [exprs]
  (let [pdefs (private-defs exprs)
        vfreq (var-freq exprs)]
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

(defn unused-fn-args [exprs]
  (let [fn-exprs (->> (mapcat util/ast-nodes exprs)
                      (filter (util/op= :fn)))]
    (for [expr fn-exprs
          :let [unused (->> (unused-fn-args* expr)
                            (map :form)
                            (remove ignore-arg?)
                            set)]
          :when (not-empty unused)]
      {:linter :unused-fn-args
       :msg (format "Function args [%s] of (or within) %s are never used"
                    (str/join " " unused)
                    (-> expr :env :name))
       :line (-> expr :env :name meta :line)})))


;; Unused namespaces

(defn required-namespaces [exprs]
  (->> (mapcat util/ast-nodes exprs)
       (filter #(and (= (:op %) :invoke)
                     (let [v (-> % :fexpr :var)]
                       (or (= v #'clojure.core/require)
                           (= v #'clojure.core/use)))))
       (mapcat :args)
       (map :val)
       (map #(if (coll? %) (first %) %))
       (remove keyword?)
       (into #{})))

(defn unused-namespaces [exprs]
  (let [curr-ns (-> exprs first :env :ns :name)
        required (required-namespaces exprs)
        used (set (map #(-> % .ns .getName) (keys (var-freq exprs))))]
    (for [ns (set/difference required used)]
      {:linter :unused-namespaces
       :msg (format "Namespace %s is never used in %s" ns curr-ns)})))

;; TODO: Unused locals
