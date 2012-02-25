(ns brittle.core
  (:import [java.awt Frame])
  (:use clojure.java.io)) ; Naked use

#_(def *rebind-me* nil) ; non-dynamic, already checked by the compiler

(def ^:private unused) ; Never used

(defn inc2 [x] ; Misplaced docstring
  "inc by 2"
  (inc (inc x)))

(defn len [s] ; reflects
  (replicate 1 0) ; deprecated
  (.length s)
  (.method s 0))

(defn foo [x] ; <- never used
  (let [a 0 x 1] ; <- a is never used
    x))



(defn hour [] ;; Deprecated x 2!
  Frame/TEXT_CURSOR
  (.getHours (java.util.Date. 2012 21 12)))


(defn defndef [x]
  (def a 3))

;; This is often ok.
(defmacro ^:private defindefmacro []
  (def b nil))

(defrecord SomeRecord [])