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
