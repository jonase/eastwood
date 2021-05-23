(ns testcases.clojure-test.are.red
  (:require
   [clojure.test :refer [are deftest testing]]))

(deftest are-with-testing
  (are [f] (testing f
             ;; should trigger a warning:
             (= "3" (f 1))
             ;; should trigger a warning:
             (= "2" (f 1))
             ;; should not trigger a warning (since it's located at tail position):
             (= "1" (f 1)))
    str
    pr-str))
