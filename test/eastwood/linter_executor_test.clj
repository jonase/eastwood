(ns eastwood.linter-executor-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [eastwood.lint]))

(def proof (atom []))

(deftest works
  (are [desc input pred] (testing input
                           (reset! proof [])
                           (with-out-str
                             (eastwood.lint/eastwood {:namespaces ['eastwood.test.linter-executor.sample-ns]
                                                      :builtin-config-files input}))
                           (is (pred @proof)
                               desc)
                           ;; Avoid duplicate failure reports, did the tests fail:
                           true)
    "The custom `set-linter-executor!` successfully runs"
    ["linter_executor.clj"]
    seq

    "The effect of custom `:builtin-config-files` is not persistent"
    []
    empty?))
