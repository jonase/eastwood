(ns eastwood.third-party-deps-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [eastwood.lint]))

(deftest speced-def-handling
  (testing "Is able to process usages of the `nedap.speced.def` library without false positives"
    (is (= {:some-warnings false :some-errors false}
           (eastwood.lint/eastwood (assoc eastwood.lint/default-opts :namespaces #{'testcases.speced-def-example}))))))
