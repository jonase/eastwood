(ns eastwood.linters.unused
  (:require [clojure.set :as set]
            [eastwood.util :as util])
  (:use analyze.core analyze.util))

;; Unused private vars
(defn- defs [exprs public?]
  (->> (mapcat expr-seq exprs)
       (filter #(and (= :def (:op %))
                     (or public? (-> % :var meta :private))
                     (-> % :var meta :macro not))) ;; skip macros
       (map #(symbol (str (-> % :var meta :ns))
                     (str (-> % :var meta :name))))))

(defn- var-freq [exprs]
  (->> (mapcat expr-seq exprs)
       (filter #(= :var (:op %)))
       (map #(symbol (str (-> % :var meta :ns))
                     (str (-> % :var meta :name))))
       frequencies))
  
(defn unused-private-vars [ast-map]
  (mapcat (fn [[namespace exprs]]
            (let [pdefs (defs exprs false)
                  vfreq (var-freq exprs)]
              (for [pvar pdefs
                    :when (nil? (vfreq pvar))]
                {:linter :unused-private-vars
                 :msg (format "Private var %s is never used" pvar)
                 :line (-> pvar :env :line)
                 :ns namespace})))
          ast-map))
                    
;; Unused vars
(defn unused-vars [ast-map]
  (let [ds (set (defs (apply concat (vals ast-map)) true))
        vs (set (keys (var-freq (apply concat (vals ast-map)))))
        unused (set/difference ds vs)]
    (for [var unused]
      {:linter :unused-vars
       :msg (format "The var %s is never used" var)})))

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

(defn unused-fn-args [ast-map]
  (mapcat (fn [[namespace exprs]]
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
                 :line (-> expr :env :line)
                 :ns namespace})))
          ast-map))

;; TODO: Unused locals