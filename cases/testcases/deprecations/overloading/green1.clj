(ns testcases.deprecations.overloading.green1
  (:import
   (java.util Date)))

(defn expiry-date []
  (-> (Date.) .getTime inc Date.))
