(ns testcases.deprecations.overloading.red
  (:import
   (java.util Date)))

(defn expiry-date []
  (Date. "12/12/1999"))
