(ns testcases.is-false.green
  (:require
   [clojure.test :refer [are deftest is join-fixtures testing use-fixtures]]))

(deftest foo
  (is false))
