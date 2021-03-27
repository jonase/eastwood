(ns testcases.unusednsimport.consumer3
  ;; This require is needed to properly import the record below
  (:require [testcases.unusednsimport.defrecord])
  (:import (testcases.unusednsimport.defrecord A)))

;; Exercises forms nested deep into arbitrary code:
(defn sample []
  (for [x [(A. 1)]]
    x))
