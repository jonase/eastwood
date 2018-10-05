(ns eastwood.test.analyze-ns-test
  (:use [clojure.test])
  (:require [eastwood.analyze-ns :as analyze]))

(deftest test3
  (let [result (analyze/analyze-ns 'testcases.wrong-require :opt {:debug #{}})]
    (is (instance? java.io.FileNotFoundException (:exception result)))))
