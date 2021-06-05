(ns testcases.implicit-dependencies.explicit-require.green3
  "Simulates `require`s placed in deeply nested code")

(when (= true
         (read-string (System/getProperty (-> (java.util.UUID/randomUUID) str)
                                          "true")))
  (doseq [_ [1]]
    (require 'clojure.string)))

clojure.string/blank?
