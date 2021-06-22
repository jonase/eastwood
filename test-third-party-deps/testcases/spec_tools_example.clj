(ns testcases.spec-tools-example
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as st]))

(s/def ::query
  (st/spec
   {:spec any?
    :swagger/example '{}
    :description "Foo"}))
