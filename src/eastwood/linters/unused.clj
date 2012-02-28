(ns eastwood.linters.unused
  (:require [clojure.set :as set]
            #_[eastwood.util :as util])
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
       (filter #(= :var (:op %)))
       (map :var)
       frequencies))
  
(defn unused-private-vars [exprs]
  (let [pdefs (private-defs exprs)
        vfreq (var-freq exprs)]
    (doseq [pvar pdefs
            :when (nil? (vfreq pvar))]
      (println "Private var" pvar "is never used")))) 


;; Unused fn args

(def ignore-args '#{_ &env &form})

(defn- params [fn-method]
  (let [required (:required-params fn-method)
        rest (:rest-param fn-method)
        params (if rest (cons rest required) required)]
    (set (map #(select-keys % [:sym :idx]) params))))

(defn- used-locals [exprs]
  (set
   (->> exprs
        (filter (op= :local-binding-expr))
        (map :local-binding)
        (map #(select-keys % [:sym :idx])))))

(defn- unused-fn-args* [fn-expr]
  (reduce set/union
          (for [method (:methods fn-expr)]
            (let [args (params method)]
              (set/difference args (used-locals (expr-seq (:body method))))))))

(defn unused-fn-args [exprs]
  (let [fn-exprs (->> (mapcat expr-seq exprs)
                      (filter (op= :fn-expr)))]
    (doseq [expr fn-exprs]
      (let [unused (set/difference (set (map :sym (unused-fn-args* expr)))
                                   ignore-args)]
        (when-not (empty? unused)
          (println "Args" unused "in" (:name expr) "is never used"))))))


;; TODO: Unused locals