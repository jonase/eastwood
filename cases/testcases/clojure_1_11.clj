(ns testcases.clojure-1-11
  "A namespace that exercises all new features in Clojure 1.11.0."
  (:require
   [clojure.math :as math]
   [clojure.test :refer [deftest is]]
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
  [(abs -1)
   (random-uuid)
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

(deftest test-iteration
  ;; equivalence to line-seq
  (let [readme #(java.nio.file.Files/newBufferedReader (.toPath (java.io.File. "project.clj")))]
    (is (= (with-open [^java.io.BufferedReader r (readme)]
             (vec (iteration (fn [_] (.readLine r)))))
           (with-open [^java.io.BufferedReader r (readme)]
             (doall (line-seq r))))))

  ;; paginated API
  (let [items 12 pgsize 5
        src (vec (repeatedly items #(java.util.UUID/randomUUID)))
        api (fn [tok]
              (let [tok (or tok 0)]
                (when (< tok items)
                  {:tok (+ tok pgsize)
                   :ret (subvec src tok (min (+ tok pgsize) items))})))]
    (is (= src
           (mapcat identity (iteration api :kf :tok :vf :ret))
           (into [] cat (iteration api :kf :tok :vf :ret)))))

  (let [src [:a :b :c :d :e]
        api (fn [k]
              (let [k (or k 0)]
                (if (< k (count src))
                  {:item (nth src k)
                   :k (inc k)})))]
    (is (= [:a :b :c]
           (vec (iteration api
                           :some? (comp #{:a :b :c} :item)
                           :kf :k
                           :vf :item))
           (vec (iteration api
                           :kf #(some-> % :k #{0 1 2})
                           :vf :item))))))
