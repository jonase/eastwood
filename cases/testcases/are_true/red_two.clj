(ns testcases.are-true.red-two
  (:require
   [clojure.test :refer [are deftest is join-fixtures testing use-fixtures]]))

(deftest foo
  (are [a b expected] (do
                        (is true)
                        true)
    1 2 3))
