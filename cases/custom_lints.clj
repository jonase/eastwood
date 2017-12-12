(ns custom-lints)

(defn fake-linter
  [analyze-results opts]
  [{:linter :thing-linter
    :msg "we linted something at testcases/custom.clj:1:1"
    :file "testcases/custom.clj"
    :line 1
    :column 1}])

(add-linter
 {:name :thing-linter
  :fn fake-linter})
