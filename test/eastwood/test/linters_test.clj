(ns eastwood.test.linters-test
  (:use [clojure.test])
  (:use [eastwood.core]))


(deftest test1
  (is (= (set (lint-ns-noprint 'eastwood.test.testcases.try4
                               [:misplaced-docstrings :def-in-def :redefd-vars] {}))
         #{{:linter :misplaced-docstrings,
            :msg "Possibly misplaced docstring, #'eastwood.test.testcases.try4/foo5",
            :line 31}
           {:linter :redefd-vars,
            :msg
            "Var #'eastwood.test.testcases.try4/i-am-redefd def'd 2 times at lines: 3 4",
            :line 4}
           {:linter :misplaced-docstrings,
            :msg "Possibly misplaced docstring, #'eastwood.test.testcases.try4/foo2",
            :line 11}}))
  )
