(ns eastwood.test.implicit-dependencies-linter-test
  (:require [clojure.test :refer :all]
            [eastwood.util :as util]
            [eastwood.test.linters-test :as linters-test]))

(deftest implicit-dependency-linter
  (linters-test/lint-test
   'testcases.implicit_dependencies
   [:implicit-dependencies]
   {}
   {{:linter :implicit-dependencies,
     :msg
     "Var clojure.string/join refers to namespace clojure.string that isn't explicitly required.",
     :file "testcases/implicit_dependencies.clj",
     :line 4,
     :column 3}
    1})
  (when (util/clojure-1-10-or-later)
    (linters-test/lint-test
     'testcases.f08
     [:implicit-dependencies]
     {}
     {})))
