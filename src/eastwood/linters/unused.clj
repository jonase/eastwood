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
  (def env {:ns {:name 'user} :context :eval})
  (def analyzed (analyze-one env src))
  (unused-locals [analyzed])

  (apply print-expr analyzed ignore)

  ;;; Macroexpanding (loop ...):
  
  ;; (loop [[a] nil])
  ;; The first 'a is never used
  (let* [G__597 nil 
         vec__598 G__597
         a (nth vec__598 0 nil)]
        (loop* [G__597 G__597]
               (let* [vec__599 G__597
                      a (nth vec__599 0 nil)])))
        
  ;; (loop [[a :as form] nil])
  ;; the first 'a and 'form is never used
  (let* [G__602 nil
         vec__603 G__602
         a (nth vec__603 0 nil)
         form vec__603]
        (loop* [G__602 G__602]
               (let* [vec__604 G__602
                      a (clojure.core/nth vec__604 0 nil)
                      form vec__604])))
  
  
  ;; (loop [[a & rest :as form] nil])
  ;; The first 'a, 'rest and 'form is never used.
  (let* [G__608 nil
         vec__609 G__608
         a (clojure.core/nth vec__609 0 nil)
         rest (clojure.core/nthnext vec__609 1)
         form vec__609]
        (loop* [G__608 G__608]
               (let* [vec__610 G__608
                      a (clojure.core/nth vec__610 0 nil)
                      rest (clojure.core/nthnext vec__610 1)
                      form vec__610])))
  
  ;; (let [[a & rest :as form] nil]
  (let* [vec__613 nil
         a (clojure.core/nth vec__613 0 nil)
         rest (clojure.core/nthnext vec__613 1)
         form vec__613])       
  
               
  )