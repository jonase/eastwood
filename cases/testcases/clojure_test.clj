(ns testcases.clojure-test
  (:require
   [clojure.test :refer [are assert-expr deftest do-report is join-fixtures testing use-fixtures]]))

(defmethod assert-expr 'truthy?
  [msg [pred v]]
  `(do-report (if ~v
                {:type :pass, :message ~msg}
                {:type :fail, :message ~msg})))

(defn throws-exception []
  (throw (ex-info "" {})))

(defn func [f] "")

;; https://github.com/jonase/eastwood/issues/116
(deftest exercises-thrown
  (is (thrown? Exception (throws-exception))))

;; https://github.com/jonase/eastwood/issues/313
(deftest exercises-truthy
  (is (truthy? 42)))

;; https://github.com/jonase/eastwood/issues/207
(deftest b-test
  (is (instance? String (func +))))

;; https://github.com/jonase/eastwood/issues/206
(deftest a-test-singular
  ;; a test with a single `is`
  (is (thrown? Exception (compare [] 1))))

;; https://github.com/jonase/eastwood/issues/206
(deftest a-test-plural
  ;; a test with multiple `is`es
  (is (thrown? Exception (compare [] 1)))
  (is (thrown? Exception (+ 2 3)))
  (is (thrown? Exception (Long/parseLong "1"))))

;; https://github.com/jonase/eastwood/issues/206
(deftest a-test-are-1
  (are [ex] (thrown? ex (+ 1 2))
    Exception
    Error))

;; https://github.com/jonase/eastwood/issues/206
(deftest a-test-are-2
  (are [op] (thrown? Exception op)
    (+ 3 2)
    (Long/parseLong "31")
    (compare [] 1)))
