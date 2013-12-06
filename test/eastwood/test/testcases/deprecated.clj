(ns eastwood.test.testcases.deprecated
  (:import java.util.Date
           java.awt.Frame))

(defn deprecated-stuff []
  ;; Constructor
  (Date. 2013 3 12)
  ;; Static Field
  Frame/TEXT_CURSOR
  ;; Method
  (.getMonth (Date.))
  ;; Var
  (replicate 1 nil))
