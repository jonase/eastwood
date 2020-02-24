(ns testcases.wrongprepost2
  (:require [clojure.spec.alpha :as spec]))


(defn issue-219-fn [data]
  {:pre [(spec/assert ::my-spec data)]}
  (assoc data 5 7))

(def ^:dynamic *dynvar* nil)

(defn issue-190-fn [x]
  {:pre [*dynvar*]}
  x)
