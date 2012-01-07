(ns eastwood.core
  (:require [analyze.core :as analyze]
            [clojure.string :as string]
            [clojure.set :as set]
            [eastwood.linters.core :as linters]
            [eastwood.linters.deprecated :as deprecated]))

(defn- analyze [ns-sym]
  (let [source-file (-> (name ns-sym)
                        (string/replace "." "/")
                        (string/replace "-" "_")
                        (str ".clj"))]
    (analyze/analyze-path source-file ns-sym)))

(def ^:private linters
  {:naked-use linters/naked-use
   :unused-private-vars linters/unused-private-vars
   :misplaced-docstrings linters/misplaced-docstrings
   ;:non-dynamic-earmuffs linters/non-dynamic-earmuffs ;checked by compiler
   :reflection linters/reflection
   :deprecated-vars deprecated/deprecated-vars})

(def ^:private all-linters (set (keys linters)))

(defn- lint [exprs kw]
  ((linters kw) exprs))

(defn lint-ns [ns-sym & {:keys [only exclude] 
                         :or {only all-linters
                              exclude nil}}]
  (let [namespaces (set/difference (set only) (set exclude))
        exprs (analyze ns-sym)]
    (doseq [ns namespaces]
      (lint exprs ns))))

;(lint-ns 'brittle.core :exclude [:naked-use])
