(ns testcases.core-async-example.misc
  (:require
   [clojure.core.async :refer [go]]
   [clojure.spec.alpha :as spec]
   [clojure.test :refer [are assert-expr deftest do-report is join-fixtures testing use-fixtures]])
  (:import
   (java.io PrintWriter)))

(defn sample1 []
  (go
    (spec/and ::foo ::bar)))

(defn sample2 []
  (go
    (spec/keys :req [::foo])))

(defn sample3 []
  (go
    (spec/coll-of int?)))

(defmethod assert-expr 'truthy?
  [msg [pred v]]
  `(do-report (if ~v
                {:type :pass, :message ~msg}
                {:type :fail, :message ~msg})))

(defn func [f] "")

(defn throws-exception []
  (throw (ex-info "" {})))

;; https://github.com/jonase/eastwood/issues/116
(deftest exercises-thrown
  (go
    (is (thrown? Exception (throws-exception)))))

;; https://github.com/jonase/eastwood/issues/313
(deftest exercises-truthy
  (go
    (is (truthy? 42))))

;; https://github.com/jonase/eastwood/issues/207
(deftest b-test
  (go
    (is (instance? String (func +)))))

(defn sample4 []
  (go
    (is false)))

(defn sample5 []
  (go
    (cond-> 1
      true inc)))

(defn sample6 [stream ^PrintWriter pw]
  (go
    (with-out-str
      (-> pw (.print "foo"))
      (spit stream 42)
      1)))

(defn sample7 [stream]
  (go
    (while true
      (throw (ex-info "." {})))))
