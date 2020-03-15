(ns eastwood.linter-executor-test
  (:require [clojure.test :refer :all]
            [eastwood.lint]))

(def proof (atom []))

(deftest works
  (are [desc input pred] (testing [desc input]
                           (reset! proof [])
                           (with-out-str
                             (eastwood.lint/eastwood {:namespaces ['eastwood.test.linter-executor.sample-ns]
                                                      :builtin-config-files input}))
                           (is (pred @proof))
                           ;; Avoid duplicate failure reports, did the tests fail:
                           true)
    "The custom `set-linter-executor!` successfully runs"
    ["linter_executor.clj"]
    seq

    "The effect of custom `:builtin-config-files` is not persistent"
    []
    empty?))
