(ns eastwood.test.linters-test
  (:use [clojure.test])
  (:use [eastwood.core])
  (:require [clojure.data :as data]
            [eastwood.test.cc-compare :as ccmp]))

;; TBD: It would be cleaner to make Eastwood's error reporting code
;; independent of Clojure's hash order, but I am going to do the
;; expedient thing and make a couple of tests expect different values
;; depending on the Clojure version.

(def clojure-1-6-or-later
  (>= (compare ((juxt :major :minor) *clojure-version*)
               [1 6])
      0))

(defn to-sorted-map [c]
  (if (map? c)
    (into (sorted-map-by ccmp/cc-cmp) c)
    c))

(defn make-sorted [c]
  (clojure.walk/postwalk to-sorted-map c))

(defmacro lint-test [ns-sym linters opts expected-lint-result]
  `(is (= (make-sorted (take 2 (data/diff
                                (frequencies (lint-ns-noprint ~ns-sym ~linters
                                                              ~opts))
                                ~expected-lint-result)))
          (make-sorted [nil nil]))))


(deftest test1
  (lint-test
   'testcases.f01
   [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :wrong-arity]
   {}
   {
    {:linter :redefd-vars,
     :msg
     "Var i-am-redefd def'd 2 times at line:col locations: testcases/f01.clj:4:6 testcases/f01.clj:5:6",
     :file "testcases/f01.clj",
     :line 5, :column 6}
    1,
    {:linter :misplaced-docstrings,
     :msg "Possibly misplaced docstring, foo2",
     :file "testcases/f01.clj",
     :line 12, :column 7}
    1,
    {:linter :misplaced-docstrings,
     :msg "Possibly misplaced docstring, foo5",
     :file "testcases/f01.clj",
     :line 31, :column 7}
    1,
    {:linter :redefd-vars,
     :msg "Var test1-redefd def'd 3 times at line:col locations: testcases/f01.clj:42:10 testcases/f01.clj:46:10 testcases/f01.clj:49:10",
     :file "testcases/f01.clj",
     :line 46, :column 10}
    1,
    {:linter :redefd-vars,
     :msg "Var i-am-redefd2 def'd 2 times at line:col locations: testcases/f01.clj:70:6 testcases/f01.clj:73:8",
     :file "testcases/f01.clj",
     :line 73, :column 8}
    1,
    {:linter :def-in-def,
     :msg "There is a def of def-in-def1 nested inside def bar",
     :file "testcases/f01.clj",
     :line 82, :column 10}
    1,
    {:linter :wrong-arity,
     :msg "Function on var #'clojure.core/assoc called with 1 args, but it is only known to take one of the following args: [map key val]  [map key val & kvs]",
     :file "testcases/f01.clj",
     :line 91, :column 4}
    1,
    {:linter :wrong-arity,
     :msg "Function on var #'clojure.core/assoc called with 0 args, but it is only known to take one of the following args: [map key val]  [map key val & kvs]",
     :file "testcases/f01.clj",
     :line 94, :column 4}
    1,
    })

  ;; TBD: I do not know why the i-am-inner-defonce-sym warning appears
  ;; twice in the result.  Once would be enough.
  (lint-test
   'testcases.f02
   [:misplaced-docstrings :def-in-def :redefd-vars :wrong-arity]
   {}
   {
    {:linter :redefd-vars,
     :msg "Var i-am-defonced-and-defmultid def'd 2 times at line:col locations: testcases/f02.clj:8:10 testcases/f02.clj:10:11",
     :file "testcases/f02.clj",
     :line 10, :column 11}
    1,
    {:linter :redefd-vars,
     :msg "Var i-am-a-redefd-defmulti def'd 2 times at line:col locations: testcases/f02.clj:16:11 testcases/f02.clj:18:11",
     :file "testcases/f02.clj",
     :line 18, :column 11}
    1,
    {:linter :def-in-def,
     :msg "There is a def of i-am-inner-defonce-sym nested inside def i-am-outer-defonce-sym",
     :file "testcases/f02.clj",
     :line 20, :column 42}
    2,
    }
   )

  (lint-test
   'testcases.f03
   [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :unused-namespaces :unused-ret-vals :unused-ret-vals-in-try :wrong-arity]
   {}
   {})
  (lint-test
   'testcases.f04
   [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :wrong-arity]
   {}
   {})
  ;; The following test is known to fail with Clojure 1.5.1 because of
  ;; protocol method names that begin with "-".  See
  ;; http://dev.clojure.org/jira/browse/TANAL-17 and
  ;; http://dev.clojure.org/jira/browse/CLJ-1202
  (when clojure-1-6-or-later
    (lint-test
     'testcases.f05
     [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
      :wrong-arity]
     {}
     {}))
  (lint-test
   'testcases.f06
   [:unused-fn-args :misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :wrong-arity]
   {}
   {
    {:linter :unused-fn-args,
     :msg "Function args [y (line 5, column 30)] of (or within) fn-with-unused-args are never used",
     :file "testcases/f06.clj",
     :line 5, :column 1}
    1,
    {:linter :unused-fn-args,
     :msg "Function args [y (line 9, column 31)] of (or within) fn-with-unused-args2 are never used",
     :file "testcases/f06.clj",
     :line 9, :column 1}
    1,
    {:linter :unused-fn-args,
     :msg "Function args [w (line 20, column 20)] of (or within) fn-with-unused-args3 are never used",
     :file "testcases/f06.clj",
     :line 19, :column 1}
    1,
    {:linter :unused-fn-args,
     :msg "Function args [body (line 33, column 23)] of (or within) macro2 are never used",
     :file "testcases/f06.clj",
     :line 33, :column 11}
    1,
    {:linter :unused-fn-args,
     :msg "Function args [z (line 50, column 33)] of (or within) fn-with-unused-args4 are never used",
     :file "testcases/f06.clj",
     :line 50, :column 1}
    1,
    {:linter :unused-fn-args,
     :msg "Function args [val (line 69, column 13) f (line 68, column 11)] of (or within) protocol CollReduce type clojure.lang.ASeq method coll-reduce are never used",
     :file nil,
     :line nil, :column nil}
    1,
    {:linter :unused-fn-args,
     :msg (if clojure-1-6-or-later
            "Function args [coll (line 63, column 6) f (line 64, column 11)] of (or within) protocol CollReduce type nil method coll-reduce are never used"
            "Function args [f (line 64, column 11) coll (line 64, column 6)] of (or within) protocol CollReduce type nil method coll-reduce are never used"),
     :file nil,
     :line nil, :column nil}
    1,
    })
  (let [common-expected-warnings
        {
    {:line 10, :column 5,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals-in-try,
     :msg "Pure static method call return value is discarded inside body of try: (. clojure.lang.Numbers (add 5 7))"}
    1,
    {:line 27, :column 5,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Constant value is discarded: 7"}
    1,
    {:line 27, :column 5,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Constant value is discarded: :a"}
    1,
    {:line 24, :column 5,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add i n)) i)) n)) i))"}
    1,
    {:line 25, :column 5,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (multiplyP n i))"}
    1,
    {:line 26, :column 5,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (clojure.core/quotient i n))"}
    1,
    {:line 27, :column 29,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Lazy function call return value is discarded: (map inc [1 2 3])"}
    1,
    {:line 27, :column 9,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (dissoc {:b 2} :b)"}
    1,
    {:line 28, :column 6,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} a n)"}
    1,
    {:line 28, :column 21,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i a))"}
    1,
    {:line 32, :column 3,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} x y)"}
    1,
    {:line 33, :column 3,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (apply + [1 2 3])"}
    1,
    {:line 34, :column 3,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Lazy function call return value is discarded: (filter print [1 2 3])"}
    1,
    {:line 35, :column 3,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg (if clojure-1-6-or-later
            "Should use return value of function call, but it is discarded: (disj! (transient #{:b :a}) :a)"
            "Should use return value of function call, but it is discarded: (disj! (transient #{:a :b}) :a)")}
    1,
    {:line 36, :column 3,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (minus (. clojure.lang.Numbers (minus x y)) x))"}
    1,
    {:line 60, :column 5,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (dec i))"}
    2,
    {:line 65, :column 5,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} i i)"}
    2,
    {:line 81, :column 5,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Constant value is discarded: :a"}
    1,
    {:line 81, :column 9,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (dissoc {:b 2} :b)"}
    1,
    {:line 82, :column 6,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} a n)"}
    1,
    {:line 82, :column 21,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i a))"}
    1,
    {:line 100, :column 5,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} i n)"}
    1,
    {:line 100, :column 28,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (add 3 n))"}
    1,
    {:line 100, :column 20,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i n))"}
    1,
    {:line 107, :column 3,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} k v)"}
    1,
    {:line 112, :column 5,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Var value is discarded: repeatedly"}
    1,
    {:line 112, :column 16,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Local value is discarded: n"}
    1,
    {:line 112, :column 18,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Local value is discarded: x"}
    1,
    {:line 125, :column 7,
     :file "testcases/f07.clj",
     :linter :unused-ret-vals,
     :msg "Var value is discarded: gah"}
    1,
    }

        clojure-1-5-expected-warnings
        (assoc common-expected-warnings
          {:line 140, :column 3,
           :file "testcases/f07.clj",
           :linter :unused-ret-vals,
           :msg "Pure function call return value is discarded: (false? a)"}
          1)

        ;; Clojure 1.5 does not have clojure.core/some? so it does not
        ;; warn about calling that function when its return value is
        ;; unused.  Clojure 1.6 and later should.
        clojure-1-6-or-later-expected-warnings
        (assoc common-expected-warnings
          {:line 140, :column 3,
           :file "testcases/f07.clj",
           :linter :unused-ret-vals,
           :msg "Pure function call return value is discarded: (some? a)"}
          1)]
    (lint-test
     'testcases.f07
     [:unused-ret-vals :unused-ret-vals-in-try :deprecations :wrong-arity]
     {}
     (if clojure-1-6-or-later
       clojure-1-6-or-later-expected-warnings
       clojure-1-5-expected-warnings)))
  (lint-test
   'testcases.deprecated
   [:deprecations :wrong-arity]
   {}
   {
    {:linter :deprecations,
     :msg
     "Constructor 'public java.util.Date(int,int,int)' is deprecated.",
     :file "testcases/deprecated.clj",
     :line 7, :column 3}
    1,
    {:linter :deprecations,
     :msg
     "Static field 'public static final int java.awt.Frame.TEXT_CURSOR' is deprecated.",
     :file "testcases/deprecated.clj",
     :line 9, :column 3}
    1,
    {:linter :deprecations,
     :msg
     "Instance method 'public int java.util.Date.getMonth()' is deprecated.",
     :file "testcases/deprecated.clj",
     :line 11, :column 3}
    1,
    {:linter :deprecations,
     :msg "Var '#'clojure.core/replicate' is deprecated.",
     :file "testcases/deprecated.clj",
     :line 13, :column 4}
    1,
    })
  (lint-test
   'testcases.tanal-9
   [:misplaced-docstrings :def-in-def :redefd-vars :unused-fn-args
    :unused-ret-vals :unused-ret-vals-in-try :deprecations :wrong-arity]
;;   [:misplaced-docstrings]
   {}  ;{:debug #{:all}}
   {})
  (lint-test
   'testcases.tanal-27
   [:misplaced-docstrings :def-in-def :redefd-vars :unused-fn-args
    :unused-ret-vals :unused-ret-vals-in-try :deprecations :wrong-arity]
   {}
   {})
  (lint-test
   'testcases.keyword-typos
   [:keyword-typos :unused-ret-vals :unused-ret-vals-in-try
    :deprecations :wrong-arity]
   {}
   {
    {:linter :keyword-typos,
     :msg "Possible keyword typo: :occuption instead of :occupation ?"}
    1,
    })
  (lint-test
   'testcases.isformsok
   [:suspicious-test :suspicious-expression]
   {}
   {})
  (lint-test
   'testcases.testtest
   [:keyword-typos :suspicious-test :suspicious-expression]
   {}
   {
    {:linter :suspicious-test,
     :msg "'is' form has string as first arg.  This will always pass.  If you meant to have a message arg to 'is', it should be the second arg, after the expression to test",
     :file "testcases/testtest.clj",
     :line 11, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has string as first arg.  This will always pass.  If you meant to have a message arg to 'is', it should be the second arg, after the expression to test",
     :file "testcases/testtest.clj",
     :line 13, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test",
     :file "testcases/testtest.clj",
     :line 17, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has a string inside (thrown? ...).  This string is ignored.  Did you mean it to be a message shown if the test fails, like (is (thrown? ...) \"message\")?",
     :file "testcases/testtest.clj",
     :line 61, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a string.  This string is ignored.  Did you mean to use thrown-with-msg? instead of thrown?, and a regex instead of the string?",
     :file "testcases/testtest.clj",
     :line 63, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?",
     :file "testcases/testtest.clj",
     :line 65, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?",
     :file "testcases/testtest.clj",
     :line 69, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?",
     :file "testcases/testtest.clj",
     :line 71, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "Found (= ...) form inside deftest.  Did you forget to wrap it in 'is', e.g. (is (= ...))?",
     :file "testcases/testtest.clj",
     :line 73, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "Found (= ...) form inside testing.  Did you forget to wrap it in 'is', e.g. (is (= ...))?",
     :file "testcases/testtest.clj",
     :line 77, :column 6}
    1,
    {:linter :suspicious-test,
     :msg "Found (contains? ...) form inside deftest.  Did you forget to wrap it in 'is', e.g. (is (contains? ...))?",
     :file "testcases/testtest.clj",
     :line 80, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has non-string as second arg.  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a string during test time, and you intended this, then ignore this warning.",
     :file "testcases/testtest.clj",
     :line 82, :column 4}
    1,
    {:linter :suspicious-expression,
     :msg "= called with 1 args.  (= x) always returns true.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>",
     :file "testcases/testtest.clj",
     :line 82, :column 8}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has non-string as second arg.  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a string during test time, and you intended this, then ignore this warning.",
     :file "testcases/testtest.clj",
     :line 83, :column 4}
    1,
    {:linter :suspicious-expression,
     :msg "min-key called with 2 args.  (min-key f x) always returns x.  Perhaps there are misplaced parentheses?",
     :file "testcases/testtest.clj",
     :line 84, :column 10}
    1,
    {:line 86, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "= called with 1 args.  (= x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 86, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "= called with 1 args.  (= x) always returns true.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 87, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "== called with 1 args.  (== x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 87, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "== called with 1 args.  (== x) always returns true.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 88, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "not= called with 1 args.  (not= x) always returns false.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 88, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "not= called with 1 args.  (not= x) always returns false.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 89, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "< called with 1 args.  (< x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 90, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "<= called with 1 args.  (<= x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 91, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "> called with 1 args.  (> x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 92, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg ">= called with 1 args.  (>= x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 93, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "min called with 1 args.  (min x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 94, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "max called with 1 args.  (max x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 95, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "min-key called with 2 args.  (min-key f x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 96, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "max-key called with 2 args.  (max-key f x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 97, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "+ called with 0 args.  (+) always returns 0.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 98, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "+ called with 1 args.  (+ x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 99, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "+' called with 0 args.  (+') always returns 0.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 100, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "+' called with 1 args.  (+' x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 101, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "* called with 0 args.  (*) always returns 1.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 102, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "* called with 1 args.  (* x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 103, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "*' called with 0 args.  (*') always returns 1.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 104, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "*' called with 1 args.  (*' x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 105, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "dissoc called with 1 args.  (dissoc map) always returns map.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 106, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "disj called with 1 args.  (disj set) always returns set.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 107, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "merge called with 0 args.  (merge) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 108, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "merge called with 1 args.  (merge map) always returns map.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 109, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "merge-with called with 1 args.  (merge-with f) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 110, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "merge-with called with 2 args.  (merge-with f map) always returns map.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 113, :column 18,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "interleave called with 0 args.  (interleave) always returns ().  Perhaps there are misplaced parentheses?"}
    1,
    {:line 114, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "pr-str called with 0 args.  (pr-str) always returns \"\".  Perhaps there are misplaced parentheses?"}
    1,
    {:line 115, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "print-str called with 0 args.  (print-str) always returns \"\".  Perhaps there are misplaced parentheses?"}
    1,
    {:line 116, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "with-out-str called with 0 args.  (with-out-str) always returns \"\".  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 117, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "pr called with 0 args.  (pr) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 118, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "print called with 0 args.  (print) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 119, :column 19,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "comp called with 0 args.  (comp) always returns identity.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 120, :column 16,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "partial called with 1 args.  (partial f) always returns f.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 121, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "lazy-cat called with 0 args.  (lazy-cat) always returns ().  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 122, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "-> called with 1 args.  (-> x) always returns x.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 123, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "cond called with 0 args.  (cond) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 124, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "case called with 2 args.  (case x y) always returns y.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 125, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "condp called with 3 args.  (condp pred test-expr expr) always returns expr.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 126, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "when called with 1 args.  (when test) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 127, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "when-not called with 1 args.  (when-not test) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 128, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "when-let called with 1 args.  (when-let [x y]) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 129, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "doseq called with 1 args.  (doseq [x coll]) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 130, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "dotimes called with 1 args.  (dotimes [i n]) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 131, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "and called with 0 args.  (and) always returns true.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 132, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "and called with 1 args.  (and x) always returns x.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 133, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "or called with 0 args.  (or) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 134, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "or called with 1 args.  (or x) always returns x.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 135, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "doto called with 1 args.  (doto x) always returns x.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 136, :column 17,
     :file "testcases/testtest.clj",
     :linter :suspicious-expression,
     :msg "declare called with 0 args.  (declare) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    })
  ;; It is strange that the :unlimited-use linter has nil for :line
  ;; and :column here, but integer values when I use it from the
  ;; command line.  What is going on here?
  (lint-test
   'testcases.unlimiteduse
   [:unlimited-use]
   {}
   {
    {:linter :unlimited-use,
     :msg "Unlimited use of (clojure.test [clojure.reflect] [clojure inspector] [clojure [set]] [clojure.java.io :as io]) in testcases.unlimiteduse",
     :file "testcases/unlimiteduse.clj",
     :line 5, :column 9}
    1,
    {:linter :unlimited-use,
     :msg "Unlimited use of ((clojure [pprint :as pp] [uuid :as u])) in testcases.unlimiteduse",
     :file "testcases/unlimiteduse.clj",
     :line 14, :column 10}
    1,
    })
  (lint-test
   'testcases.in-ns-switching
   [:unlimited-use]
   {}
   {
    {:linter :unlimited-use,
     :msg "Unlimited use of (clojure.test clojure.set [testcases.f01 :as t1]) in testcases.in-ns-switching",
     :file "testcases/in_ns_switching.clj",
     :line 3, :column 9}
    1,
    })
  ;; I would prefer if this threw an exception, but I think it does
  ;; not because Clojure reads, analyzes, and evaluates the namespace
  ;; before lint-test does, and thus the namespace is already there
  ;; when analyze-ns re-analyzes it.  I tried a variation of the
  ;; lint-test macro that did remove-ns first, but that caused other
  ;; tests to fail for reasons that I did not spend long enough to
  ;; learn the reason for.
;;  (lint-test
;;   'testcases.topleveldo
;;   [:redefd-vars :unlimited-use]
;;   {}
;;   {})
  )
