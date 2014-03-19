(ns testcases.f05
  (:require [clojure.set :as set]))

;; The combination of two classes named IInterval and IIntervals, with
;; one being the same as the other except with an "s" appended, used
;; to exhibit a bug in tools.analyzer before ticket TANAL-7 was fixed.
;; The code is copied and modified from part of the library
;; core.logic, which I was testing Eastwood on when I discovered this
;; issue.

(alias 'core 'clojure.core)

(defprotocol IMergeDomains
  (-merge-doms [a b]))

(defprotocol IMemberCount
  (-member-count [dom]))

(defprotocol IInterval
  (-lb [this])
  (-ub [this]))

(defprotocol IIntervals
  (-intervals [this]))

(defprotocol ISortedDomain
  (-drop-one [this])
  (-drop-before [this n])
  (-keep-before [this n]))

(defprotocol ISet
  (-member? [this n])
  (-disjoint? [this that])
  (-intersection [this that])
  (-difference [this that]))


(declare finite-domain? domain disjoint?* sorted-set->domain
         intersection* difference*)

(deftype FiniteDomain [s min max]
  Object
  (equals [this that]
    (if (finite-domain? that)
      (if (= (-member-count this) (-member-count that))
        (= s (:s that))
        false)
      false))

  clojure.lang.ILookup
  (valAt [this k]
    (.valAt this k nil))
  (valAt [this k not-found]
    (case k
      :s s
      :min min
      :max max
      not-found))

  IMemberCount
  (-member-count [this] (count s))

  IInterval
  (-lb [_] min)
  (-ub [_] max)

  ISortedDomain
  (-drop-one [_]
    (let [s (disj s min)
          c (count s)]
      (cond
       (= c 1) (first s)
       (core/> c 1) (FiniteDomain. s (first s) max)
       :else nil)))

  (-drop-before [_ n]
    (apply domain (drop-while #(core/< % n) s)))

  (-keep-before [this n]
    (apply domain (take-while #(core/< % n) s)))

  ISet
  (-member? [this n]
    (if (s n) true false))

  (-disjoint? [this that]
    (cond
     (integer? that)
       (if (s that) false true)
     (instance? FiniteDomain that)
       (cond
         (core/< max (:min that)) true
         (core/> min (:max that)) true
         :else (empty? (set/intersection s (:s that))))
     :else (disjoint?* this that)))

  (-intersection [this that]
    (cond
     (integer? that)
       (when (-member? this that) that)
     (instance? FiniteDomain that)
       (sorted-set->domain (set/intersection s (:s that)))
     :else
       (intersection* this that)))

  (-difference [this that]
    (cond
     (integer? that)
       (sorted-set->domain (disj s that))
     (instance? FiniteDomain that)
       (sorted-set->domain (set/difference s (:s that)))
     :else
       (difference* this that)))

  IIntervals
  (-intervals [_] (seq s))

  IMergeDomains
  (-merge-doms [this that]
    (-intersection this that))

  )


(defn finite-domain? [x]
  (instance? FiniteDomain x))
