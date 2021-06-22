(ns eastwood.third-party-deps-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [eastwood.lint]))

(deftest speced-def-handling
  (testing "Is able to process usages of the `nedap.speced.def` library without false positives"
    (is (= {:some-warnings false :some-errors false}
           (eastwood.lint/eastwood (assoc eastwood.lint/default-opts :namespaces #{'testcases.speced-def-example}))))))

(deftest timbre-example
  (testing "Is able to process usages of the `taoensso.timbre` library without false positives"
    (is (= {:some-warnings false :some-errors false}
           (eastwood.lint/eastwood (assoc eastwood.lint/default-opts :namespaces #{'testcases.timbre-example}))))))

(deftest tufte-example
  (testing "Is able to process usages of the `taoensso.tufte` library without false positives"
    (is (= {:some-warnings false :some-errors false}
           (eastwood.lint/eastwood (assoc eastwood.lint/default-opts :namespaces #{'testcases.tufte-example}))))))

(deftest manifold-example
  (testing "Is able to process usages of the `manifold` library without false positives"
    (is (= {:some-warnings false :some-errors false}
           (eastwood.lint/eastwood (assoc eastwood.lint/default-opts :namespaces #{'testcases.manifold-example}))))))

(deftest spec-tools-example
  (testing "Is able to process usages of the `spec-tools` library without false positives"
    (is (= {:some-warnings false :some-errors false}
           (eastwood.lint/eastwood (assoc eastwood.lint/default-opts :namespaces #{'testcases.spec-tools-example}))))))

(deftest core-async-example
  (let [opts (assoc eastwood.lint/default-opts :namespaces #{'testcases.core-async-example})]

    (testing "When encountering `go` usages that throw exceptions, it omits the exception"
      (is (= {:some-warnings false :some-errors false}
             (eastwood.lint/eastwood opts))))

    ;; This counterexample is important not only for covering the option itself,
    ;; but also for making sure the previous test is logically valid:
    (testing "When encountering `go` usages that throw exceptions, and passing an opt-out flag,
it doesn't omit the exception"
      (is (= {:some-warnings true :some-errors true}
             (eastwood.lint/eastwood (assoc opts :abort-on-core-async-exceptions? true)))))))
