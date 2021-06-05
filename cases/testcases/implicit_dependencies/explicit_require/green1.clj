(ns testcases.implicit-dependencies.explicit-require.green1
  "Exercises an explicit `require`, with aliasing")

(require '[clojure.string :as string])

string/blank?
