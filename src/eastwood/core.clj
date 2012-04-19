(ns eastwood.core
  (:require [clojure.java.io :as io]
            [analyze.core :as analyze]
            [clojure.string :as string]
            [clojure.set :as set]
            [clojure.pprint :as pp]
            [clojure.tools.namespace :as clj-ns]
            [eastwood.linters.misc :as misc]
            [eastwood.linters.deprecated :as deprecated]
            [eastwood.linters.unused :as unused]
            [eastwood.linters.reflection :as reflection]
            [eastwood.linters.typos :as typos])
  (:import [java.io PushbackReader]
           [clojure.lang LineNumberingPushbackReader]))

(reset! analyze/JAVA-OBJ true)
(reset! analyze/CHILDREN true)

(def ^:private linters
  {:naked-use misc/naked-use
   :misplaced-docstrings misc/misplaced-docstrings
   :def-in-def misc/def-in-def
   :reflection reflection/reflection
   :deprecations deprecated/deprecations
   :unused-fn-args unused/unused-fn-args
   :unused-private-vars unused/unused-private-vars
   :keyword-typos typos/keyword-typos})

(def ^:private default-linters
  #{:naked-use
    :misplaced-docstrings
    :def-in-def
    :deprecations
    :unused-fn-args
    :keyword-typos
    :unused-private-vars})

(defn- lint [exprs kw]
  ((linters kw) exprs))

(defn lint-ns [ns-sym linters]
  (println "== Linting" ns-sym "==")
  (let [exprs (analyze/analyze-path ns-sym)]
    (doseq [linter linters
            result (lint exprs linter)]
      (pp/pprint result)
      (println))))

(defn run-eastwood [opts]
  (let [namespaces (set (or (:namespaces opts)
                            (mapcat #(-> % io/file clj-ns/find-namespaces-in-dir)
                                    (:source-paths opts))))
        excluded-namespaces (set (:exclude-namespaces opts))
        namespaces (set/difference namespaces excluded-namespaces)
        linters (set (or (:linters opts)
                         default-linters))
        excluded-linters (set (:exclude-linters opts))
        linters (set/difference linters excluded-linters)]
    (doseq [namespace namespaces]
      (lint-ns namespace linters))))

