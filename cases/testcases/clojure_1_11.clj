(ns testcases.clojure-1-11
  "A namespace that exercises all new features in Clojure 1.11.0."
  (:require
   [clojure.java.math :as math]
   [totally-does-not-exist :as-alias does-not-exist]))

;; https://clojure.org/news/2021/03/18/apis-serving-people-and-programs
(defn destr [& {:keys [a b] :as opts}]
  [a b opts])

(defn uses-destr []
  [(destr :a 1)
   (destr {:a 1 :b 2})])

(defn uses-non-existing []
  ::does-not-exist/foo)

(defn uses-misc []
  [(random-uuid)
   (update-keys (fn [v]
                  (+ v v))
                {1 2
                 3 4})
   (update-vals (fn [v]
                  (+ v v))
                {1 2
                 3 4})
   (parse-long "1")
   (parse-double "1.1")
   (parse-uuid "fail")
   (parse-uuid "true")
   (NaN? :not-a-nan)
   (infinite? 42)
   (math/random)
   (math/round 2.4)])
