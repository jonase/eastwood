(defn custom-linter
  [analyze-results opts]
  (for [expr (mapcat ast/nodes (:asts analyze-results))
        :when (= (:op expr) :def)
        :let [^clojure.lang.Var v (:var expr)
              s (.sym v)
              loc (:env expr)]
        :when (re-find #"custom" (name s))]
    (add-loc-info loc
                  {:linter :custom-linter
                   :msg (format "%s shouldn't have the word 'custom'" v)})))

(add-linter
 {:name :custom-linter
  :fn custom-linter})
