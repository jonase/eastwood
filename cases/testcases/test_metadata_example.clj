(ns testcases.test-metadata-example
  "Exercises a defn with `:test` metadata"
  (:require
   [clojure.test :refer [is]]))

(defn foo
  {:test (fn []
           (is (= 42
                  (foo 21))))}
  [x]
  (* 2 x))
