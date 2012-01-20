(ns eastwood.linters.unused
  (:require [clojure.set :as set]
            #_[eastwood.util :as util])
  (:use analyze.core analyze.util))

(defn binding-expr? [expr]
  (#{:let :letfn :fn} (:op expr)))

(defn locals-used [expr]
  (set (->> (expr-seq expr)
            (filter #(= (:op %) :local-binding-expr))
            (map #(-> % :local-binding :LocalBinding-obj)))))

(defn local-bindings [expr]
  (condp = (:op expr)
    :fn-method (if-let [rest-param (:rest-param expr)]
                 (conj (set (map :LocalBinding-obj (:required-params expr)))
                       (:LocalBinding-obj rest-param))
                 (set (map :LocalBinding-obj (:required-params expr))))
    :let (set (map #(-> % :local-binding :LocalBinding-obj)
                   (:binding-inits expr)))
    :letfn (set (map #(-> % :local-binding :LocalBinding-obj)
                      (:binding-inits expr)))))

(defn report [expr locals]
  (let [msg (condp = (:op  expr)
              :let "let-local"
              :let-fn "letfn argument"
              :fn-method "fn argument")]
    (if (> (count locals) 1)
      (let [msg (str msg "s:")]
        (apply println "Unused" msg locals))
      (let [msg (str msg ":")]
        (println "Unused" msg (first locals))))))

(def ^:dynamic *ignore-locals* #{'_ '&form '&env})

;; NOTE: destructured loop-locals are reported as unused
(defn unused-locals [exprs]
  (doseq [expr (mapcat expr-seq exprs)
          :when (binding-expr? expr)]
    (let [dl (local-bindings expr)
          lu (locals-used expr)
          diff (map #(.sym %) (set/difference dl lu))]
      (when (seq (remove *ignore-locals* diff))
        (report expr diff)))))

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


(comment
  
  (def src '(loop [[a] []] a))
  (def analyzed (analyze-one {:ns {:name 'user} :context :eval}
                             src))

  (unused-locals [analyzed])

  (print-expr (analyze-one {:ns {:name 'user} :context :eval}
                           '(let [a 0])))

  (def expr (first (drop-while #(not= (:op %) :let)
                                 (expr-seq analyzed))))
  
  (print-expr expr
              :children
              :env)

  (locals-used expr)

  (local-bindings expr)

  (set/difference (local-bindings expr)
                  (locals-used expr))
  
  )