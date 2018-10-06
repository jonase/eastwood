(ns eastwood.test.implicit-dependencies-linter-test
  (:use [clojure.test])
  (:require [eastwood.test.linters-test :as linters-test]
            [clojure.data :as data]
            [clojure.pprint :as pp])
  (:import (java.io File)))


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
