(ns eastwood.linters.typos
  (:require [clojure.pprint :as pp])
  (:require [eastwood.util :as util]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.reader.edn :as edn])
  (:import [name.fraser.neil.plaintext diff_match_patch]))

;; Typos in keywords

(def debug-keywords-found false)

(defn flattenable?
  [x]
  (or (sequential? x)
      (coll? x)))

(defn flatten-also-colls
  [x]
  (filter (complement flattenable?)
          (rest (tree-seq flattenable? seq x))))

;(defn debug-seq [x]
;  (when (some #{:end-line} (flatten-also-colls x))
;    (println "dbg debug-seq is travering a thing that has :end-line in it:")
;    (binding [*print-meta* true
;              *print-level* 10
;              *print-length* 50]
;      (pp/pprint x)))
;  (seq x))
;
;(defn debug-flatten-also-colls
;  [x]
;  (filter (complement flattenable?)
;          (rest (tree-seq flattenable? debug-seq x))))

(def ^:private ^diff_match_patch dmp (diff_match_patch.))

(defn levenshtein [s1 s2]
  (.diff_levenshtein dmp (.diff_main dmp s1 s2)))

;; Note: Walking the asts and looking for keywords also finds keywords
;; from macro expansions, ones that the developer never typed in their
;; code.  Better to use the forms to stay closer to the source code
;; they typed and warn about similar keywords there only.

;; The only disadvantage I know of in doing it this way is that
;; binding forms like {:keys [k1 k2 k3]} will see k1 k2 k3 as symbols
;; k1 k2 k3, not keywords :k1 :k2 :k3.

;; If we used the forms as read during analysis, there is the
;; disadvantage that any backquoted expressions will have keywords
;; like :line, :column, :end-line, and :end-column 'leak' from the
;; metadata added by the reader into the backquoted expression as
;; read.

;; Instead, re-read the forms from the string given by the :source key
;; on the linter argument, in a way that will not attach this :line
;; :column etc. metadata, implemented in string->forms.

(defn keyword-typos [{:keys [asts source]}]
  ;; Hack alert: Assumes that the first form in the file is an ns
  ;; form, and that the namespace remains the same throughout the
  ;; source file.
  (let [this-ns (-> (first asts) :env :ns the-ns)
;        _ (println (format "Namespace= %s\n"
;                           (-> (first asts) :env :ns the-ns)))
        forms (util/string->forms source this-ns false)
        freqs (->> forms
                   flatten-also-colls
                   (filter keyword?)
                   frequencies)]
    (when debug-keywords-found
;      (println "dbx: forms:")
;      (binding [*print-meta* true
;                *print-level* 10
;                *print-length* 50]
;        (pp/pprint forms))
      (println "dbx: keyword-typos frequencies:")
      (pp/pprint (into (sorted-map) freqs)))
    (for [[kw1 n] freqs
          [kw2 _] freqs
          :let [s1 (name kw1)
                s2 (name kw2)]
          :when (and (= n 1)
                     (not= s1 s2)
                     (< 3 (count s1))
                     (< (levenshtein s1 s2) 2))]
      {:linter :keyword-typos
       :msg (format "Possible keyword typo: %s instead of %s ?" kw1 kw2)})))


;; Wrong args to clojure.test/is macro invocations

;; TBD: Consider trying to find forms like (= ...) that are not
;; wrapped inside (is (= ...)) inside deftest, even if they are nested
;; within a (testing ...) form.

(defn subforms-with-first-symbol-in-set [form sym-set]
  (let [a (atom [])]
    (util/prewalk (fn [form]
                    (when (and (sequential? form)
                               (not (vector? form))
                               (contains? sym-set (first form)))
                      (swap! a conj form))
                    form)
                  form)
    @a))

(defn constant-expr-logical-true? [expr]
  (or (char? expr)
      (true? expr)
      (class? expr)
      (number? expr)
      (keyword? expr)
      (string? expr)
      (util/regex? expr)
      (list? expr)
      (map? expr)
      (set? expr)
      (vector? expr)))

(defn constant-expr-logical-false? [expr]
  (or (false? expr)
      (nil? expr)))

(defn constant-expr? [expr]
  (or (constant-expr-logical-false? expr)
      (constant-expr-logical-true? expr)))

(defn suspicious-is-forms [is-forms]
  (apply
   concat
   (for [isf is-forms]
     (let [is-args (next isf)
           n (count is-args)
           is-arg1 (first is-args)
           thrown? (and (sequential? is-arg1)
                        (= 'thrown? (first is-arg1)))
           thrown-args (and thrown? (rest is-arg1))
           thrown-arg2 (and thrown? (nth is-arg1 2))
           is-loc (-> isf first meta)]
;;       (when thrown?
;;         (println (format "Found (is (thrown? ...)) with thrown-arg2=%s: %s" thrown-arg2 isf)))
       (cond
        (and (= n 2) (string? is-arg1))
        [{:linter :suspicious-test,
          :msg (format "'is' form has string as first arg.  This will always pass.  If you meant to have a message arg to 'is', it should be the second arg, after the expression to test")
          :line (:line is-loc)}]
        
        (and (constant-expr-logical-true? is-arg1)
             (not (list? is-arg1)))
        [{:linter :suspicious-test,
          :msg (format "'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test")
          :line (:line is-loc)}]
        
        (and (= n 2) (not (string? (second is-args))))
        [{:linter :suspicious-test,
          :msg (format "'is' form has non-string as second arg.  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a string during test time, and you intended this, then ignore this warning.")
          :line (:line is-loc)}]
        
        (and thrown? (util/regex? thrown-arg2))
        [{:linter :suspicious-test,
          :msg (format "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?")
          :line (:line is-loc)}]
        
        (and thrown? (string? thrown-arg2))
        [{:linter :suspicious-test,
          :msg (format "(is (thrown? ...)) form has second thrown? arg that is a string.  This string is ignored.  Did you mean to use thrown-with-msg? instead of thrown?, and a regex instead of the string?")
          :line (:line is-loc)}]
        
        (and thrown? (some string? thrown-args))
        [{:linter :suspicious-test,
          :msg (format "(is (thrown? ...)) form has a string inside (thrown? ...).  This string is ignored.  Did you mean it to be a message shown if the test fails, like (is (thrown? ...) \"message\")?")
          :line (:line is-loc)}]
        
        :else nil)))))

(def ^:dynamic *var-info-map* nil)

(defn predicate-forms [forms form-type]
  (apply
   concat
   (for [f forms]
     (cond
      (and (not (list? f))
           (constant-expr? f))
      [(let [line (-> f meta :line)]
         {:linter :suspicious-test,
          :msg (format "Found constant form%s with class %s inside %s.  Did you intend to compare its value to something else inside of an 'is' expresssion?"
                       (cond line ""
                             (string? f) (str " \"" f "\"")
                             :else (str " " f))
                       (if f (.getName (class f)) "nil") form-type)
          :line line})]
      
      (sequential? f)
      (let [ff (first f)
            cc-sym (and ff (symbol "clojure.core" (name ff)))
            var-info (and cc-sym (get *var-info-map* cc-sym))
;;             _ (println (format "dbx: predicate-forms ff=%s cc-sym=%s var-info=%s"
;;                                ff cc-sym var-info))
            ]
        (cond
         (and var-info (get var-info :predicate))
         [{:linter :suspicious-test,
           :msg (format "Found (%s ...) form inside %s.  Did you forget to wrap it in 'is', e.g. (is (%s ...))?"
                        ff form-type ff)
           :line (-> ff meta :line)}]
         
         (and var-info (get var-info :pure-fn))
         [{:linter :suspicious-test,
           :msg (format "Found (%s ...) form inside %s.  This is a pure function with no side effects, and its return value is unused.  Did you intend to compare its return value to something else inside of an 'is' expression?"
                        ff form-type)
           :line (-> ff meta :line)}]
         
         :else nil))
      :else nil))))

;; Same hack alert for suspicious-test as for keyword-typos above.  We
;; should probably add this version of forms as another input to all
;; linters.  Perhaps we should add both the version with no :line
;; :column :end-line :end-column metadata done here, and another
;; version that does have that metadata, with documented examples of
;; what can go weird with a combination of the with-metadata version
;; and backquoted expressions.

(defn suspicious-test [{:keys [forms]}]
  (binding [*var-info-map* (edn/read-string (slurp (io/resource "var-info.edn")))]
    (doall
     (let [is-forms (subforms-with-first-symbol-in-set forms #{'is})
           deftest-forms (subforms-with-first-symbol-in-set forms #{'deftest})
           testing-forms (subforms-with-first-symbol-in-set forms #{'testing})
           deftest-subexprs (apply concat
                                   (map #(nthnext % 2) deftest-forms))
           testing-subexprs (apply concat
                                   (map #(nthnext % 2) testing-forms))]
       (concat (suspicious-is-forms is-forms)
               (predicate-forms deftest-subexprs 'deftest)
               (predicate-forms testing-subexprs 'testing))))))

;; Suspicious function calls

(def core-fns-that-do-little
  '{
    =        {1 {:args [x] :ret-val true}}
    ==       {1 {:args [x] :ret-val true}}
    not=     {1 {:args [x] :ret-val false}}
    <        {1 {:args [x] :ret-val true}}
    <=       {1 {:args [x] :ret-val true}}
    >        {1 {:args [x] :ret-val true}}
    >=       {1 {:args [x] :ret-val true}}
    min      {1 {:args [x] :ret-val x}}
    max      {1 {:args [x] :ret-val x}}
    min-key  {2 {:args [f x] :ret-val x}}
    max-key  {2 {:args [f x] :ret-val x}}
    +        {0 {:args []  :ret-val 0},
              1 {:args [x] :ret-val x}}
    +'       {0 {:args []  :ret-val 0},
              1 {:args [x] :ret-val x}}
    *        {0 {:args []  :ret-val 1},
              1 {:args [x] :ret-val x}}
    *'       {0 {:args []  :ret-val 1},
              1 {:args [x] :ret-val x}}
    ;; Note: (- x) and (/ x) do something useful
    dissoc   {1 {:args [map] :ret-val map}}
    disj     {1 {:args [set] :ret-val set}}
    merge    {0 {:args [] :ret-val nil},
              1 {:args [map] :ret-val map}}
    merge-with {1 {:args [f] :ret-val nil},
                2 {:args [f map] :ret-val map}}
    interleave {0 {:args [] :ret-val ()}}
    pr-str   {0 {:args [] :ret-val ""}}
    print-str {0 {:args [] :ret-val ""}}
    with-out-str {0 {:args [] :ret-val ""}}
    pr       {0 {:args [] :ret-val nil}}
    print    {0 {:args [] :ret-val nil}}

    comp     {0 {:args [] :ret-val identity}}
    partial  {1 {:args [f] :ret-val f}}
    lazy-cat {0 {:args [] :ret-val ()}}

    ;; Note: (->> x) throws arity exception, so no lint warning for it.
    ->       {1 {:args [x] :ret-val x}}
    ;; Note: (if x) is a compiler error, as is (if a b c d) or more args
    cond     {0 {:args [] :ret-val nil}}
    case     {2 {:args [x y] :ret-val y}}
    condp    {3 {:args [pred test-expr expr] :ret-val expr}}  ;; TBD: correct?
    when     {1 {:args [test] :ret-val nil}}
    when-not {1 {:args [test] :ret-val nil}}
    when-let {1 {:args [[x y]] :ret-val nil}}
    doseq    {1 {:args [[x coll]] :ret-val nil}}
    dotimes  {1 {:args [[i n]] :ret-val nil}}
    and      {0 {:args []  :ret-val true},
              1 {:args [x] :ret-val x}}
    or       {0 {:args []  :ret-val nil},
              1 {:args [x] :ret-val x}}
    doto     {1 {:args [x] :ret-val x}}
    declare  {0 {:args []  :ret-val nil}}

    })

(defn suspicious-expression [{:keys [forms]}]
  (apply
   concat
   (let [fs (subforms-with-first-symbol-in-set
             forms (set (keys core-fns-that-do-little)))]
     (for [f fs]
       (let [fn-sym (first f)
             num-args (dec (count f))
             suspicious-args (get core-fns-that-do-little fn-sym)
             info (get suspicious-args num-args)]
         (if (contains? suspicious-args num-args)
           [{:linter :suspicious-expression,
             :msg (format "%s called with %d args.  (%s%s) always returns %s.  Perhaps there are misplaced parentheses?"
                          fn-sym num-args fn-sym
                          (if (> num-args 0)
                            (str " " (str/join " " (:args info)))
                            "")
                          (:ret-val info))
             :line (-> fn-sym meta :line)}]))))))
