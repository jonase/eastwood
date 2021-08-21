(ns testcases.refer-clojure-exclude.green
  (:refer-clojure :exclude [update])
  (:require
   [clojure.test :refer [deftest is]]))

(defn update [m k f]
  (println "A side effect!" m k f))

(defn sut [x]
  (* x 2))

(deftest uses-update
  (update {} :f inc)
  (is (= 42 (sut 21))))
