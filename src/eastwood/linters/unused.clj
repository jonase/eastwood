(ns eastwood.linters.unused
  (:require [clojure.set :as set]
            [eastwood.util :as util])
  (:use analyze.core analyze.util))

(defn unused-locals* [binding-expr]
  (let [lbs (util/bound-locals binding-expr)
        free (apply set/union (map util/free-locals
                                   (:children binding-expr)))]
    (set/difference lbs free)))

(defn binding-expr? [expr]
  (#{:let :letfn :fn-method} (:op expr)))

(defmulti report (fn [expr locals] (:op expr)))

(defmethod report :let [expr locals]
  (if (> (count locals) 1)
    (println "Unused let-locals:" locals)
    (println "Unused let-local:" (first locals))))

(defmethod report :letfn [expr locals]
  (if (> (count locals) 1)
    (println "Unused letfn arguments:" locals)
    (println "Unused letfn argument:" (first locals))))

(defmethod report :fn-method [expr locals]
  (if (> (count locals) 1)
    (println "Unused fn arguments:" locals)
    (println "Unused fn argument:" (first locals))))

(def ^:dynamic ignore-local? #{'_ '&form '&env})

(defn unused-locals [exprs]
  (doseq [expr (mapcat expr-seq exprs)]
    (when (binding-expr? expr)
      (when-let [ul (seq (remove ignore-local? (unused-locals* expr)))]
        (report expr ul)))))

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


(comment ;; Fails on
  (def ignore [:children :Expr-obj :ObjMethod-obj :LocalBinding-obj :BindingInit-obj :env :method :init])
  (def src '(loop [[a] nil] a))
  (def boilerplate {:ns {:name 'user} :context :eval})
  (def analyzed (analyze-one boilerplate src))
  (unused-locals [analyzed])

  (apply print-expr analyzed ignore)

  (fn [] (let [G__12015 nil
               vec__12016 G__12015
               a (nth vec__12016 0 nil)]
           (do (let [G__12121 ?]
                 (do (let [vec__12123 ?
                           a ?]
                       (do 1 a)))))))
                 
                     
               
  )