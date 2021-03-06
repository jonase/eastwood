(ns testcases.are-true.green
  (:require
   [clojure.test :refer [are deftest is join-fixtures testing use-fixtures]]))

(deftest foo
  (are [a b expected] (testing [a b]
                        (is (= expected
                               (+ a b)))
                        true)
    1 2 3))

(deftest bar
  (are [a b expected] (do
                        (is (= expected
                               (+ a b)))
                        true)
    1 2 3))

(deftest baz
  (are [a b expected] (let [x [a b]]
                        (is (= expected
                               (+ a b))
                            (pr-str x))
                        true)
    1 2 3))
