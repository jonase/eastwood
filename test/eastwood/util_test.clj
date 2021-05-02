(ns eastwood.util-test
  (:require
   [clojure.test :refer [are deftest is join-fixtures testing use-fixtures]]
   [eastwood.util :as sut]))

(deftest trim-thrown-form
  (testing "Removes the `Exception` from a `(is (thrown? Exception ...`"
    (are [input expected] (testing input
                            (is (= expected
                                   (sut/trim-thrown-form input)))
                            true)
      nil                                   nil
      1                                     1
      []                                    []
      {}                                    {}
      '(foo)                                '(foo)
      '(is (= 1 1))                         '(is (= 1 1))
      '(is (thrown? Exception (foo)))       '(is (thrown? (foo)))
      '(is (thrown? ::anything (foo)))      '(is (thrown? (foo)))
      '(do (is (thrown? Exception (foo))))  '(do (is (thrown? (foo))))
      '(do (is (thrown? ::anything (foo)))) '(do (is (thrown? (foo)))))))

(deftest in-thrown?
  (are [statement-form ancestor-form expected] (testing [statement-form ancestor-form]
                                                 (is (= expected
                                                        (sut/in-thrown? statement-form ancestor-form)))
                                                 true)
    '(+ 3 2)
    '(do (is (thrown? Exception (+ 3 2))))
    true

    '(+ 999 999)
    '(do (is (thrown? Exception (+ 3 2))))
    false))
