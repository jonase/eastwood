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
     :msg "There is a def of def-in-def1 nested inside def bar",
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
     :msg "There is a def of i-am-inner-defonce-sym nested inside def i-am-outer-defonce-sym",
     :line 20}
    2,
    }
   )

  (lint-test
   'eastwood.test.testcases.f03
   [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :unused-namespaces :unused-ret-vals :unused-ret-vals-in-try]
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
     :msg "Function args [f (line 64) coll (line 64)] of (or within) protocol CollReduce type nil method coll-reduce are never used",
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
    {:line 110, :linter :unused-ret-vals, :msg "Var value is discarded inside gah: repeatedly"}
    1,
    {:line 110, :linter :unused-ret-vals, :msg "Local value is discarded inside gah: n"}
    1,
    {:line 110, :linter :unused-ret-vals, :msg "Local value is discarded inside gah: x"}
    1,
    {:line 114, :linter :unused-ret-vals, :msg "Var value is discarded inside check-do-let-nesting: gah"}
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
  (lint-test
   'eastwood.test.testcases.tanal-9
   [:misplaced-docstrings :def-in-def :redefd-vars :unused-fn-args
    :unused-ret-vals]
   {}
   {})
  (lint-test
   'eastwood.test.testcases.tanal-27
   [:misplaced-docstrings :def-in-def :redefd-vars :unused-fn-args
    :unused-ret-vals]
   {}
   {})
  (lint-test
   'eastwood.test.testcases.keyword-typos
   [:keyword-typos]
   {}
   {
    {:linter :keyword-typos,
     :msg "Possible keyword typo: :occuption instead of :occupation ?"}
    1,
    })
  (lint-test
   'eastwood.test.testcases.testtest
   [:suspicious-test :suspicious-expression]
   {}
   {
    {:linter :suspicious-test,
     :msg "'is' form has string as first arg.  This will always pass.  If you meant to have a message arg to 'is', it should be the second arg, after the expression to test",
     :line 11}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has string as first arg.  This will always pass.  If you meant to have a message arg to 'is', it should be the second arg, after the expression to test",
     :line 13}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test",
     :line 17}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has a string inside (thrown? ...).  This string is ignored.  Did you mean it to be a message shown if the test fails, like (is (thrown? ...) \"message\")?",
     :line 61}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a string.  This string is ignored.  Did you mean to use thrown-with-msg? instead of thrown?, and a regex instead of the string?",
     :line 63}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?",
     :line 65}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?",
     :line 69}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?",
     :line 71}
    1,
    {:linter :suspicious-test,
     :msg "Found (= ...) form inside deftest.  Did you forget to wrap it in 'is', e.g. (is (= ...))?",
     :line 73}
    1,
    {:linter :suspicious-test,
     :msg "Found (= ...) form inside testing.  Did you forget to wrap it in 'is', e.g. (is (= ...))?",
     :line 77}
    1,
    {:linter :suspicious-test,
     :msg "Found (contains? ...) form inside deftest.  Did you forget to wrap it in 'is', e.g. (is (contains? ...))?",
     :line 80}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has non-string as second arg.  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a string during test time, and you intended this, then ignore this warning.",
     :line 82}
    1,
    {:linter :suspicious-expression,
     :msg "= called with 1 args.  (= x) always returns true.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>",
     :line 82}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has non-string as second arg.  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a string during test time, and you intended this, then ignore this warning.",
     :line 83}
    1,
    {:linter :suspicious-expression,
     :msg "min-key called with 2 args.  (min-key f x) always returns x.  Perhaps there are misplaced parentheses?",
     :line 84}
    1,
    {:line 86, :linter :suspicious-expression, :msg "= called with 1 args.  (= x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 86, :linter :suspicious-expression, :msg "= called with 1 args.  (= x) always returns true.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 87, :linter :suspicious-expression, :msg "== called with 1 args.  (== x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 87, :linter :suspicious-expression, :msg "== called with 1 args.  (== x) always returns true.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 88, :linter :suspicious-expression, :msg "not= called with 1 args.  (not= x) always returns false.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 88, :linter :suspicious-expression, :msg "not= called with 1 args.  (not= x) always returns false.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 89, :linter :suspicious-expression, :msg "< called with 1 args.  (< x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 90, :linter :suspicious-expression, :msg "<= called with 1 args.  (<= x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 91, :linter :suspicious-expression, :msg "> called with 1 args.  (> x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 92, :linter :suspicious-expression, :msg ">= called with 1 args.  (>= x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 93, :linter :suspicious-expression, :msg "min called with 1 args.  (min x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 94, :linter :suspicious-expression, :msg "max called with 1 args.  (max x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 95, :linter :suspicious-expression, :msg "min-key called with 2 args.  (min-key f x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 96, :linter :suspicious-expression, :msg "max-key called with 2 args.  (max-key f x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 97, :linter :suspicious-expression, :msg "+ called with 0 args.  (+) always returns 0.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 98, :linter :suspicious-expression, :msg "+ called with 1 args.  (+ x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 99, :linter :suspicious-expression, :msg "+' called with 0 args.  (+') always returns 0.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 100, :linter :suspicious-expression, :msg "+' called with 1 args.  (+' x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 101, :linter :suspicious-expression, :msg "* called with 0 args.  (*) always returns 1.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 102, :linter :suspicious-expression, :msg "* called with 1 args.  (* x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 103, :linter :suspicious-expression, :msg "*' called with 0 args.  (*') always returns 1.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 104, :linter :suspicious-expression, :msg "*' called with 1 args.  (*' x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 105, :linter :suspicious-expression, :msg "dissoc called with 1 args.  (dissoc map) always returns map.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 106, :linter :suspicious-expression, :msg "disj called with 1 args.  (disj set) always returns set.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 107, :linter :suspicious-expression, :msg "merge called with 0 args.  (merge) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 108, :linter :suspicious-expression, :msg "merge called with 1 args.  (merge map) always returns map.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 109, :linter :suspicious-expression, :msg "merge-with called with 1 args.  (merge-with f) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 110, :linter :suspicious-expression, :msg "merge-with called with 2 args.  (merge-with f map) always returns map.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 113, :linter :suspicious-expression, :msg "interleave called with 0 args.  (interleave) always returns ().  Perhaps there are misplaced parentheses?"}
    1,
    {:line 114, :linter :suspicious-expression, :msg "pr-str called with 0 args.  (pr-str) always returns \"\".  Perhaps there are misplaced parentheses?"}
    1,
    {:line 115, :linter :suspicious-expression, :msg "print-str called with 0 args.  (print-str) always returns \"\".  Perhaps there are misplaced parentheses?"}
    1,
    {:line 116, :linter :suspicious-expression, :msg "with-out-str called with 0 args.  (with-out-str) always returns \"\".  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 117, :linter :suspicious-expression, :msg "pr called with 0 args.  (pr) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 118, :linter :suspicious-expression, :msg "print called with 0 args.  (print) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 119, :linter :suspicious-expression, :msg "comp called with 0 args.  (comp) always returns identity.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 120, :linter :suspicious-expression, :msg "partial called with 1 args.  (partial f) always returns f.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 121, :linter :suspicious-expression, :msg "lazy-cat called with 0 args.  (lazy-cat) always returns ().  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 122, :linter :suspicious-expression, :msg "-> called with 1 args.  (-> x) always returns x.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 123, :linter :suspicious-expression, :msg "cond called with 0 args.  (cond) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 124, :linter :suspicious-expression, :msg "case called with 2 args.  (case x y) always returns y.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 125, :linter :suspicious-expression, :msg "condp called with 3 args.  (condp pred test-expr expr) always returns expr.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 126, :linter :suspicious-expression, :msg "when called with 1 args.  (when test) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 127, :linter :suspicious-expression, :msg "when-not called with 1 args.  (when-not test) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 128, :linter :suspicious-expression, :msg "when-let called with 1 args.  (when-let [x y]) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 129, :linter :suspicious-expression, :msg "doseq called with 1 args.  (doseq [x coll]) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 130, :linter :suspicious-expression, :msg "dotimes called with 1 args.  (dotimes [i n]) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 131, :linter :suspicious-expression, :msg "and called with 0 args.  (and) always returns true.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 132, :linter :suspicious-expression, :msg "and called with 1 args.  (and x) always returns x.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 133, :linter :suspicious-expression, :msg "or called with 0 args.  (or) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 134, :linter :suspicious-expression, :msg "or called with 1 args.  (or x) always returns x.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 135, :linter :suspicious-expression, :msg "doto called with 1 args.  (doto x) always returns x.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    {:line 136, :linter :suspicious-expression, :msg "declare called with 0 args.  (declare) always returns nil.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"}
    1,
    })
  )
