(ns testcases.implicit-dependencies.explicit-require.green2
  "Exercises an explicit `require`, without aliasing")

(require 'clojure.string)

clojure.string/blank?
