(ns eastwood.util-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [eastwood.util :as sut])
  (:import
   (java.io File)))

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

(deftest expand-exclude-linters
  (are [input expected] (testing input
                          (is (= expected
                                 (sut/expand-exclude-linters input)))
                          true)
    #{}                         #{}
    #{:foo}                     #{:foo}
    #{[:foo :bar]}              #{[:foo :bar]}
    #{[:foo #{:bar :baz}]}      #{[:foo :bar] [:foo :baz]}
    #{[:foo [:bar :baz]]}       #{[:foo :bar] [:foo :baz]}
    #{:foo [:foo :bar]}         #{:foo [:foo :bar]}
    #{:foo [:foo [:bar]]}       #{:foo [:foo :bar]}
    #{:foo [:foo #{:bar}]}      #{:foo [:foo :bar]}
    #{:foo [:foo [:bar :baz]]}  #{:foo [:foo :bar] [:foo :baz]}
    #{:foo [:foo #{:bar :baz}]} #{:foo [:foo :bar] [:foo :baz]}))

(deftest excludes-kind?
  (are [exclude-linters path expected] (testing [exclude-linters path]
                                         (is (= expected
                                                (sut/excludes-kind? path (sut/expand-exclude-linters exclude-linters))))
                                         true)
    #_exclude-linters     #_path       #_expected
    #{}                   [:foo :bar]  false
    #{:foo}               [:foo :bar]  true
    #{[:foo :bar]}        [:foo :bar]  true
    #{[:foo :bar]}        [:foo :baz]  false
    #{[:foo [:bar]]}      [:foo :baz]  false
    #{[:foo [:bar :baz]]} [:foo :baz]  true
    #{[:foo [:bar :baz]]} [:foo :quux] false))

(deftest dir-outside-root-dir?
  (are [input expected] (testing input
                          (is (= expected
                                 (sut/dir-outside-root-dir? input)))
                          true)
    (File. (System/getProperty "user.dir")) false
    (File. ".")                             false
    (File. "src")                           false
    (File. "/")                             true))

(deftest dir-superset?
  (are [candidate other expected] (testing [candidate other]
                                    (is (= expected
                                           (sut/dir-superset? (File. candidate) (File. other))))
                                    true)
    "/"            "."            true
    "/"            "src"          true
    "/"            "src/eastwood" true
    ;; In theory an x is a superset of itself, but we'll disregard that -
    ;; we prefer to accept a duplicate entry over a confusing error message:
    "/"            "/"            false
    "."            "."            false
    "src"          "src"          false
    "src/eastwood" "src/eastwood" false

    "."            "src"          true
    "src"          "."            false
    "src"          "test"         false
    "src/eastwood" "src"          false
    "src"          "src/eastwood" true))
