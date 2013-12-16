(ns eastwood.test.linters-test
  (:use [clojure.test])
  (:use [eastwood.core])
  (:require [clojure.data :as data]
            [eastwood.test.cc-compare :as ccmp]))

(defn to-sorted-map [c]
  (if (map? c)
    (into (sorted-map-by ccmp/cc-cmp) c)
    c))

(defn make-sorted [c]
  (clojure.walk/postwalk to-sorted-map c))

(defmacro lint-test [ns-sym linters opts expected-lint-result]
  `(is (= (make-sorted (data/diff
                        (frequencies (lint-ns-noprint ~ns-sym ~linters ~opts))
                        ~expected-lint-result))
          (make-sorted [nil nil ~expected-lint-result]))))


(deftest test1
  (lint-test
   'eastwood.test.testcases.f01
   [:misplaced-docstrings :def-in-def :redefd-vars]
   {}
   {
    {:linter :redefd-vars,
     :msg
     "Var #'eastwood.test.testcases.f01/i-am-redefd def'd 2 times at lines: 4 5",
     :line 5}
    1,
    {:linter :misplaced-docstrings,
     :msg "Possibly misplaced docstring, #'eastwood.test.testcases.f01/foo2",
     :line 12}
    1,
    {:linter :misplaced-docstrings,
     :msg "Possibly misplaced docstring, #'eastwood.test.testcases.f01/foo5",
     :line 31}
    1,
    {:linter :redefd-vars,
     :msg "Var #'eastwood.test.testcases.f01/test1-redefd def'd 3 times at lines: 42 46 49",
     :line 46}
    1,
    {:linter :redefd-vars,
     :msg "Var #'eastwood.test.testcases.f01/i-am-redefd2 def'd 2 times at lines: 70 73",
     :line 73}
    1,
    {:linter :def-in-def,
     :msg "There is a def of #'eastwood.test.testcases.f01/def-in-def1 nested inside def TBD",
     :line 82}
    1,
    })

  ;; TBD: I do not know why the i-am-inner-defonce-sym warning appears
  ;; twice in the result.  Once would be enough.
  (lint-test
   'eastwood.test.testcases.f02
   [:misplaced-docstrings :def-in-def :redefd-vars]
   {}
   {
    {:linter :redefd-vars,
     :msg "Var #'eastwood.test.testcases.f02/i-am-defonced-and-defmultid def'd 2 times at lines:  ",
     :line nil}
    1,
    {:linter :redefd-vars,
     :msg "Var #'eastwood.test.testcases.f02/i-am-a-redefd-defmulti def'd 2 times at lines:  ",
     :line nil}
    1,
    {:linter :def-in-def,
     :msg "There is a def of #'eastwood.test.testcases.f02/i-am-inner-defonce-sym nested inside def TBD",
     :line nil}
    2,
    }
   )

  (lint-test
   'eastwood.test.testcases.f03
   [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :unused-namespaces :unused-ret-vals]
   {}
   {})
  (lint-test
   'eastwood.test.testcases.f04
   [:misplaced-docstrings :def-in-def :redefd-vars]
   {}
   {})
  ;; The following test is known to fail with Clojure 1.5.1 because of
  ;; protocol method names that begin with "-".  See
  ;; http://dev.clojure.org/jira/browse/TANAL-17 and
  ;; http://dev.clojure.org/jira/browse/CLJ-1202
  (when (and (>= (:major *clojure-version*) 1)
             (>= (:minor *clojure-version*) 6))
    (lint-test
     'eastwood.test.testcases.f05
     [:misplaced-docstrings :def-in-def :redefd-vars]
     {}
     {}))
  (lint-test
   'eastwood.test.testcases.f06
   [:unused-fn-args :misplaced-docstrings :def-in-def :redefd-vars]
   {}
   {
    {:linter :unused-fn-args,
     :msg "Function args [y (line 5)] of (or within) fn-with-unused-args are never used",
     :line 5}
    1,
    {:linter :unused-fn-args,
     :msg "Function args [y (line 9)] of (or within) fn-with-unused-args2 are never used",
     :line 9}
    1,
    {:linter :unused-fn-args,
     :msg "Function args [w (line 20)] of (or within) fn-with-unused-args3 are never used",
     :line 19}
    1,
    {:linter :unused-fn-args,
     :msg "Function args [body (line 33)] of (or within) macro2 are never used",
     :line 33}
    1,
    {:linter :unused-fn-args,
     :msg "Function args [z (line 50)] of (or within) fn-with-unused-args4 are never used",
     :line 50}
    1,
    {:linter :unused-fn-args,
     :msg "Function args [val (line 69) f (line 68)] of (or within) protocol CollReduce type clojure.lang.ASeq method coll-reduce are never used",
     :line nil}
    1,
    {:linter :unused-fn-args,
     :msg "Function args [f (line 64) coll (line 63)] of (or within) protocol CollReduce type nil method coll-reduce are never used",
     :line nil}
    1,
    })
  (lint-test
   'eastwood.test.testcases.f07
   [:unused-ret-vals :unused-ret-vals-in-try]
   {}
   {
    {:line 10, :linter :unused-ret-vals-in-try, :msg "Pure static method call return value is discarded inside body of try: (. clojure.lang.Numbers (add 5 7))"}
    1,
    {:line 22, :linter :unused-ret-vals, :msg "Constant value is discarded inside unused-ret-vals33: 7"}
    1,
    {:line 22, :linter :unused-ret-vals, :msg "Constant value is discarded inside unused-ret-vals33: :a"}
    1,
    {:line 24, :linter :unused-ret-vals, :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add i n)) i)) n)) i))"}
    1,
    {:line 25, :linter :unused-ret-vals, :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (multiplyP n i))"}
    1,
    {:line 26, :linter :unused-ret-vals, :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (clojure.core/quotient i n))"}
    1,
    {:line 27, :linter :unused-ret-vals, :msg "Lazy function call return value is discarded: (map inc [1 2 3])"}
    1,
    {:line 27, :linter :unused-ret-vals, :msg "Pure function call return value is discarded: (dissoc {:b 2} :b)"}
    1,
    {:line 28, :linter :unused-ret-vals, :msg "Pure function call return value is discarded: (assoc {} a n)"}
    1,
    {:line 28, :linter :unused-ret-vals, :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i a))"}
    1,
    {:line 32, :linter :unused-ret-vals, :msg "Pure function call return value is discarded: (assoc {} x y)"}
    1,
    {:line 33, :linter :unused-ret-vals, :msg "Pure function call return value is discarded: (apply + [1 2 3])"}
    1,
    {:line 34, :linter :unused-ret-vals, :msg "Lazy function call return value is discarded: (filter print [1 2 3])"}
    1,
    {:line 35, :linter :unused-ret-vals, :msg "Should use return value of function call, but it is discarded: (disj! (transient #{:a :b}) :a)"}
    1,
    {:line 36, :linter :unused-ret-vals, :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (minus (. clojure.lang.Numbers (minus x y)) x))"}
    1,
    {:line 60, :linter :unused-ret-vals, :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (dec i))"}
    2,
    {:line 65, :linter :unused-ret-vals, :msg "Pure function call return value is discarded: (assoc {} i i)"} 2, {:line 79, :linter :unused-ret-vals, :msg "Constant value is discarded inside unused-ret-vals3: :a"}
    1,
    {:line 81, :linter :unused-ret-vals, :msg "Pure function call return value is discarded: (dissoc {:b 2} :b)"}
    1,
    {:line 82, :linter :unused-ret-vals, :msg "Pure function call return value is discarded: (assoc {} a n)"}
    1,
    {:line 82, :linter :unused-ret-vals, :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i a))"}
    1,
    {:line 100, :linter :unused-ret-vals, :msg "Pure function call return value is discarded: (assoc {} i n)"}
    1,
    {:line 100, :linter :unused-ret-vals, :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (add 3 n))"}
    1,
    {:line 100, :linter :unused-ret-vals, :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i n))"}
    1,
    {:line 107, :linter :unused-ret-vals, :msg "Pure function call return value is discarded: (assoc {} k v)"}
    1,
    })
  (lint-test
   'eastwood.test.testcases.deprecated
   [:deprecations]
   {}
   {
    {:linter :deprecations,
     :msg
     "Constructor 'public java.util.Date(int,int,int)' is deprecated.",
     :line 7}
    1,
    {:linter :deprecations,
     :msg
     "Static field 'public static final int java.awt.Frame.TEXT_CURSOR' is deprecated.",
     :line 9}
    1,
    {:linter :deprecations,
     :msg
     "Instance method 'public int java.util.Date.getMonth()' is deprecated.",
     :line 11}
    1,
    {:linter :deprecations,
     :msg "Var '#'clojure.core/replicate' is deprecated.",
     :line 13}
    1,
    })
  )
