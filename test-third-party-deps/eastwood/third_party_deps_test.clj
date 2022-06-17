(ns eastwood.third-party-deps-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [eastwood.lint]))

(when-not (System/getProperty "eastwood.internal.plugin-profile-active")
  (let [p "eastwood.internal.running-test-suite"
        v (System/getProperty p)]
    (assert (= "true" v)
            (format "The test suite should be running with the %s system property set, for extra test robustness"
                    p))))

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

;; https://github.com/jonase/eastwood/issues/436
(deftest jdbc-example
  (testing "Is able to process usages of the `clojure.java.jdbc` library without false positives"
    (is (= {:some-warnings false :some-errors false}
           (eastwood.lint/eastwood (assoc eastwood.lint/default-opts :namespaces #{'testcases.jdbc-example}))))))

(deftest core-async-example
  (testing "Handling of hard-to-analyze `go` forms"
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


  (let [base-opts (assoc eastwood.lint/default-opts :abort-on-core-async-exceptions? true)] ;; Make test more logically robust

    (testing "`alt!` can be used without false positives, see https://github.com/jonase/eastwood/issues/411"
      (is (= {:some-warnings false :some-errors false}
             (eastwood.lint/eastwood (assoc base-opts
                                            :namespaces #{'testcases.core-async-example.alt})))))

    (testing "`assert` can be used without false positives"
      (is (= {:some-warnings false :some-errors false}
             (eastwood.lint/eastwood (assoc base-opts
                                            :namespaces #{'testcases.core-async-example.assert})))))

    (testing "All other (relevant) silencings present in resource/clojure.clj"
      (is (= {:some-warnings false :some-errors false}
             (eastwood.lint/eastwood (assoc base-opts
                                            :namespaces #{'testcases.core-async-example.misc})))))))
