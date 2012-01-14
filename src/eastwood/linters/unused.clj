(ns eastwood.linters.unused
  (:require [clojure.set :as set]
            [eastwood.util :as util])
  (:use analyze.core analyze.util))

(defn unused-locals* [binding-expr]
  (let [lbs (util/local-bindings binding-expr)
        free (apply set/union (map util/free-locals
                                   (:children binding-expr)))]
    (set/difference lbs free)))

(defn binding-expr? [expr]
  (#{:let :let-fn :fn-method} (:op expr)))

(defn report [locals]
  ;; TODO: Line numbers?
  (println "Bindings" locals "are never used"))

(defn unused-locals [exprs]
  (doseq [expr (mapcat expr-seq exprs)]
    (if (and (binding-expr? expr))
      (when-let [ul (seq (for [{sym :sym} (unused-locals* expr)
                               :when (not= '_ sym)]
                           sym))]
        (report ul)))))
                      
