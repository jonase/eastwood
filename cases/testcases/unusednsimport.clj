(ns testcases.unusednsimport
  ; This require is needed to properly import the record below
  (:require [testcases.unusednsimport2])
  (:import (testcases.unusednsimport2 A)))

(A. 1)
