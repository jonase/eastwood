(ns custom-lints
  (:require [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
            [eastwood.util :as util]))

(defn custom-linter
  [analyze-results opts]
  (for [expr (mapcat ast/nodes (:asts analyze-results))
        :when (= (:op expr) :def)
        :let [^clojure.lang.Var v (:var expr)
              s (.sym v)
              loc (:env expr)]
        :when (re-find #"custom" (name s))]
    (util/add-loc-info loc
                       {:linter :custom-linter
                        :msg (format "%s shouldn't have the word 'custom'" v)})))

(util/add-linter
 {:name :custom-linter
  :fn custom-linter})
