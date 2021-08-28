(ns eastwood.test.linters-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [eastwood.lint]
   [eastwood.reporting-callbacks :as reporting-callbacks]
   [eastwood.util :as util]
   [lambdaisland.deep-diff])
  (:import
   (java.io File)))

;; TBD: It would be cleaner to make Eastwood's error reporting code
;; independent of Clojure's hash order, but I am going to do the
;; expedient thing and make a couple of tests expect different values
;; depending on the Clojure version.

(def lint-warning-map-keys-for-testing-in-order
  [:linter
   :msg
   :file
   :line
   :column])

(def empty-ordered-lint-warning-map-for-testing
  (util/ordering-map lint-warning-map-keys-for-testing-in-order))

(defn normalize-warning [lint-warning]
  (into empty-ordered-lint-warning-map-for-testing
        (select-keys lint-warning lint-warning-map-keys-for-testing-in-order)))

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

(def empty-sorted-lint-warning-frequencies-map
  (sorted-map-by (fn [w1 w2]
                   (compare ((juxt :line :column :linter :msg) w1)
                            ((juxt :line :column :linter :msg) w2)))))

(def default-test-opts {})

;; If an exception occurs during analyze, re-throw it. This will
;; cause any test written that calls lint-ns-noprint to fail, unless
;; it expects the exception.
(defn lint-ns-noprint [ns-sym linters opts]
  (let [opts (assoc opts
                    :linters linters
                    :exclude-linters [:reflection]
                    :debug #{})
        opts (eastwood.lint/last-options-map-adjustments opts (reporting-callbacks/silent-reporter opts))
        cb (fn [info]
             (case (:kind info)
               (:eval-out :eval-err) (println (:msg info))
               :default-do-nothing))
        opts (assoc opts :callback cb)
        {:keys [exception lint-results]} (eastwood.lint/lint-ns ns-sym linters opts)]
    (if-not exception
      (->> lint-results
           (mapcat :lint-warning)
           (map :warn-data))
      (throw (:exception exception)))))

(defn lint-test [ns-sym linters opts expected-lint-result]
  (let [v (->> (lint-ns-noprint ns-sym linters opts)
               (map normalize-warning)
               (map warning-replace-auto-numbered-symbol-names)
               frequencies)]
    (is (= expected-lint-result
           v)
        (with-out-str
          (-> expected-lint-result
              (lambdaisland.deep-diff/diff v)
              lambdaisland.deep-diff/pretty-print)))))

(defn fname-from-parts [& parts]
  (str/join File/separator parts))

(deftest test1
  (lint-test
   'testcases.f01
   [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :wrong-arity :local-shadows-var :wrong-tag :non-dynamic-earmuffs
    :unused-locals]
   (assoc default-test-opts
          :config-files
          [(fname-from-parts "cases" "testcases" "eastwood-testing-config.clj")])
   {{:linter :redefd-vars,
     :msg
     (str "Var i-am-redefd def'd 2 times at line:col locations: "
          (fname-from-parts "testcases" "f01.clj") ":4:6 "
          (fname-from-parts "testcases" "f01.clj") ":5:6."),
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 5, :column 6}
    1,
    {:linter :misplaced-docstrings,
     :msg "Possibly misplaced docstring, foo2.",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 12, :column 7}
    1,
    {:linter :misplaced-docstrings,
     :msg "Possibly misplaced docstring, foo5.",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 31, :column 7}
    1,
    {:linter :redefd-vars,
     :msg (str "Var test1-redefd def'd 3 times at line:col locations: "
               (fname-from-parts "testcases" "f01.clj") ":42:10 "
               (fname-from-parts "testcases" "f01.clj") ":46:10 "
               (fname-from-parts "testcases" "f01.clj") ":49:10."),
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 46, :column 10}
    1,
    {:linter :redefd-vars,
     :msg (str "Var i-am-redefd2 def'd 2 times at line:col locations: "
               (fname-from-parts "testcases" "f01.clj") ":70:6 "
               (fname-from-parts "testcases" "f01.clj") ":73:8."),
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 73, :column 8}
    1,
    {:linter :def-in-def,
     :msg "There is a def of def-in-def1 nested inside def bar.",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 82, :column 10}
    1,
    {:linter :non-dynamic-earmuffs, :msg "#'testcases.f01/var3 should use the earmuff naming convention: please use #'testcases.f01/*var3* instead.", :file "testcases/f01.clj", :line 154, :column 1}
    1
    {:linter :wrong-arity,
     :msg "Function on var #'clojure.core/assoc called with 1 args, but it is only known to take one of the following args: [map key val]  [map key val & kvs].",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 91, :column 4}
    1,
    {:linter :wrong-arity,
     :msg "Function on var #'clojure.core/assoc called with 0 args, but it is only known to take one of the following args: [map key val]  [map key val & kvs].",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 94, :column 4}
    1,
    {:linter :wrong-arity,
     :msg "Function on local f1 called with 0 args, but it is only known to take one of the following args: [x].",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 98, :column 6}
    1,
    {:linter :wrong-arity,
     :msg "Function on local f1 called with 0 args, but it is only known to take one of the following args: [p1__<num># p2__<num>#].",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 102, :column 6}
    1,
    {:linter :wrong-arity,
     :msg "Function on var #'testcases.f01/fn-with-arglists-meta called with 2 args, but it is only known to take one of the following args: [x y z].",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 133, :column 4}
    1,
    {:linter :wrong-arity,
     :msg "Function on if no-name called with 3 args, but it is only known to take one of the following args: [coll x].",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 137, :column 4}
    1,
    {:linter :wrong-arity,
     :msg "Function on do no-name called with 3 args, but it is only known to take one of the following args: [coll x].",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 144, :column 4}
    1,
    {:linter :non-dynamic-earmuffs,
     :msg "#'testcases.f01/*var2* should be marked dynamic.",
     :file (fname-from-parts "testcases" "f01.clj"),
     :line 152, :column 1}
    1}))

;; TBD: I do not know why the i-am-inner-defonce-sym warning appears
;; twice in the result. Once would be enough.

(deftest test2
  (lint-test
   'testcases.f02
   [:misplaced-docstrings :def-in-def :redefd-vars :wrong-arity
    :local-shadows-var :wrong-tag :unused-locals]
   eastwood.lint/default-opts
   {{:linter :redefd-vars,
     :msg (str "Var i-am-defonced-and-defmultid def'd 2 times at line:col locations: "
               (fname-from-parts "testcases" "f02.clj") ":8:10 "
               (fname-from-parts "testcases" "f02.clj") ":10:11."),
     :file (fname-from-parts "testcases" "f02.clj"),
     :line 10, :column 11}
    1,
    {:linter :redefd-vars,
     :msg (str "Var i-am-a-redefd-defmulti def'd 2 times at line:col locations: "
               (fname-from-parts "testcases" "f02.clj") ":16:11 "
               (fname-from-parts "testcases" "f02.clj") ":18:11."),
     :file (fname-from-parts "testcases" "f02.clj"),
     :line 18, :column 11}
    1,
    {:linter :def-in-def,
     :msg "There is a def of i-am-inner-defonce-sym nested inside def i-am-outer-defonce-sym.",
     :file (fname-from-parts "testcases" "f02.clj"),
     :line 20, :column 42}
    2}))

(deftest test3
  (lint-test
   'testcases.f03
   [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :unused-namespaces :unused-ret-vals :unused-ret-vals-in-try :wrong-arity
    :wrong-tag :unused-locals]
   eastwood.lint/default-opts
   {}))

(deftest test4
  (lint-test
   'testcases.f04
   [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :wrong-arity :local-shadows-var :wrong-tag :unused-locals]
   eastwood.lint/default-opts
   {{:linter :local-shadows-var,
     :msg "local: replace invoked as function shadows var: #'clojure.core/replace.",
     :file (fname-from-parts "testcases" "f04.clj"),
     :line 44, :column 14}
    1,
    {:linter :local-shadows-var,
     :msg "local: shuffle invoked as function shadows var: #'clojure.core/shuffle.",
     :file (fname-from-parts "testcases" "f04.clj"),
     :line 47, :column 14}
    1,
    {:linter :local-shadows-var,
     :msg "local: count invoked as function shadows var: #'clojure.core/count.",
     :file (fname-from-parts "testcases" "f04.clj"),
     :line 64, :column 20}
    1,
    {:linter :local-shadows-var,
     :msg "local: dogs invoked as function shadows var: #'testcases.f04/dogs.",
     :file (fname-from-parts "testcases" "f04.clj"),
     :line 90, :column 13}
    1}))

;; The following test is known to fail with Clojure 1.5.1 because of
;; protocol method names that begin with "-". See
;; http://dev.clojure.org/jira/browse/TANAL-17 and
;; http://dev.clojure.org/jira/browse/CLJ-1202

(when (util/clojure-1-6-or-later)
  (deftest test5
    (lint-test
     'testcases.f05
     [:misplaced-docstrings :def-in-def :redefd-vars :deprecations
      :wrong-arity :local-shadows-var :wrong-tag :unused-locals]
     eastwood.lint/default-opts
     {})))

(deftest test6
  (lint-test
   'testcases.f06
   [:unused-fn-args :misplaced-docstrings :def-in-def :redefd-vars :deprecations
    :wrong-arity :local-shadows-var :wrong-tag :unused-locals]
   eastwood.lint/default-opts
   {{:linter :unused-fn-args,
     :msg "Function arg y never used.",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 5, :column 30}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg y never used.",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 9, :column 31}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg w never used.",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 20, :column 20}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg body never used.",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 33, :column 23}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg z never used.",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 50, :column 33}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg coll never used.",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 63, :column 6}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg f never used.",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 64, :column 11}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg f never used.",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 68, :column 11}
    1,
    {:linter :unused-fn-args,
     :msg "Function arg val never used.",
     :file (fname-from-parts "testcases" "f06.clj"),
     :line 69, :column 13}
    1}))

(deftest test7
  (let [common-expected-warnings
        {{:line 10, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals-in-try,
          :msg "Pure static method call return value is discarded inside body of try: (. clojure.lang.Numbers (add 5 7))."}
         1,
         {:line 27, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Constant value is discarded: 7."}
         1,
         {:line 27, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Constant value is discarded: :a."}
         1,
         {:line 24, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add (. clojure.lang.Numbers (add i n)) i)) n)) i))."}
         1,
         {:line 25, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (multiplyP n i))."}
         1,
         {:line 26, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (clojure.core/quotient i n))."}
         1,
         {:line 27, :column 29,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Lazy function call return value is discarded: (map inc [1 2 3])."}
         1,
         {:line 27, :column 9,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (dissoc {:b 2} :b)."}
         1,
         {:line 28, :column 6,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (assoc {} a n)."}
         1,
         {:line 28, :column 21,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i a))."}
         1,
         {:line 32, :column 3,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (assoc {} x y)."}
         1,
         {:line 33, :column 3,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (apply + [1 2 3])."}
         1,
         {:line 34, :column 3,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Lazy function call return value is discarded: (filter print [1 2 3])."}
         1,
         {:line 36, :column 3,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (minus (. clojure.lang.Numbers (minus x y)) x))."}
         1,
         {:line 60, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (dec i))."}
         2,
         {:line 65, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (assoc {} i i)."}
         2,
         {:line 81, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Constant value is discarded: :a."}
         1,
         {:line 81, :column 9,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (dissoc {:b 2} :b)."}
         1,
         {:line 82, :column 6,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (assoc {} a n)."}
         1,
         {:line 82, :column 21,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i a))."}
         1,
         {:line 100, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (assoc {} i n)."}
         1,
         {:line 100, :column 28,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (add 3 n))."}
         1,
         {:line 100, :column 20,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure static method call return value is discarded: (. clojure.lang.Numbers (divide i n))."}
         1,
         {:line 107, :column 3,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (assoc {} k v)."}
         1,
         {:line 112, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Var value is discarded: repeatedly."}
         1,
         {:line 112, :column 16,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Local value is discarded: n."}
         1,
         {:line 112, :column 18,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Local value is discarded: x."}
         1,
         {:line 125, :column 7,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Var value is discarded: gah."}
         1}

        clojure-1-5-additional-expected-warnings
        {{:line 140, :column 3,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (false? a)."}
         1}

        ;; Clojure 1.5 does not have clojure.core/some? so it does not
        ;; warn about calling that function when its return value is
        ;; unused. Clojure 1.6 and later should.
        clojure-1-6-or-later-additional-expected-warnings
        {{:line 140, :column 3,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (some? a)."}
         1}

        clojure-1-8-or-later-additional-expected-warnings
        {{:line 162, :column 5,
          :file (fname-from-parts "testcases" "f07.clj"),
          :linter :unused-ret-vals,
          :msg "Pure function call return value is discarded: (str/includes? \"food\" \"oo\")."}
         1}]
    (lint-test
     'testcases.f07
     [:unused-ret-vals :unused-ret-vals-in-try :deprecations :wrong-arity
      :local-shadows-var :wrong-tag :unused-locals]
     eastwood.lint/default-opts
     (merge common-expected-warnings
            (if (util/clojure-1-6-or-later)
              clojure-1-6-or-later-additional-expected-warnings
              clojure-1-5-additional-expected-warnings)
            (when (util/clojure-1-8-or-later)
              clojure-1-8-or-later-additional-expected-warnings)))))

(deftest test8
  (lint-test
   'testcases.deprecated
   [:deprecations :wrong-arity :local-shadows-var :wrong-tag :unused-locals]
   eastwood.lint/default-opts
   {{:linter :deprecations,
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
    1}))

(deftest test9
  (lint-test
   'testcases.tanal-9
   [:misplaced-docstrings :def-in-def :redefd-vars :unused-fn-args
    :unused-ret-vals :unused-ret-vals-in-try :deprecations :wrong-arity
    :local-shadows-var :wrong-tag :unused-locals]
   eastwood.lint/default-opts
   {}))

(deftest test10
  (lint-test
   'testcases.tanal-27
   [:misplaced-docstrings :def-in-def :redefd-vars :unused-fn-args
    :unused-ret-vals :unused-ret-vals-in-try :deprecations :wrong-arity
    :local-shadows-var :wrong-tag :unused-locals]
   eastwood.lint/default-opts
   {}))

(deftest test11
  (lint-test
   'testcases.keyword-typos
   [:keyword-typos :unused-ret-vals :unused-ret-vals-in-try
    :deprecations :wrong-arity :local-shadows-var :wrong-tag :unused-locals]
   eastwood.lint/default-opts
   {{:linter :keyword-typos,
     :msg "Possible keyword typo: :occuption instead of :occupation ?"}
    1}))

(deftest test12
  (lint-test
   'testcases.isformsok
   [:suspicious-test :suspicious-expression :local-shadows-var :wrong-tag
    :unused-locals]
   eastwood.lint/default-opts
   {}))

(deftest test13
  (lint-test
   'testcases.testtest
   [:keyword-typos :suspicious-test :suspicious-expression
    :local-shadows-var :wrong-tag]
   eastwood.lint/default-opts
   {{:linter :suspicious-test,
     :msg "'is' form has string as first arg. This will always pass. If you meant to have a message arg to 'is', it should be the second arg, after the expression to test.",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 11, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has string as first arg. This will always pass. If you meant to have a message arg to 'is', it should be the second arg, after the expression to test.",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 13, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has first arg that is a constant whose value is logical true. This will always pass. There is probably a mistake in this test.",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 17, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has a string inside (thrown? ...). This string is ignored. Did you mean it to be a message shown if the test fails, like (is (thrown? ...) \"message\")?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 61, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a string. This string is ignored. Did you mean to use thrown-with-msg? instead of thrown?, and a regex instead of the string?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 63, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex. This regex is ignored. Did you mean to use thrown-with-msg? instead of thrown?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 65, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex. This regex is ignored. Did you mean to use thrown-with-msg? instead of thrown?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 69, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "(is (thrown? ...)) form has second thrown? arg that is a regex. This regex is ignored. Did you mean to use thrown-with-msg? instead of thrown?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 71, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "Found (= ...) form inside deftest. Did you forget to wrap it in 'is', e.g. (is (= ...))?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 73, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "Found (= ...) form inside testing. Did you forget to wrap it in 'is', e.g. (is (= ...))?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 77, :column 6}
    1,
    {:linter :suspicious-test,
     :msg "Found (contains? ...) form inside deftest. Did you forget to wrap it in 'is', e.g. (is (contains? ...))?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 80, :column 4}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has non-string as second arg (inferred type is class java.lang.Long). The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception. If the second arg is an expression that evaluates to a message string during test time, and you intended this, you may wrap it in a call to (str ...) so this warning goes away.",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 82, :column 4}
    1,
    {:linter :suspicious-expression,
     :msg "= called with 1 args. (= x) always returns true. Perhaps there are misplaced parentheses?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 82, :column 7}
    1,
    {:linter :suspicious-test,
     :msg "'is' form has non-string as second arg (inferred type is class java.lang.Long). The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception. If the second arg is an expression that evaluates to a message string during test time, and you intended this, you may wrap it in a call to (str ...) so this warning goes away.",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 83, :column 4}
    1,
    {:linter :suspicious-expression,
     :msg "> called with 1 args. (> x) always returns true. Perhaps there are misplaced parentheses?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 83, :column 7}
    1,
    {:linter :suspicious-expression,
     :msg "min-key called with 2 args. (min-key f x) always returns x. Perhaps there are misplaced parentheses?",
     :file (fname-from-parts "testcases" "testtest.clj"),
     :line 84, :column 10}
    1,
    {:line 86, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "= called with 1 args. (= x) always returns true. Perhaps there are misplaced parentheses?"}
    1,
    {:line 87, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "== called with 1 args. (== x) always returns true. Perhaps there are misplaced parentheses?"}
    1,
    {:line 88, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "not= called with 1 args. (not= x) always returns false. Perhaps there are misplaced parentheses?"}
    1,
    {:line 89, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "< called with 1 args. (< x) always returns true. Perhaps there are misplaced parentheses?"}
    1,
    {:line 90, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "<= called with 1 args. (<= x) always returns true. Perhaps there are misplaced parentheses?"}
    1,
    {:line 91, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "> called with 1 args. (> x) always returns true. Perhaps there are misplaced parentheses?"}
    1,
    {:line 92, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg ">= called with 1 args. (>= x) always returns true. Perhaps there are misplaced parentheses?"}
    1,
    {:line 93, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "min called with 1 args. (min x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 94, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "max called with 1 args. (max x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 95, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "min-key called with 2 args. (min-key f x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 96, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "max-key called with 2 args. (max-key f x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 97, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "+ called with 0 args. (+) always returns 0. Perhaps there are misplaced parentheses?"}
    1,
    {:line 98, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "+ called with 1 args. (+ x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 99, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "+' called with 0 args. (+') always returns 0. Perhaps there are misplaced parentheses?"}
    1,
    {:line 100, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "+' called with 1 args. (+' x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 101, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "* called with 0 args. (*) always returns 1. Perhaps there are misplaced parentheses?"}
    1,
    {:line 102, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "* called with 1 args. (* x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 103, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "*' called with 0 args. (*') always returns 1. Perhaps there are misplaced parentheses?"}
    1,
    {:line 104, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "*' called with 1 args. (*' x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 105, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "dissoc called with 1 args. (dissoc map) always returns map. Perhaps there are misplaced parentheses?"}
    1,
    {:line 106, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "disj called with 1 args. (disj set) always returns set. Perhaps there are misplaced parentheses?"}
    1,
    {:line 107, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "merge called with 0 args. (merge) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 108, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "merge called with 1 args. (merge map) always returns map. Perhaps there are misplaced parentheses?"}
    1,
    {:line 109, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "merge-with called with 1 args. (merge-with f) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 110, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "merge-with called with 2 args. (merge-with f map) always returns map. Perhaps there are misplaced parentheses?"}
    1,
    {:line 113, :column 18,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "interleave called with 0 args. (interleave) always returns (). Perhaps there are misplaced parentheses?"}
    1,
    {:line 114, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "pr-str called with 0 args. (pr-str) always returns \"\". Perhaps there are misplaced parentheses?"}
    1,
    {:line 115, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "print-str called with 0 args. (print-str) always returns \"\". Perhaps there are misplaced parentheses?"}
    1,
    {:line 116, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "with-out-str called with 0 args. (with-out-str) always returns \"\". Perhaps there are misplaced parentheses?"}
    1,
    {:line 117, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "pr called with 0 args. (pr) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 118, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "print called with 0 args. (print) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 119, :column 19,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "comp called with 0 args. (comp) always returns identity. Perhaps there are misplaced parentheses?"}
    1,
    {:line 120, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "partial called with 1 args. (partial f) always returns f. Perhaps there are misplaced parentheses?"}
    1,
    {:line 121, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "lazy-cat called with 0 args. (lazy-cat) always returns (). Perhaps there are misplaced parentheses?"}
    1,
    {:line 122, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "-> called with 1 args. (-> x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 123, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "cond called with 0 args. (cond) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 124, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "case called with 2 args. (case x y) always returns y. Perhaps there are misplaced parentheses?"}
    1,
    {:line 125, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "condp called with 3 args. (condp pred test-expr expr) always returns expr. Perhaps there are misplaced parentheses?"}
    1,
    {:line 126, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "when called with 1 args. (when test) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 127, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "when-not called with 1 args. (when-not test) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 128, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "when-let called with 1 args. (when-let [x y]) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 129, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "doseq called with 1 args. (doseq [x coll]) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 130, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "dotimes called with 1 args. (dotimes [i n]) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 131, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "and called with 0 args. (and) always returns true. Perhaps there are misplaced parentheses?"}
    1,
    {:line 132, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "and called with 1 args. (and x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 133, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "or called with 0 args. (or) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 134, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "or called with 1 args. (or x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 135, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "doto called with 1 args. (doto x) always returns x. Perhaps there are misplaced parentheses?"}
    1,
    {:line 136, :column 16,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "declare called with 0 args. (declare) always returns nil. Perhaps there are misplaced parentheses?"}
    1,
    {:line 145, :column 7,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "= called with 1 args. (= x) always returns true. Perhaps there are misplaced parentheses?"}
    1,
    {:line 146, :column 4,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-test,
     :msg "'is' form has non-string as second arg (inferred type is class java.lang.Long). The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception. If the second arg is an expression that evaluates to a message string during test time, and you intended this, you may wrap it in a call to (str ...) so this warning goes away."}
    1,
    {:line 146, :column 7,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "== called with 1 args. (== x) always returns true. Perhaps there are misplaced parentheses?"}
    1,
    {:line 156, :column 7,
     :file (fname-from-parts "testcases" "testtest.clj"),
     :linter :suspicious-expression,
     :msg "= called with 1 args. (= x) always returns true. Perhaps there are misplaced parentheses?"}
    1}))

(deftest test14
  (let [common-expected-warnings
        {{:linter :suspicious-expression,
          :msg "doto called with 1 args. (doto x) always returns x. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 23, :column 5}
         1,
         {:linter :suspicious-expression,
          :msg
          "-> called with 1 args. (-> x) always returns x. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 25, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "and called with 0 args. (and) always returns true. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 27, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "and called with 1 args. (and x) always returns x. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 28, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "as-> called with 2 args. (as-> expr name) always returns expr. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 29, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "case called with 2 args. (case x y) always returns y. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 30, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "cond called with 0 args. (cond) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 31, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "cond-> called with 1 args. (cond-> x) always returns x. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 32, :column 12}
         1,
         {:linter :suspicious-expression,
          :msg
          "cond->> called with 1 args. (cond->> x) always returns x. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 33, :column 12}
         1,
         {:linter :suspicious-expression,
          :msg
          "condp called with 3 args. (condp pred test-expr expr) always returns expr. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 34, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "declare called with 0 args. (declare) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 35, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "delay called with 0 args. (delay) always returns (delay nil). Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 36, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "doseq called with 1 args. (doseq [x coll]) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 37, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "dotimes called with 1 args. (dotimes [i n]) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 38, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "doto called with 1 args. (doto x) always returns x. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 39, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "import called with 0 args. (import) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 40, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "lazy-cat called with 0 args. (lazy-cat) always returns (). Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 41, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "let called with 1 args. (let bindings) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 42, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "letfn called with 1 args. (letfn bindings) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 43, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "locking called with 1 args. (locking x) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 44, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "loop called with 1 args. (loop bindings) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 45, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "or called with 0 args. (or) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 46, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "or called with 1 args. (or x) always returns x. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 47, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "pvalues called with 0 args. (pvalues) always returns (). Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 48, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "some-> called with 1 args. (some-> expr) always returns expr. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 49, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "some->> called with 1 args. (some->> expr) always returns expr. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 50, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "when called with 1 args. (when test) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 51, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "when-first called with 1 args. (when-first [x y]) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 52, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "when-let called with 1 args. (when-let [x y]) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 53, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "when-not called with 1 args. (when-not test) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 54, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "with-bindings called with 1 args. (with-bindings map) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 56, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "with-in-str called with 1 args. (with-in-str s) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 57, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "with-local-vars called with 1 args. (with-local-vars bindings) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 58, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "with-open called with 1 args. (with-open bindings) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 59, :column 1}
         2,
         {:linter :suspicious-expression,
          :msg
          "with-out-str called with 0 args. (with-out-str) always returns \"\". Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 60, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "with-precision called with 1 args. (with-precision precision) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 61, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "with-redefs called with 1 args. (with-redefs bindings) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 62, :column 1}
         1}
        clojure-1-6-or-later-additional-expected-warnings
        {{:linter :suspicious-expression,
          :msg
          "->> called with 1 args. (->> x) always returns x. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 26, :column 1}
         1,
         {:linter :suspicious-expression,
          :msg
          "when-some called with 1 args. (when-some [x y]) always returns nil. Perhaps there are misplaced parentheses?",
          :file (fname-from-parts "testcases" "suspicious.clj"),
          :line 55, :column 1}
         1}
        expected-warnings
        (merge common-expected-warnings
               (if (util/clojure-1-6-or-later)
                 clojure-1-6-or-later-additional-expected-warnings
                 nil))]
    (lint-test
     'testcases.suspicious
     [:suspicious-test :suspicious-expression :local-shadows-var :wrong-tag]
     eastwood.lint/default-opts
     expected-warnings)))

;; It is strange that the :unlimited-use linter has nil for :line
;; and :column here, but integer values when I use it from the
;; command line. What is going on here?

(deftest test15
  (lint-test
   'testcases.unlimiteduse
   [:unlimited-use :local-shadows-var :wrong-tag :unused-locals]
   eastwood.lint/default-opts
   {{:linter :unlimited-use,
     :msg "Unlimited use of ([clojure.reflect] [clojure inspector] [clojure [set]] [clojure.java.io :as io]) in testcases.unlimiteduse.",
     :file (fname-from-parts "testcases" "unlimiteduse.clj"),
     :line 6, :column 10}
    1,
    {:linter :unlimited-use,
     :msg "Unlimited use of ((clojure [pprint :as pp] [uuid :as u])) in testcases.unlimiteduse.",
     :file (fname-from-parts "testcases" "unlimiteduse.clj"),
     :line 14, :column 10}
    1}))

(deftest test16
  (lint-test
   'testcases.in-ns-switching
   [:unlimited-use :local-shadows-var :wrong-tag :unused-locals]
   eastwood.lint/default-opts
   {{:linter :unlimited-use,
     :msg "Unlimited use of (clojure.set [testcases.f01 :as t1]) in testcases.in-ns-switching.",
     :file (fname-from-parts "testcases" "in_ns_switching.clj"),
     :line 4, :column 9}
    1}))

(deftest test17
  (let [common-expected-warnings
        {{:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lv1.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 7, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lv2.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 8, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: iv1.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 19, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: iv2.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 20, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lf1.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 31, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lf2.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 32, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lf3.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 33, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$long@<somehex> in def of Var: lf4.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 34, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: if1.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 35, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: if2.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 36, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: if3.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 37, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: clojure.core$int@<somehex> in def of Var: if4.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 38, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Tag: (quote LinkedList) for return type of function method: ([coll] (java.util.LinkedList. coll)) should be Java class name (fully qualified if not in java.lang package).",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 84, :column 12}
         1,
         {:linter :wrong-tag,
          :msg "Tag: (quote LinkedList) for return type of function method: ([coll] (java.util.LinkedList. coll)) should be Java class name (fully qualified if not in java.lang package).",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 85, :column 1}
         1,
         {:linter :wrong-tag,
          :msg "Wrong tag: Set on form: b.",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 125, :column 14}
         1}

        ;; These warnings are not needed after CLJ-1232 was fixed in
        ;; Clojure 1.8.0.

        clojure-1-7-or-earlier-expected-warnings
        {{:linter :wrong-tag,
          :msg "Tag: LinkedList for return type of function on arg vector: [coll] should be fully qualified Java class name, or else it may cause exception if used from another namespace. This is only an issue for Clojure 1.7 and earlier. Clojure 1.8 fixes it (CLJ-1232 https://dev.clojure.org/jira/browse/CLJ-1232).",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 87, :column 28}
         1,
         {:linter :wrong-tag,
          :msg "Tag: LinkedList for return type of function on arg vector: [coll] should be fully qualified Java class name, or else it may cause exception if used from another namespace. This is only an issue for Clojure 1.7 and earlier. Clojure 1.8 fixes it (CLJ-1232 https://dev.clojure.org/jira/browse/CLJ-1232).",
          :file (fname-from-parts "testcases" "wrongtag.clj"),
          :line 88, :column 25}
         1}

        clojure-1-6-or-earlier-expected-warnings
        (merge common-expected-warnings
               clojure-1-7-or-earlier-expected-warnings)

        clojure-1-7-only-expected-warnings
        (merge common-expected-warnings
               clojure-1-7-or-earlier-expected-warnings
               {{:linter :wrong-tag,
                 :msg "Tag: LinkedList for return type of function on arg vector: [& p__<num>] should be fully qualified Java class name, or else it may cause exception if used from another namespace. This is only an issue for Clojure 1.7 and earlier. Clojure 1.8 fixes it (CLJ-1232 https://dev.clojure.org/jira/browse/CLJ-1232).",
                 :file (fname-from-parts "testcases" "wrongtag.clj"),
                 :line 93, :column 26}
                1})]
    (lint-test
     'testcases.wrongtag
     (concat @#'eastwood.lint/default-linters [:unused-locals])
     eastwood.lint/default-opts
     (cond (util/clojure-1-8-or-later) common-expected-warnings

           ;; This is actually the expected result only for 1.7.0-alpha2
           ;; or later, because the behavior changed with the fix for
           ;; CLJ-887, so it will fail if you run the test with
           ;; 1.7.0-alpha1. I won't bother checking the version that
           ;; precisely, though.
           (util/clojure-1-7-or-later) clojure-1-7-only-expected-warnings

           :else clojure-1-6-or-earlier-expected-warnings))))

(deftest test18
  (lint-test
   'testcases.macrometa
   [:unlimited-use :local-shadows-var :wrong-tag :unused-meta-on-macro
    :unused-locals]
   eastwood.lint/default-opts
   {{:linter :unused-meta-on-macro,
     :msg "Java constructor call 'StringWriter.' has metadata with keys (:foo). All metadata is eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 20, :column 28}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java constructor call 'StringWriter.' has metadata with keys (:tag). All metadata is eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 21, :column 28}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java instance method call '.close' has metadata with keys (:foo). All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 35, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java instance method call '.close' has metadata with keys (:foo). All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 37, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java static method call 'Math/abs' has metadata with keys (:foo). All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 44, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java static method call 'Math/abs' has metadata with keys (:foo). All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
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
     :msg "Java static field access 'Long/MAX_VALUE' has metadata with keys (:foo). All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 92, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java static field access 'Long/MAX_VALUE' has metadata with keys (:foo). All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 94, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java instance method/field access '.x' has metadata with keys (:foo). All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 102, :column 20}
    1,
    {:linter :unused-meta-on-macro,
     :msg "Java instance method/field access '.x' has metadata with keys (:foo). All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure.",
     :file (fname-from-parts "testcases" "macrometa.clj"),
     :line 104, :column 20}
    1}))

(deftest test19
  (let [common-expected-warnings
        {{:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: false in form (if false 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 13, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: [nil] in form (if [nil] 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 14, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: #{} in form (if #{} 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 15, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: {:a 1} in form (if {:a 1} 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 16, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (quote ()) in form (if (quote ()) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 17, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (quote (\"string\")) in form (if (quote (\"string\")) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 18, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: [(inc 41)] in form (if [(inc 41)] 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 19, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: #{(dec 43)} in form (if #{(dec 43)} 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 20, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: {:a (/ 84 2)} in form (if {:a (/ 84 2)} 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 21, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (seq {:a 1}) in form (if (seq {:a 1}) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 22, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (clojure.core/not (quote x)) in form (if (clojure.core/not (quote x)) \"y\" \"z\").",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 24, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (clojure.core/not false) in form (if (clojure.core/not false) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 25, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (clojure.core/not [nil]) in form (if (clojure.core/not [nil]) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 26, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (clojure.core/not #{}) in form (if (clojure.core/not #{}) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 27, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (clojure.core/not {:a 1}) in form (if (clojure.core/not {:a 1}) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 28, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (clojure.core/not (quote ())) in form (if (clojure.core/not (quote ())) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 29, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (clojure.core/not (quote (\"string\"))) in form (if (clojure.core/not (quote (\"string\"))) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 30, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: [(inc 41)] in form (if (clojure.core/not [(inc 41)]) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 31, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: #{(dec 43)} in form (if (clojure.core/not #{(dec 43)}) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 32, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: {:a (/ 84 2)} in form (if (clojure.core/not {:a (/ 84 2)}) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 33, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (clojure.core/not (seq {:a 1})) in form (if (clojure.core/not (seq {:a 1})) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 34, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: nil in form (if nil (do (quote tom) :cat)).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 36, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: [nil] in form (if [nil] nil (do 1)).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 37, :column 1},
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: :x in form (if :x 8 (clojure.core/cond :else 9)).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 50, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: false in form (if false (assert false \"This won't be reached, but shouldn't warn about it whether it can be reached or not.\")).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 64, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: [false] in form (if temp__<num>__auto__ (clojure.core/let [x temp__<num>__auto__] \"w\") \"v\").",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 67, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (sorted-set 5 7) in form (if temp__<num>__auto__ (do (clojure.core/let [x temp__<num>__auto__] (println \"Hello\")))).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 68, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (clojure.core/seq [1 2]) in form (if temp__<num>__auto__ (do (clojure.core/let [xs__<num>__auto__ temp__<num>__auto__] (clojure.core/let [x (clojure.core/first xs__<num>__auto__)] (println \"Goodbye\"))))).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 69, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: 7 in form (if and__<num>__auto__ (clojure.core/and (inc 2)) and__<num>__auto__).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 71, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: false in form (if or__<num>__auto__ or__<num>__auto__ (clojure.core/or 2)).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 72, :column 1}
         1,
         {:linter :constant-test,
          :msg
          "Test expression is always logical true or always logical false: {:a 1} in form (if (clojure.core/nil? temp__<num>__auto__) \"v\" (clojure.core/let [x temp__<num>__auto__] \"w\")).",
          :file "testcases/constanttestexpr.clj",
          :line 74,
          :column 1}
         1,
         {:linter :constant-test,
          :msg
          "Test expression is always logical true or always logical false: \"w\" in form (if (clojure.core/nil? temp__<num>__auto__) nil (clojure.core/let [x temp__<num>__auto__] nil)).",
          :file "testcases/constanttestexpr.clj",
          :line 75,
          :column 1}
         1
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: true in form (if true nil (do (throw (new java.lang.AssertionError (clojure.core/str \"Assert failed: \" \"string\" \"\\n\" (clojure.core/pr-str (quote true))))))).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 84, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: [false] in form (if [false] nil (do (throw (new java.lang.AssertionError (clojure.core/str \"Assert failed: \" (clojure.core/pr-str (quote [false]))))))).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 85, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: 0 in form (if 0 nil (do (throw (new java.lang.AssertionError (clojure.core/str \"Assert failed: \" (clojure.core/pr-str (quote 0))))))).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 111, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: 32 in form (if 32 nil (do (throw (new java.lang.AssertionError (clojure.core/str \"Assert failed: \" (clojure.core/pr-str (quote 32))))))).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 111, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (clojure.core/string? format-in__<num>__auto__) in form (if (clojure.core/string? format-in__<num>__auto__) ((var clojure.pprint/cached-compile) format-in__<num>__auto__) format-in__<num>__auto__).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 122, :column 4}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (map? (list [:p \"a\"] [:p \"b\"])) in form (if (map? (list [:p \"a\"] [:p \"b\"])) 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 127, :column 1}
         1,
         {:linter :constant-test,
          :msg "Test expression is always logical true or always logical false: (map? (list [:p \"a\"] [:p \"b\"])) in form (if x 1 2).",
          :file (fname-from-parts "testcases" "constanttestexpr.clj"),
          :line 131, :column 3}
         1,
         {:linter :unused-locals,
          :msg "let bound symbol 'x' never used.",
          :file "testcases/constanttestexpr.clj",
          :line 67,
          :column 10}
         1,
         {:linter :unused-locals,
          :msg "let bound symbol 'x' never used.",
          :file "testcases/constanttestexpr.clj",
          :line 68,
          :column 12}
         1,
         {:linter :unused-locals,
          :msg "let bound symbol 'x' never used.",
          :file "testcases/constanttestexpr.clj",
          :line 69,
          :column 14}
         1,
         {:linter :unused-locals,
          :msg "let bound symbol 'x' never used.",
          :file "testcases/constanttestexpr.clj",
          :line 74,
          :column 55}
         1,
         {:linter :unused-locals,
          :msg "let bound symbol 'x' never used.",
          :file "testcases/constanttestexpr.clj",
          :line 75,
          :column 59}
         1}
        clojure-1-6-or-later-additional-expected-warnings
        {}
        expected-warnings
        (merge common-expected-warnings
               (if (util/clojure-1-6-or-later)
                 clojure-1-6-or-later-additional-expected-warnings
                 nil))]
    (lint-test
     'testcases.constanttestexpr
     [:constant-test :unused-locals]
     eastwood.lint/default-opts
     expected-warnings)))

(deftest test20
  (lint-test
   'testcases.unusedlocals
   [:unused-locals :unused-private-vars]
   eastwood.lint/default-opts
   {{:linter :unused-locals,
     :msg "let bound symbol 'unused-first-should-warn' never used.",
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 68, :column 11}
    1,
    {:linter :unused-locals,
     :msg "loop bound symbol 'unused-loop-symbol' never used.",
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 80, :column 3}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'foo' never used."
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 112, :column 17}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'baz' never used."
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 112, :column 25}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'guh' never used."
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 112, :column 29}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'unused1' never used."
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 113, :column 11}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'unused2' never used."
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 113, :column 22}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'unused3' never used."
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 113, :column 40}
    1,
    {:linter :unused-private-vars,
     :msg "Private var 'upper-limit3' is never used."
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 139, :column 1}
    1,
    {:linter :unused-private-vars,
     :msg "Private var 'lower-limit3' is never used."
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 140, :column 1}
    1,
    {:linter :unused-private-vars,
     :msg "Private var 'foo10' is never used."
     :file (fname-from-parts "testcases" "unusedlocals.clj"),
     :line 164, :column 1}
    1}))

;; No faults expected:
(deftest test21
  (lint-test 'testcases.unusednsimport.consumer1 [:unused-namespaces] eastwood.lint/default-opts {}))

;; Fault expected (since the refered type is in a `comment` form):
(deftest test22
  (lint-test 'testcases.unusednsimport.consumer2
             [:unused-namespaces]
             eastwood.lint/default-opts
             {{:linter :unused-namespaces
               :msg "Namespace testcases.unusednsimport.defrecord is never used in testcases.unusednsimport.consumer2."
               :file "testcases/unusednsimport/consumer2.clj"
               :line 1
               :column 1} 1}))

;; No faults expected:
(deftest test23
  (lint-test 'testcases.unusednsimport.consumer3 [:unused-namespaces] eastwood.lint/default-opts {}))

;; No faults expected:
(deftest test24
  (lint-test 'testcases.unusednsimport.consumer3 [:unused-namespaces] eastwood.lint/default-opts {}))

(deftest test25
  (lint-test 'testcases.unusednsimport.consumer4 [:unused-namespaces] eastwood.lint/default-opts {}))

;; No faults expected:
(deftest test26
  (lint-test 'testcases.unusednsimport.consumer5 [:unused-namespaces] eastwood.lint/default-opts {}))

(deftest test27
  (lint-test
   'testcases.unusednss
   [:unused-namespaces :unused-locals]
   eastwood.lint/default-opts
   {{:linter :unused-namespaces,
     :msg "Namespace clojure.string is never used in testcases.unusednss.",
     :file (fname-from-parts "testcases" "unusednss.clj"),
     :line 1, :column 1}
    1}))

(deftest test28
  (lint-test
   'testcases.unusednss3
   [:unused-namespaces :unused-locals]
   eastwood.lint/default-opts
   {}))

(deftest test29
  (lint-test
   'testcases.unusednss4
   [:unused-namespaces :unused-locals]
   eastwood.lint/default-opts
   {{:linter :unused-namespaces,
     :msg "Namespace testcases.unusednss2 is never used in testcases.unusednss4.",
     :file (fname-from-parts "testcases" "unusednss4.clj"),
     :line 1, :column 1}
    1}))

(deftest test30
  (lint-test
   'testcases.wrongnsform
   [:wrong-ns-form :unused-locals]
   eastwood.lint/default-opts
   {{:linter :wrong-ns-form,
     :msg "ns reference starts with ':println' - should be one one of the keywords: :gen-class :import :load :refer-clojure :require :use.",
     :file (fname-from-parts "testcases" "wrongnsform.clj"),
     :line 5, :column 3}
    1,
    {:linter :wrong-ns-form,
     :msg "ns references should be lists. This is not: [:use clojure.test].",
     :file (fname-from-parts "testcases" "wrongnsform.clj"),
     :line 10, :column 3}
    1,
    {:linter :wrong-ns-form,
     :msg "More than one ns form found in same file.",
     :file (fname-from-parts "testcases" "wrongnsform.clj"),
     :line 19, :column 1}
    1,
    {:linter :wrong-ns-form,
     :msg "ns references should be lists. This is not: [:use clojure.test].",
     :file (fname-from-parts "testcases" "wrongnsform.clj"),
     :line 20, :column 3}
    1,
    {:linter :wrong-ns-form,
     :msg "ns references should be lists. This is not: [:use clojure.test].",
     :file (fname-from-parts "testcases" "wrongnsform.clj"),
     :line 26, :column 3}
    1,
    {:linter :wrong-ns-form,
     :msg "ns references should be lists. This is not: [:use clojure.test].",
     :file (fname-from-parts "testcases" "wrongnsform.clj"),
     :line 33, :column 3}
    1,
    {:linter :wrong-ns-form,
     :msg "ns references should be lists. This is not: [:use clojure.test].",
     :file (fname-from-parts "testcases" "wrongnsform.clj"),
     :line 39, :column 3}
    1,
    {:linter :wrong-ns-form,
     :msg ":require has an arg that is a 1-item list. Clojure silently does nothing with this. To require it as a namespace, it should be a symbol on its own or it should be inside of a vector, not a list. To use it as the first part of a prefix list, there should be libspecs after it in the list: (eastwood.foo).",
     :file (fname-from-parts "testcases" "wrongnsform.clj"),
     :line 60, :column 13}
    1,
    {:linter :wrong-ns-form,
     :msg ":require contains the following valid flags, but it is most common to use them interactively, not in ns forms: :reload.",
     :file (fname-from-parts "testcases" "wrongnsform.clj"),
     :line 114, :column 3}
    1,
    {:linter :wrong-ns-form,
     :msg ":require has a libspec with wrong option keys: :only - option keys for :require should only include the following: :as :include-macros :refer :refer-macros.",
     :file (fname-from-parts "testcases" "wrongnsform.clj"),
     :line 150, :column 13}
    1,
    {:linter :wrong-ns-form,
     :msg ":require has a libspec with wrong option keys: :only - option keys for :require should only include the following: :as :exclude :include-macros :refer :refer-macros :rename.",
     :file (fname-from-parts "testcases" "wrongnsform.clj"),
     :line 182, :column 13}
    1}))

(deftest test31
  (lint-test
   'testcases.wrongprepost
   (concat @#'eastwood.lint/default-linters [:unused-locals])
   eastwood.lint/default-opts
   {{:linter :wrong-pre-post,
     :msg "All function preconditions should be in a vector. Found: (pos? x).",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 5, :column 9}
    1,
    {:linter :wrong-pre-post,
     :msg "All function preconditions should be in a vector. Found: (> x y).",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 12, :column 12}
    1,
    {:linter :wrong-pre-post,
     :msg "Postcondition found that is probably always logical true or always logical false. Should be changed to function call?  number?.",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 23, :column 10}
    1,
    {:linter :wrong-pre-post,
     :msg "All function postconditions should be in a vector. Found: (number? %).",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 35, :column 10}
    1,
    {:linter :wrong-pre-post,
     :msg "All function preconditions should be in a vector. Found: (number? x).",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 41, :column 9}
    1,
    {:linter :wrong-pre-post,
     :msg "Precondition found that is probably always logical true or always logical false. Should be changed to function call?  f.",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 51, :column 9}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: 7 in form (if 7 nil (do (throw (new java.lang.AssertionError (clojure.core/str \"Assert failed: \" (clojure.core/pr-str (quote 7))))))).",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 69, :column 1}
    1,
    {:linter :wrong-pre-post,
     :msg "Precondition found that is always logical true or always logical false. Should be changed to function call?  7.",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 70, :column 9}
    1,
    {:linter :wrong-pre-post,
     :msg "Precondition found that is probably always logical true or always logical false. Should be changed to function call?  >=.",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 70, :column 9}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: \"constant\" in form (if \"constant\" nil (do (throw (new java.lang.AssertionError (clojure.core/str \"Assert failed: \" (clojure.core/pr-str (quote \"constant\"))))))).",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 74, :column 1}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: :a in form (if :a nil (do (throw (new java.lang.AssertionError (clojure.core/str \"Assert failed: \" (clojure.core/pr-str (quote :a))))))).",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 74, :column 1}
    1,
    {:linter :wrong-pre-post,
     :msg "Precondition found that is always logical true or always logical false. Should be changed to function call?  :a.",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 75, :column 9}
    1,
    {:linter :wrong-pre-post,
     :msg "Postcondition found that is always logical true or always logical false. Should be changed to function call?  \"constant\".",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 76, :column 10}
    1,
    {:linter :wrong-pre-post,
     :msg "Precondition found that is probably always logical true or always logical false. Should be changed to function call?  wrong-pre-9.",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 102, :column 9}
    1,
    {:linter :constant-test,
     :msg "Test expression is always logical true or always logical false: Number in form (if Number nil (do (throw (new java.lang.AssertionError (clojure.core/str \"Assert failed: \" (clojure.core/pr-str (quote Number))))))).",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 108, :column 1}
    1,
    {:linter :wrong-pre-post,
     :msg "All function preconditions should be in a vector. Found: (instance? Number datastore).",
     :file (fname-from-parts "testcases" "wrongprepost.clj"),
     :line 111, :column 9}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'tqname' never used.",
     :file "testcases/wrongprepost.clj",
     :line 88,
     :column 25}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'tqname' never used.",
     :file "testcases/wrongprepost.clj",
     :line 100,
     :column 25}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'tqname' never used.",
     :file "testcases/wrongprepost.clj",
     :line 109,
     :column 25}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'redis-ttl-ms' never used.",
     :file "testcases/wrongprepost.clj",
     :line 109,
     :column 40}
    1}))

(deftest test32
  (lint-test
   'testcases.arglists
   (concat @#'eastwood.lint/default-linters [:unused-locals])
   eastwood.lint/default-opts
   {{:linter :bad-arglists,
     :msg "Function on var fn-with-arglists1 defined taking # args [1] but :arglists metadata has # args [2].",
     :file (fname-from-parts "testcases" "arglists.clj"),
     :line 9, :column 7}
    1,
    {:linter :bad-arglists,
     :msg "Function on var fn-with-arglists3 defined taking # args [1 3] but :arglists metadata has # args [2 4].",
     :file (fname-from-parts "testcases" "arglists.clj"),
     :line 22, :column 7}
    1}))

(deftest test33
  (lint-test
   'testcases.duplicateparams
   (concat @#'eastwood.lint/default-linters [:unused-locals])
   eastwood.lint/default-opts
   {{:linter :duplicate-params,
     :msg
     "Local name `a` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 7,
     :column 18}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `y` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 17,
     :column 30}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'y' never used.",
     :file "testcases/duplicateparams.clj",
     :line 27,
     :column 16}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `y` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 27,
     :column 20}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `y` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 36,
     :column 25}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'y' never used.",
     :file "testcases/duplicateparams.clj",
     :line 49,
     :column 17}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `y` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 49,
     :column 23}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'x' never used.",
     :file "testcases/duplicateparams.clj",
     :line 59,
     :column 14}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `x` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 59,
     :column 22}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'z' never used.",
     :file "testcases/duplicateparams.clj",
     :line 65,
     :column 23}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `z` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 65,
     :column 33}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'z' never used.",
     :file "testcases/duplicateparams.clj",
     :line 75,
     :column 21}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `z` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 75,
     :column 27}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `b` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 84,
     :column 35}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `c` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 84,
     :column 49}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `d` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 84,
     :column 51}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `a` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 95,
     :column 25}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `a` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 107,
     :column 16}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `a` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 113,
     :column 16}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `a` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 121,
     :column 16}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `a` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 129,
     :column 17}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `a` (part of full name `:g/a`) occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 194,
     :column 14}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `b` (part of full name `:i.j/b`) occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 194,
     :column 14}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'b' never used.",
     :file "testcases/duplicateparams.clj",
     :line 194,
     ;; I do not yet know _why_, but on both macOS and Linux I have
     ;; seen that the column number for this warning and the next one
     ;; is always 1 unless you are running Clojure 1.9. Weird. Just
     ;; expect that difference for now (and maybe always).
     :column (if (util/clojure-1-9-or-later) 22 1)}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'a' never used.",
     :file "testcases/duplicateparams.clj",
     :line 194,
     :column (if (util/clojure-1-9-or-later) 28 1)}
    1,
    {:linter :duplicate-params,
     :msg
     "Local name `f` occurs multiple times in the same argument vector.",
     :file "testcases/duplicateparams.clj",
     :line 194,
     :column 54}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'f' never used.",
     :file "testcases/duplicateparams.clj",
     :line 194,
     :column 54}
    1,
    {:linter :unused-or-default,
     :msg
     "Name f with default value in :or map of associative destructuring does not appear elsewhere in that same destructuring expression. The default value in the :or will never be used.",
     :file "testcases/duplicateparams.clj",
     :line 217,
     :column 46}
    1,
    {:linter :unused-or-default,
     :msg
     "Name f with default value in :or map of associative destructuring does not appear elsewhere in that same destructuring expression. The default value in the :or will never be used.",
     :file "testcases/duplicateparams.clj",
     :line 231,
     :column 20}
    1,
    {:linter :unused-or-default,
     :msg
     "Name h with default value in :or map of associative destructuring does not appear elsewhere in that same destructuring expression. The default value in the :or will never be used.",
     :file "testcases/duplicateparams.clj",
     :line 231,
     :column 24}
    1,
    {:linter :unused-or-default,
     :msg
     "Name c after :as is also in :or map of associative destructuring. The default value in the :or will never be used.",
     :file "testcases/duplicateparams.clj",
     :line 252,
     :column 31}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'tqname' never used.",
     :file "testcases/duplicateparams.clj",
     :line 257,
     :column 25}
    1,
    {:linter :unused-locals,
     :msg "let bound symbol 'redis-ttl-ms' never used.",
     :file "testcases/duplicateparams.clj",
     :line 257,
     :column 40}
    1}))

;; I would prefer if this threw an exception, but I think it does
;; not because Clojure reads, analyzes, and evaluates the namespace
;; before lint-test does, and thus the namespace is already there
;; when analyze-ns re-analyzes it. I tried a variation of the
;; lint-test macro that did remove-ns first, but that caused other
;; tests to fail for reasons that I did not spend long enough to
;; learn the reason for.
;;  (lint-test
;;   'testcases.topleveldo
;;   [:redefd-vars :unlimited-use]
;;   default-opts
;;   {})
(deftest test34
  (when (util/clojure-1-9-or-later)
    (lint-test
     'testcases.wrongprepost2
     (concat @#'eastwood.lint/default-linters [:unused-locals])
     eastwood.lint/default-opts
     {})
    (lint-test
     'testcases.duplicateparams2
     (concat @#'eastwood.lint/default-linters [:unused-locals])
     eastwood.lint/default-opts
     {{:linter :duplicate-params,
       :msg
       "Local name `a` occurs multiple times in the same argument vector.",
       :file "testcases/duplicateparams2.clj",
       :line 7,
       :column 48}
      1})))
