(ns eastwood.core
  (:require [clojure.java.io :as io]
            [eastwood.analyze-ns :as analyze]
            [clojure.string :as string]
            [clojure.set :as set]
            [clojure.pprint :as pp]
            [clojure.repl :as repl]
            [clojure.tools.namespace :as clj-ns]
            [eastwood.linters.misc :as misc]
            [eastwood.linters.deprecated :as deprecated]
            [eastwood.linters.unused :as unused]
            [eastwood.linters.reflection :as reflection]
            [eastwood.linters.typos :as typos])
  (:import [java.io PushbackReader]
           [clojure.lang LineNumberingPushbackReader]))


(def ^:private linters
  {:naked-use misc/naked-use
   :misplaced-docstrings misc/misplaced-docstrings
   :def-in-def misc/def-in-def
   :redefd-vars misc/redefd-vars
   :reflection reflection/reflection
   :deprecations deprecated/deprecations
   :unused-fn-args unused/unused-fn-args
   :unused-private-vars unused/unused-private-vars
   :unused-namespaces unused/unused-namespaces
   :keyword-typos typos/keyword-typos})

(def ^:private default-linters
  #{;;:naked-use
    :misplaced-docstrings
    :def-in-def
    :redefd-vars
    ;;:reflection
    ;;:deprecations
    ;;:unused-fn-args
    ;;:unused-private-vars
    ;;:unused-namespaces
    ;;:keyword-typos
    })

(defn- lint [exprs kw]
  ((linters kw) exprs))

(defn lint-ns [ns-sym linters opts]
  (println "== Linting" ns-sym "==")
  (let [exprs (analyze/analyze-ns ns-sym :opt opts)]
    (doseq [linter linters
            result (lint exprs linter)]
      (pp/pprint result)
      (println))))

(defn lint-ns-noprint [ns-sym linters opts]
  (let [exprs (analyze/analyze-ns ns-sym :opt opts)]
    (mapcat #(lint exprs %) linters)))

(defn run-eastwood [opts]
  (let [namespaces (set (or (:namespaces opts)
                            (mapcat #(-> % io/file clj-ns/find-namespaces-in-dir)
                                    (concat (:source-paths opts) (:test-paths opts)))))
        excluded-namespaces (set (:exclude-namespaces opts))
        namespaces (set/difference namespaces excluded-namespaces)
        linters (set (or (:linters opts)
                         default-linters))
        excluded-linters (set (:exclude-linters opts))
        add-linters (set (:add-linters opts))
        linters (-> (set/difference linters excluded-linters)
                    (set/union add-linters))]
    (doseq [namespace namespaces]
      (try
        (lint-ns namespace linters opts)
        (catch RuntimeException e
          (println "Linting failed:")
          (repl/pst e 100))))))
