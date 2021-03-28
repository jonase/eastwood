(ns eastwood.analyze-ns-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [eastwood.analyze-ns :as sut]))

(deftest cleanup
  (testing "Removes `:const` metadata"
    (are [f input expected] (let [result (f input)]
                              (is (= result input)
                                  "Applying `f` should return an equivalent object (even after removing metadata)")
                              (= expected
                                 (-> result second meta :const)))
      identity    '(defn ^:const foo) true
      sut/cleanup '(defn ^:const foo) nil
      sut/cleanup '(defn foo)         nil)))
