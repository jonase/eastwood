(ns testcases.unusednsimport.consumer2
  ;; This require is needed to properly import the record below
  (:require [testcases.unusednsimport.defrecord])
  (:import (testcases.unusednsimport.defrecord A)))

;; Should be deemed unused:
(comment
  (A. 1))
