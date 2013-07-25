(ns eastwood.linters.unused
  (:require [clojure.set :as set]
            [eastwood.util :as util])
  (:use analyze.core analyze.util))

;; Unused private vars
(defn- private-defs [exprs]
  (->> (mapcat expr-seq exprs)
       (filter #(and (= :def (:op %))
                     (-> % :var meta :private)
                     (-> % :var meta :macro not))) ;; skip private macros
       (map :var)))

(defn- var-freq [exprs]
  (->> (mapcat expr-seq exprs)
       (filter #(contains? #{:var :the-var} (:op %)))
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

(defn- ignore-arg? [arg]
  (or (contains? #{'&env '&form} arg)
      (.startsWith (name arg) "_")))

(defn- params [fn-method]
  (let [required (:required-params fn-method)
        rest (:rest-param fn-method)
        params (if rest (cons rest required) required)]
    (set (map #(select-keys % [:sym :idx]) params))))

(defn- used-locals [exprs]
  (set
   (->> exprs
        (filter (util/op= :local-binding-expr))
        (map :local-binding)
        (map #(select-keys % [:sym :idx])))))

(defn- unused-fn-args* [fn-expr]
  (reduce set/union
          (for [method (:methods fn-expr)]
            (let [args (params method)]
              (set/difference args (used-locals (expr-seq (:body method))))))))

(defn unused-fn-args [exprs]
  (let [fn-exprs (->> (mapcat expr-seq exprs)
                      (filter (util/op= :fn-expr)))]
    (for [expr fn-exprs
          :let [unused (->> (unused-fn-args* expr)
                            (map :sym)
                            (remove ignore-arg?)
                            set)]
          :when (not-empty unused)]
      {:linter :unused-fn-args
       :msg (format "Function args %s are never used" unused)
       :line (-> expr :env :line)})))

;; TODO: Unused locals