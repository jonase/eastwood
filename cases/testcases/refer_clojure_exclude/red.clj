(ns testcases.refer-clojure-exclude.red
  (:require
   [clojure.test :refer [deftest is]]))

(defn sut [x]
  (* x 2))

(deftest uses-update
  (update {} :f inc)
  (is (= 42 (sut 21))))
