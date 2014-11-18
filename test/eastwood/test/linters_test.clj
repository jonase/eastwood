(ns eastwood.test.linters-test
  (:use [clojure.test])
  (:use [eastwood.lint])
  (:require [clojure.data :as data]
            [clojure.string :as str]
            [eastwood.test.cc-compare :as ccmp])
  (:import (java.io File)))

;; TBD: It would be cleaner to make Eastwood's error reporting code
;; independent of Clojure's hash order, but I am going to do the
;; expedient thing and make a couple of tests expect different values
;; depending on the Clojure version.

(def clojure-1-6-or-later
  (>= (compare ((juxt :major :minor) *clojure-version*)
               [1 6])
      0))

(def clojure-1-7-or-later
  (>= (compare ((juxt :major :minor) *clojure-version*)
               [1 7])
      0))

(defn to-sorted-map [c]
  (if (map? c)
    (into (sorted-map-by ccmp/cc-cmp) c)
    c))

(defn make-sorted [c]
  (clojure.walk/postwalk to-sorted-map c))

(defn select-tested-keys [lint-warning]
  (select-keys lint-warning
               [:linter :msg :file :line :column]))

(defn msg-replace-auto-numbered-symbol-names
  "In warning messages, replace symbols like p__7346 containing
auto-generated numeric values with p__<num>, so they can be compared
against expected results that will not change from one run/whatever to
the next."
  [s]
  (str/replace s #"__\d+"
               (str/re-quote-replacement "__<num>")))

(defn warning-replace-auto-numbered-symbol-names [warn]
  (update-in warn [:msg] msg-replace-auto-numbered-symbol-names))

(defmacro lint-test [ns-sym linters opts expected-lint-result]
  `(is (= (make-sorted (take 2 (data/diff
                                (->> (lint-ns-noprint ~ns-sym ~linters ~opts)
                                     (map select-tested-keys)
                                     (map warning-replace-auto-numbered-symbol-names)
                                     frequencies)
                                ~expected-lint-result)))
          (make-sorted [nil nil]))))

(defn fname-from-parts [& parts]
  (str/join File/separator parts))


(deftest test1
  (lint-test
   'testcases.f01
   [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :wrong-arity :local-shadows-var :wrong-tag]
   {}
   {
    {:linter :redefd-vars,
     :msg
     (str "Var i-am-redefd def'd 2 times at line:col locations: "
          (fname-from-parts "testcases" "f01.clj") ":4:6 "
          (fname-from-parts "testcases" "f01.clj") ":5:6"),
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 5, :column 6}
    1,
    {:linter :misplaced-docstrings,
     :msg "Possibly misplaced docstring, foo2",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 12, :column 7}
    1,
    {:linter :misplaced-docstrings,
     :msg "Possibly misplaced docstring, foo5",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 31, :column 7}
    1,
    {:linter :redefd-vars,
     :msg (str "Var test1-redefd def'd 3 times at line:col locations: "
               (fname-from-parts "testcases" "f01.clj") ":42:10 "
               (fname-from-parts "testcases" "f01.clj") ":46:10 "
               (fname-from-parts "testcases" "f01.clj") ":49:10"),
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 46, :column 10}
    1,
    {:linter :redefd-vars,
     :msg (str "Var i-am-redefd2 def'd 2 times at line:col locations: "
               (fname-from-parts "testcases" "f01.clj") ":70:6 "
               (fname-from-parts "testcases" "f01.clj") ":73:8"),
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 73, :column 8}
    1,
    {:linter :def-in-def,
     :msg "There is a def of def-in-def1 nested inside def bar",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 82, :column 10}
    1,
    {:linter :wrong-arity,
     :msg "Function on var #'clojure.core/assoc called with 1 args, but it is only known to take one of the following args: [map key val]  [map key val & kvs]",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 91, :column 4}
    1,
    {:linter :wrong-arity,
     :msg "Function on var #'clojure.core/assoc called with 0 args, but it is only known to take one of the following args: [map key val]  [map key val & kvs]",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 94, :column 4}
    1,
    })

  ;; TBD: I do not know why the i-am-inner-defonce-sym warning appears
  ;; twice in the result.  Once would be enough.
  (lint-test
   'testcases.f02
   [:misplaced-docstrings :def-in-def :redefd-vars :wrong-arity
    :local-shadows-var :wrong-tag]
   {}
   {
    {:linter :redefd-vars,
     :msg (str "Var i-am-defonced-and-defmultid def'd 2 times at line:col locations: "
               (fname-from-parts "testcases" "f02.clj") ":8:10 "
               (fname-from-parts "testcases" "f02.clj") ":10:11"),
     :file (fname-from-parts "testcases" "f02.clj"),
     :line 10, :column 11}
    1,
    {:linter :redefd-vars,
     :msg (str "Var i-am-a-redefd-defmulti def'd 2 times at line:col locations: "
               (fname-from-parts "testcases" "f02.clj") ":16:11 "
               (fname-from-parts "testcases" "f02.clj") ":18:11"),
     :file (fname-from-parts "testcases" "f02.clj"),
     :line 18, :column 11}
    1,
    {:linter :def-in-def,
     :msg "There is a def of i-am-inner-defonce-sym nested inside def i-am-outer-defonce-sym",
     :file (fname-from-parts "testcases" "f02.clj"),
     :line 20, :column 42}
    2,
    }
   )

  (lint-test
   'testcases.f03
   [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :unused-namespaces :unused-ret-vals :unused-ret-vals-in-try :wrong-arity
    :wrong-tag]
   {}
   {})
  (lint-test
   'testcases.f04
   [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :wrong-arity :local-shadows-var :wrong-tag]
   {}
   {
    {:linter :local-shadows-var,
     :msg "local: replace invoked as function shadows var: #'clojure.core/replace",
     :file (fname-from-parts "testcases" "f04.clj"),
     :line 44, :column 14}
    1,
    {:linter :local-shadows-var,
     :msg "local: shuffle invoked as function shadows var: #'clojure.core/shuffle",
     :file (fname-from-parts "testcases" "f04.clj"),
     :line 47, :column 14}
    1,
    {:linter :local-shadows-var,
     :msg "local: count invoked as function shadows var: #'clojure.core/count",
     :file (fname-from-parts "testcases" "f04.clj"),
     :line 64, :column 20}
    1,
    })
  ;; The following test is known to fail with Clojure 1.5.1 because of
  ;; protocol method names that begin with "-".  See
  ;; http://dev.clojure.org/jira/browse/TANAL-17 and
  ;; http://dev.clojure.org/jira/browse/CLJ-1202
  (when clojure-1-6-or-later
    (lint-test
     'testcases.f05
     [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
      :wrong-arity :local-shadows-var :wrong-tag]
     {}
     {}))
  (lint-test
   'testcases.f06
   [:unused-fn-args :misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :wrong-arity :local-shadows-var :wrong-tag]
   {}
   {
    {:linter :unused-fn-args,
     :msg "Function arg y never used",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 5, :column 30}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg y never used",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 9, :column 31}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg w never used",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 20, :column 20}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg body never used",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 33, :column 23}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg z never used",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 50, :column 33}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg coll never used",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 63, :column 6}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg f never used",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 64, :column 11}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg f never used",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 68, :column 11}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg val never used",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 69, :column 13}
    1,
    })
  (let [common-expected-warnings
        {
    {:line 10, :column 5,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals-in-try,
     :msg "Pure static method call return value is discarded inside body of try: (. clojure.lang.Numbers (add 5 7))"}
    1,
    {:line 27, :column 5,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Constant value is discarded: 7"}
    1,
    {:line 27, :column 5,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Constant value is discarded: :a"}
    1,
    {:line 24, :column 5,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add i n)) i)) n)) i))"}
    1,
    {:line 25, :column 5,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (multiplyP n i))"}
    1,
    {:line 26, :column 5,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (clojure.core/quotient i n))"}
    1,
    {:line 27, :column 29,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Lazy function call return value is discarded: (map inc [1 2 3])"}
    1,
    {:line 27, :column 9,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (dissoc {:b 2} :b)"}
    1,
    {:line 28, :column 6,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} a n)"}
    1,
    {:line 28, :column 21,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i a))"}
    1,
    {:line 32, :column 3,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} x y)"}
    1,
    {:line 33, :column 3,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (apply + [1 2 3])"}
    1,
    {:line 34, :column 3,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Lazy function call return value is discarded: (filter print [1 2 3])"}
    1,
    {:line 35, :column 3,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg (if clojure-1-6-or-later
            "Should use return value of function call, but it is discarded: (disj! (transient #{:b :a}) :a)"
            "Should use return value of function call, but it is discarded: (disj! (transient #{:a :b}) :a)")}
    1,
    {:line 36, :column 3,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (minus (. clojure.lang.Numbers (minus x y)) x))"}
    1,
    {:line 60, :column 5,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (dec i))"}
    2,
    {:line 65, :column 5,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} i i)"}
    2,
    {:line 81, :column 5,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Constant value is discarded: :a"}
    1,
    {:line 81, :column 9,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (dissoc {:b 2} :b)"}
    1,
    {:line 82, :column 6,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} a n)"}
    1,
    {:line 82, :column 21,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i a))"}
    1,
    {:line 100, :column 5,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} i n)"}
    1,
    {:line 100, :column 28,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (add 3 n))"}
    1,
    {:line 100, :column 20,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i n))"}
    1,
    {:line 107, :column 3,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Pure function call return value is discarded: (assoc {} k v)"}
    1,
    {:line 112, :column 5,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Var value is discarded: repeatedly"}
    1,
    {:line 112, :column 16,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Local value is discarded: n"}
    1,
    {:line 112, :column 18,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Local value is discarded: x"}
    1,
    {:line 125, :column 7,
     :file (fname-from-parts "testcases" "f07.clj"),
     :linter :unused-ret-vals,
     :msg "Var value is discarded: gah"}
    1,
    }

        clojure-1-5-expected-warnings
        (assoc common-expected-warnings
          {:line 140, :column 3,
           :file (fname-from-parts "testcases" "f07.clj"),
           :linter :unused-ret-vals,
           :msg "Pure function call return value is discarded: (false? a)"}
          1)

        ;; Clojure 1.5 does not have clojure.core/some? so it does not
        ;; warn about calling that function when its return value is
        ;; unused.  Clojure 1.6 and later should.
        clojure-1-6-or-later-expected-warnings
        (assoc common-expected-warnings
          {:line 140, :column 3,
           :file (fname-from-parts "testcases" "f07.clj"),
           :linter :unused-ret-vals,
           :msg "Pure function call return value is discarded: (some? a)"}
          1)]
    (lint-test
     'testcases.f07
     [:unused-ret-vals :unused-ret-vals-in-try :deprecations :wrong-arity
      :local-shadows-var :wrong-tag]
     {}
     (if clojure-1-6-or-later
       clojure-1-6-or-later-expected-warnings
       clojure-1-5-expected-warnings)))
  (lint-test
   'testcases.deprecated
   [:deprecations :wrong-arity :local-shadows-var :wrong-tag]
   {}
   {
    {:linter :deprecations,
     :msg
     "Constructor 'public java.util.Date(int,int,int)' is deprecated.",
     :file (fname-from-parts "testcases" "deprecated.clj"),
     :line 7, :column 3}
    1,
    {:linter :deprecations,
     :msg
     "Static field 'public static final int java.awt.Frame.TEXT_CURSOR' is deprecated.",
     :file (fname-from-parts "testcases" "deprecated.clj"),
     :line 9, :column 3}
    1,
    {:linter :deprecations,
     :msg
     "Instance method 'public int java.util.Date.getMonth()' is deprecated.",
     :file (fname-from-parts "testcases" "deprecated.clj"),
     :line 11, :column 3}
    1,
    {:linter :deprecations,
     :msg "Var '#'clojure.core/replicate' is deprecated.",
     :file (fname-from-parts "testcases" "deprecated.clj"),
     :line 13, :column 4}
    1,
    })
  (lint-test
   'testcases.tanal-9
   [:misplaced-docstrings :def-in-def :redefd-vars :unused-fn-args
    :unused-ret-vals :unused-ret-vals-in-try :deprecations :wrong-arity
    :local-shadows-var :wrong-tag]
;;   [:misplaced-docstrings]
   {}  ;{:debug #{:all}}
   {})
  (lint-test
   'testcases.tanal-27
   [:misplaced-docstrings :def-in-def :redefd-vars :unused-fn-args
    :unused-ret-vals :unused-ret-vals-in-try :deprecations :wrong-arity
    :local-shadows-var :wrong-tag]
   {}
   {})
  (lint-test
   'testcases.keyword-typos
   [:keyword-typos :unused-ret-vals :unused-ret-vals-in-try
    :deprecations :wrong-arity :local-shadows-var :wrong-tag]
   {}
   {
    {:linter :keyword-typos,
     :msg "Possible keyword typo: :occuption instead of :occupation ?"}
    1,
    })
  (lint-test
   'testcases.isformsok
   [:suspicious-test :suspicious-expression :local-shadows-var :wrong-tag]
   {}
   {})
  (lint-test
   'testcases.testtest
   [:keyword-typos :suspicious-test :suspicious-expression
    :local-shadows-var :wrong-tag]
   {}
   {
    {:linter :suspicious-test,
     :msg "'is' form has string as first arg.  This will always pass.  If you meant to have a message arg to 'is', it should be the second arg, after the expression to test",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 11, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has string as first arg.  This will always pass.  If you meant to have a message arg to 'is', it should be the second arg, after the expression to test",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 13, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 17, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has a string inside (thrown? ...).  This string is ignored.  Did you mean it to be a message shown if the test fails, like (is (thrown? ...) \"message\")?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 61, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a string.  This string is ignored.  Did you mean to use thrown-with-msg? instead of thrown?, and a regex instead of the string?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 63, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 65, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 69, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 71, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "Found (= ...) form inside deftest.  Did you forget to wrap it in 'is', e.g. (is (= ...))?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 73, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "Found (= ...) form inside testing.  Did you forget to wrap it in 'is', e.g. (is (= ...))?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 77, :column 6}
    1,
    {:linter :suspicious-test,
     :msg "Found (contains? ...) form inside deftest.  Did you forget to wrap it in 'is', e.g. (is (contains? ...))?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 80, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has non-string as second arg.  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a string during test time, and you intended this, then ignore this warning.",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 82, :column 4}
    1,
    {:linter :suspicious-expression,
     :msg "= called with 1 args.  (= x) always returns true.  Perhaps there are misplaced parentheses?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 82, :column 7}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has non-string as second arg.  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a string during test time, and you intended this, then ignore this warning.",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 83, :column 4}
    1,
    {:linter :suspicious-expression,
     :msg "> called with 1 args.  (> x) always returns true.  Perhaps there are misplaced parentheses?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 83, :column 7}
    1,
    {:linter :suspicious-expression,
     :msg "min-key called with 2 args.  (min-key f x) always returns x.  Perhaps there are misplaced parentheses?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 84, :column 10}
    1,
    {:line 86, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "= called with 1 args.  (= x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 87, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "== called with 1 args.  (== x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 88, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "not= called with 1 args.  (not= x) always returns false.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 89, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "< called with 1 args.  (< x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 90, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "<= called with 1 args.  (<= x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 91, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "> called with 1 args.  (> x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 92, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg ">= called with 1 args.  (>= x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 93, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "min called with 1 args.  (min x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 94, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "max called with 1 args.  (max x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 95, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "min-key called with 2 args.  (min-key f x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 96, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "max-key called with 2 args.  (max-key f x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 97, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "+ called with 0 args.  (+) always returns 0.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 98, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "+ called with 1 args.  (+ x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 99, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "+' called with 0 args.  (+') always returns 0.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 100, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "+' called with 1 args.  (+' x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 101, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "* called with 0 args.  (*) always returns 1.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 102, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "* called with 1 args.  (* x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 103, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "*' called with 0 args.  (*') always returns 1.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 104, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "*' called with 1 args.  (*' x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 105, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "dissoc called with 1 args.  (dissoc map) always returns map.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 106, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "disj called with 1 args.  (disj set) always returns set.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 107, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "merge called with 0 args.  (merge) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 108, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "merge called with 1 args.  (merge map) always returns map.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 109, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "merge-with called with 1 args.  (merge-with f) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 110, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "merge-with called with 2 args.  (merge-with f map) always returns map.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 113, :column 18,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "interleave called with 0 args.  (interleave) always returns ().  Perhaps there are misplaced parentheses?"}
    1,
    {:line 114, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "pr-str called with 0 args.  (pr-str) always returns \"\".  Perhaps there are misplaced parentheses?"}
    1,
    {:line 115, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "print-str called with 0 args.  (print-str) always returns \"\".  Perhaps there are misplaced parentheses?"}
    1,
    {:line 116, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "with-out-str called with 0 args.  (with-out-str) always returns \"\".  Perhaps there are misplaced parentheses?"}
    1,
    {:line 117, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "pr called with 0 args.  (pr) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 118, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "print called with 0 args.  (print) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 119, :column 19,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "comp called with 0 args.  (comp) always returns identity.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 120, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "partial called with 1 args.  (partial f) always returns f.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 121, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "lazy-cat called with 0 args.  (lazy-cat) always returns ().  Perhaps there are misplaced parentheses?"}
    1,
    {:line 122, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "-> called with 1 args.  (-> x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 123, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "cond called with 0 args.  (cond) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 124, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "case called with 2 args.  (case x y) always returns y.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 125, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "condp called with 3 args.  (condp pred test-expr expr) always returns expr.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 126, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "when called with 1 args.  (when test) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 127, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "when-not called with 1 args.  (when-not test) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 128, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "when-let called with 1 args.  (when-let [x y]) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 129, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "doseq called with 1 args.  (doseq [x coll]) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 130, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "dotimes called with 1 args.  (dotimes [i n]) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 131, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "and called with 0 args.  (and) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 132, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "and called with 1 args.  (and x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 133, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "or called with 0 args.  (or) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 134, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "or called with 1 args.  (or x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 135, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "doto called with 1 args.  (doto x) always returns x.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 136, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "declare called with 0 args.  (declare) always returns nil.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 145, :column 7,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "= called with 1 args.  (= x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 146, :column 4,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-test,
     :msg "'is' form has non-string as second arg.  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a string during test time, and you intended this, then ignore this warning."}
    1,
    {:line 146, :column 7,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "== called with 1 args.  (== x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    {:line 156, :column 7,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "= called with 1 args.  (= x) always returns true.  Perhaps there are misplaced parentheses?"}
    1,
    })
  (lint-test
   'testcases.suspicious
   [:suspicious-test :suspicious-expression :local-shadows-var :wrong-tag]
   {}
   {
    {:linter :suspicious-expression,
     :msg "doto called with 1 args.  (doto x) always returns x.  Perhaps there are misplaced parentheses?",
     :file (fname-from-parts "testcases" "suspicious.clj"),
     :line 23, :column 5}
    1,
    })
  ;; It is strange that the :unlimited-use linter has nil for :line
  ;; and :column here, but integer values when I use it from the
  ;; command line.  What is going on here?
  (lint-test
   'testcases.unlimiteduse
   [:unlimited-use :local-shadows-var :wrong-tag]
   {}
   {
    {:linter :unlimited-use,
     :msg "Unlimited use of ([clojure.reflect] [clojure inspector] [clojure [set]] [clojure.java.io :as io]) in testcases.unlimiteduse",
     :file (fname-from-parts "testcases" "unlimiteduse.clj"),
     :line 6, :column 10}
    1,
    {:linter :unlimited-use,
     :msg "Unlimited use of ((clojure [pprint :as pp] [uuid :as u])) in testcases.unlimiteduse",
     :file (fname-from-parts "testcases" "unlimiteduse.clj"),
     :line 14, :column 10}
    1,
    })
  (lint-test
   'testcases.in-ns-switching
   [:unlimited-use :local-shadows-var :wrong-tag]
   {}
   {
    {:linter :unlimited-use,
     :msg "Unlimited use of (clojure.set [testcases.f01 :as t1]) in testcases.in-ns-switching",
     :file (fname-from-parts "testcases" "in_ns_switching.clj"),
     :line 4, :column 9}
    1,
    })
  (let [common-expected-warnings
        {
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lv1",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 7, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lv2",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 8, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: iv1",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 19, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: iv2",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 20, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lf1",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 31, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lf2",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 32, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lf3",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 33, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lf4",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 34, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: if1",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 35, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: if2",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 36, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: if3",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 37, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: if4",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 38, :column 1}
    1,
    {:linter :wrong-tag,
     :msg "Tag: (quote LinkedList) for return type of function on arg vector: [coll] should be Java class name (fully qualified if not in java.lang package)",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 84, :column 36}
    1,
    {:linter :wrong-tag,
     :msg "Tag: (quote LinkedList) for return type of function on arg vector: [coll] should be Java class name (fully qualified if not in java.lang package)",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 85, :column 33}
    1,
    {:linter :wrong-tag,
     :msg "Tag: LinkedList for return type of function on arg vector: [coll] should be fully qualified Java class name, or else it may cause exception if used from another namespace",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 87, :column 28}
    1,
    {:linter :wrong-tag,
     :msg "Tag: LinkedList for return type of function on arg vector: [coll] should be fully qualified Java class name, or else it may cause exception if used from another namespace",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 88, :column 25}
    1,
    }
        clojure-1-6-or-earlier-expected-warnings
        common-expected-warnings

        clojure-1-7-or-later-expected-warnings
        (assoc common-expected-warnings
    {:linter :wrong-tag,
     :msg "Tag: LinkedList for return type of function on arg vector: [& p__<num>] should be fully qualified Java class name, or else it may cause exception if used from another namespace",
     :file (fname-from-parts "testcases" "wrongtag.clj"),
     :line 93, :column 26}
    1)]
    (lint-test
     'testcases.wrongtag
     @#'eastwood.lint/default-linters
     {}
     (if clojure-1-7-or-later
       ;; This is actually the expected result only for 1.7.0-alpha2
       ;; or later, because the behavior changed with the fix for
       ;; CLJ-887, so it will fail if you run the test with
       ;; 1.7.0-alpha1.  I won't bother checking the version that
       ;; precisely, though.
       clojure-1-7-or-later-expected-warnings
       clojure-1-6-or-earlier-expected-warnings)))
  (lint-test
   'testcases.macrometa
   [:unlimited-use :local-shadows-var :wrong-tag :unused-meta-on-macro]
   {}
   {
    {:linter :unused-meta-on-macro,
     :msg "Java constructor call 'StringWriter.' has metadata with keys (:foo).  All metadata is eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 20, :column 28}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java constructor call 'StringWriter.' has metadata with keys (:tag).  All metadata is eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 21, :column 28}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java instance method call '.close' has metadata with keys (:foo).  All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 35, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java instance method call '.close' has metadata with keys (:foo).  All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 37, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java static method call 'Math/abs' has metadata with keys (:foo).  All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 44, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java static method call 'Math/abs' has metadata with keys (:foo).  All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 46, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Macro invocation of 'my-writer-macro' has metadata with keys (:foo) that are almost certainly ignored.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 76, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Macro invocation of 'my-writer-macro' has metadata with keys (:tag) that are almost certainly ignored.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 77, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Macro invocation of 'my-writer-macro' has metadata with keys (:foo :tag) that are almost certainly ignored.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 78, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java static field access 'Long/MAX_VALUE' has metadata with keys (:foo).  All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 92, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java static field access 'Long/MAX_VALUE' has metadata with keys (:foo).  All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 94, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java instance method/field access '.x' has metadata with keys (:foo).  All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 102, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java instance method/field access '.x' has metadata with keys (:foo).  All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 104, :column 20}
    1,
    })
  (lint-test
   'testcases.constanttestexpr
   [:constant-test]
   {}
   {
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: false",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 13, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: [nil]",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 14, :column 5}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: #{}",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 15, :column 6}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: {:a 1}",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 16, :column 5}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (quote ())",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 17, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (quote (\"string\"))",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 18, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: [(inc 41)]",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 19, :column 5}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: #{(dec 43)}",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 20, :column 6}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: {:a (/ 84 2)}",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 21, :column 5}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (seq {:a 1})",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 22, :column 5}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (clojure.core/not (quote x))",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 24, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (clojure.core/not false)",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 25, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (clojure.core/not [nil])",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 26, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (clojure.core/not #{})",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 27, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (clojure.core/not {:a 1})",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 28, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (clojure.core/not (quote ()))",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 29, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (clojure.core/not (quote (\"string\")))",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 30, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: [(inc 41)]",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 31, :column 9}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: #{(dec 43)}",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 32, :column 10}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: {:a (/ 84 2)}",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 33, :column 9}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (clojure.core/not (seq {:a 1}))",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 34, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: nil",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 36, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: [nil]",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 37, :column 11},
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: :x",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 50, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: false",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 64, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: [false]",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 67, :column 12}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: #{7 5}",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 68, :column 15}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (clojure.core/seq [1 2])",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 69, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: 7",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 71, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: false",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 72, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: true",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 84, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: [false]",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 85, :column 9}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: 0",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 111, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: 32",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 111, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (clojure.core/string? format-in__<num>__auto__)",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 122, :column 4}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (map? (list [:p \"a\"] [:p \"b\"]))",
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 127, :column 5}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: (map? (list [:p \"a\"] [:p \"b\"]))"
     :file (fname-from-parts "testcases" "constanttestexpr.clj"),
     :line 130, :column 9}
    1,
    })
  (lint-test
   'testcases.unusedlocals
   [:unused-locals :unused-private-vars]
   {}
   {
    {:linter :unused-locals,
     :msg "let bound symbol 'unused-first-should-warn' never used",
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 68, :column 11}
    1,
    {:linter :unused-locals,
     :msg "loop bound symbol 'unused-loop-symbol' never used",
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 80, :column 3}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'foo' never used"
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 112, :column 17}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'baz' never used"
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 112, :column 25}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'guh' never used"
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 112, :column 29}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'unused1' never used"
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 113, :column 11}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'unused2' never used"
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 113, :column 22}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'unused3' never used"
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 113, :column 40}
    1,
    {:linter :unused-private-vars,
     :msg "Private var 'upper-limit3' is never used"
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 139, :column 1}
    1,
    {:linter :unused-private-vars,
     :msg "Private var 'lower-limit3' is never used"
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 140, :column 1}
    1,
    {:linter :unused-private-vars,
     :msg "Private var 'foo10' is never used"
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 164, :column 1}
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
