(ns testcases.unusednsimport.consumer5
  ;; This require is needed to properly import the record below
  (:require [testcases.unusednsimport.defrecord])
  (:import (testcases.unusednsimport.defrecord A)))

;; Exercises simple defs:
(def thing (A. 1))
