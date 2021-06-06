(ns testcases.deprecations.overloading.green2
  (:import
   (java.util Date)))

(defn expiry-date []
  (Date. 0))
