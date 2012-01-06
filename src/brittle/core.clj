(ns brittle.core
  (:use clojure.java.io)) ; Naked use

(def *rebind-me* nil) ; non-dynamic, already checked by the compiler

(def ^:private unused) ; Never used

(defn inc2 [x] ; Misplaced docstring
  "inc by 2"
  (inc (inc x)))

(defn len [s] ; reflects
  (replicate 1 0) ; deprecated
  (.length s))

