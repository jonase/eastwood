(set! *warn-on-reflection* false)

(ns eastwood.core
  (:require [analyze.core :as analyze]
            [clojure.string :as string]
            [clojure.set :as set]
            [eastwood.linters.misc :as misc]
            [eastwood.linters.deprecated :as deprecated]
            [eastwood.linters.unused :as unused]
            [eastwood.linters.reflection :as reflection]))


(defn analyze [ns-sym]
  (let [source-file (-> (name ns-sym)
                        (string/replace "." "/")
                        (string/replace "-" "_")
                        (str ".clj"))]
    (analyze/analyze-path source-file ns-sym)))

(def ^:private linters
  {:naked-use misc/naked-use
   :misplaced-docstrings misc/misplaced-docstrings
   ;; :non-dynamic-earmuffs misc/non-dynamic-earmuffs ; checked by compiler
   :reflection reflection/reflection
   :deprecations deprecated/deprecations
   :unused-locals unused/unused-locals ; Currently too slow to be practical
   :unused-private-vars unused/unused-private-vars})

(def ^:private all-linters (set (keys linters)))

(defn- lint [exprs kw]
  (println "==" kw "==")
  ((linters kw) exprs))

(defn lint-ns [ns-sym & {:keys [only exclude] 
                         :or {only (disj all-linters :unused-locals)
                              exclude nil}}]
  (let [linters (set/difference (set only) (set exclude))
        exprs (analyze ns-sym)]
    (doseq [linter linters]
      (lint exprs linter))))

;(lint-ns 'brittle.core)

