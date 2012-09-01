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

(defn lint [ast-map ls]
  (doseq [lint-fn (map linters ls)
          result (lint-fn ast-map)]
    (pp/pprint result)
    (println)))

;; TODO: error handling
(defn analyze-namespaces
  "Returns a map from namespace to ast data"
  [namespaces]
  (zipmap namespaces
          (map analyze/analyze-path namespaces)))

(defn run-eastwood [opts]
  (let [namespaces (set (or (:namespaces opts)
                            (mapcat #(-> % io/file clj-ns/find-namespaces-in-dir)
                                    (:source-paths opts))))
        excluded-namespaces (set (:exclude-namespaces opts))
        namespaces (set/difference namespaces excluded-namespaces)
        linters (set (or (:linters opts)
                         default-linters))
        excluded-linters (set (:exclude-linters opts))
        add-linters (set (:add-linters opts))
        linters (-> (set/difference linters excluded-linters)
                    (set/union add-linters))]
    (lint (analyze-namespaces namespaces) linters)))

