(ns testcases.implicit-dependencies.explicit-require.green4
  "Exercises prefix notation, without aliases")

(require '[clojure [string]])

clojure.string/blank?
