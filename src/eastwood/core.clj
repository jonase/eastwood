(set! *warn-on-reflection* false)

(ns eastwood.core
  (:require [analyze.core :as analyze]
            [clojure.string :as string]
            [clojure.set :as set]
            [eastwood.linters.misc :as misc]
            [eastwood.linters.deprecated :as deprecated]
            [eastwood.linters.unused :as unused]))


(defn analyze [ns-sym]
  (let [source-file (-> (name ns-sym)
                        (string/replace "." "/")
                        (string/replace "-" "_")
                        (str ".clj"))]
    (analyze/analyze-path source-file ns-sym)))

(def ^:private linters
  {:naked-use misc/naked-use
   :unused-private-vars misc/unused-private-vars
   :misplaced-docstrings misc/misplaced-docstrings
   ;; :non-dynamic-earmuffs misc/non-dynamic-earmuffs ; checked by compiler
   :reflection misc/reflection
   :deprecations deprecated/deprecations
   :unused-locals unused/unused-locals
   })

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

;(lint-ns 'brittle.core :only [:unused-locals])
