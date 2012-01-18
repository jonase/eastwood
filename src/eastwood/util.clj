(ns eastwood.util
  (:require [clojure.set :as set]))

;; TODO: Profile!

(defmulti bound-locals :op)
(defmulti free-locals :op)

(defmethod bound-locals :fn-method [expr]
  (let [required (set (map :sym (:required-params expr)))]
    (if-let [rest (:sym (:rest-param expr))]
      (conj required rest)
      required)))

(defmethod bound-locals :let [expr]
  (set (map (comp :sym :local-binding) (:binding-inits expr))))

(defmethod bound-locals :letfn [expr]
  (set (map (comp :sym :local-binding) (:binding-inits expr))))

(defmethod free-locals :local-binding-expr [expr]
  #{(:sym (:local-binding expr))})

(defn tails [seq]
  (take-while (complement empty?)
              (iterate rest seq)))

(declare free-locals*)
;; Tricky because the second 'a in (let [a a] a) is free.
(defmethod free-locals :let [expr]
  (let [free-in-bindings
        (let [local-bindings (map :local-binding (:binding-inits expr))]
          (loop [[lb & lbs] local-bindings
                 flocals (free-locals (:init (first local-bindings)))]
            (if lbs
              (let [free (apply set/union (map (comp free-locals :init) lbs))]
                (recur lbs (disj (set/union free flocals) (:sym lb))))
              flocals)))]
    (set/union
     (set/difference (free-locals (:body expr))
                     (bound-locals expr))
     free-in-bindings)))
              
(defn free-locals* [expr]
  (set/difference (apply set/union (map free-locals
                                          (:children expr)))
                  (bound-locals expr)))

(defmethod free-locals :fn-method [expr]
  (free-locals* expr))

(defmethod free-locals :letfn [expr]
  (free-locals* expr))

(defmethod free-locals :default [expr]
  (apply set/union (map free-locals (:children expr))))

(def binding-expr? #{:fn-method :let :letfn})
