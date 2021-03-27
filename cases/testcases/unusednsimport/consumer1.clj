(ns testcases.unusednsimport.consumer1
  ;; This require is needed to properly import the record below
  (:require [testcases.unusednsimport.defrecord])
  (:import (testcases.unusednsimport.defrecord A)))

;; Exercises top-level forms:
(A. 1)
