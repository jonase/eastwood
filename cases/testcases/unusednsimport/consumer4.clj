(ns testcases.unusednsimport.consumer4
  ;; This require is needed to properly import the record below
  (:require [testcases.unusednsimport.defrecord])
  (:import (testcases.unusednsimport.defrecord A)))

;; Exercises metadata:
(defn ^A sample []
  ;; return nil (and not `(A.)` so that we are certain that only metadata is being exercised)
  nil)
