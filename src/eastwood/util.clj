(ns eastwood.util
  (:require [clojure.set :as set]))

;; TODO: Profile!

(defmulti bound-locals :op)
(defmulti free-locals :op)

(defn fn-method-bound-locals  [fnm]
  (let [required (set (map :sym (:required-params fnm)))]
    (if-let [rest (:sym (:rest-param fnm))]
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
              
(defn fn-method-free-locals [fnm]
  (set/difference (free-locals (:body fnm))
                  (fn-method-bound-locals fnm)))

(defmethod free-locals :fn-expr [expr]
  (apply set/union (map fn-method-free-locals (:methods expr))))

(defmethod free-locals :letfn [expr]
  (set/difference (apply set/union (map free-locals
                                        (:children expr)))
                  (bound-locals expr)))

(defmethod free-locals :default [expr]
  (apply set/union (map free-locals (:children expr))))

(def binding-expr? #{:fn-method :let :letfn})
