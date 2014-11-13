(ns eastwood.linters.typos
  (:require [clojure.pprint :as pp])
  (:require [eastwood.util :as util]
            [eastwood.passes :as pass]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [eastwood.copieddeps.dep10.clojure.tools.reader.edn :as edn]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast])
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
                   util/replace-comments-and-quotes-with-nil
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

(defn fn-call-returns-string?
  "sym is a symbol such as 'str or 'foo that appears at the beginning
of a parenthesized list, in a place where (is ...) expects a string
message to be printed if the test fails.  Return true if this symbol
names a function or macro commonly used to produce and return a
string, since this is sometimes used in clojure.test is expressions to
generate varying strings while the test is running."
  [sym]
  (contains? '#{str format pr-str prn-str print-str println-str with-out-str}
             sym))

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
        [(util/add-loc-info is-loc
          {:linter :suspicious-test,
           :msg (format "'is' form has string as first arg.  This will always pass.  If you meant to have a message arg to 'is', it should be the second arg, after the expression to test")})]
        
        (and (constant-expr-logical-true? is-arg1)
             (not (list? is-arg1)))
        [(util/add-loc-info is-loc
          {:linter :suspicious-test,
           :msg (format "'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test")})]
        
        (and (= n 2)
             (let [arg2 (second is-args)]
               (not (or (string? arg2)
                        (and (sequential? arg2)
                             (fn-call-returns-string? (first arg2)))))))
        [(util/add-loc-info is-loc
          {:linter :suspicious-test,
           :msg (format "'is' form has non-string as second arg.  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a string during test time, and you intended this, then ignore this warning.")})]
        
        (and thrown? (util/regex? thrown-arg2))
        [(util/add-loc-info is-loc
          {:linter :suspicious-test,
           :msg (format "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?")})]
        
        (and thrown? (string? thrown-arg2))
        [(util/add-loc-info is-loc
          {:linter :suspicious-test,
           :msg (format "(is (thrown? ...)) form has second thrown? arg that is a string.  This string is ignored.  Did you mean to use thrown-with-msg? instead of thrown?, and a regex instead of the string?")})]
        
        (and thrown? (some string? thrown-args))
        [(util/add-loc-info is-loc
          {:linter :suspicious-test,
           :msg (format "(is (thrown? ...)) form has a string inside (thrown? ...).  This string is ignored.  Did you mean it to be a message shown if the test fails, like (is (thrown? ...) \"message\")?")})]
        
        :else nil)))))


(def var-info-map-delayed
  (delay
   ;;(println "Reading var-info.edn for :suspicious-test linter")
   (edn/read-string (slurp (io/resource "var-info.edn")))))


(defn predicate-forms [subexpr-maps form-type]
  (let [var-info-map @var-info-map-delayed]
    (apply
     concat
     (for [{:keys [subexpr ast]} subexpr-maps
           :let [f subexpr]]
       (cond
        (and (not (list? f))
             (constant-expr? f))
        [(let [meta-loc (-> f meta)
               loc (or (pass/has-code-loc? meta-loc)
                       (pass/code-loc (pass/nearest-ast-with-loc ast)))]
           (util/add-loc-info loc
            {:linter :suspicious-test,
             :msg (format "Found constant form%s with class %s inside %s.  Did you intend to compare its value to something else inside of an 'is' expresssion?"
                          (cond (-> meta-loc :line) ""
                                (string? f) (str " \"" f "\"")
                                :else (str " " f))
                          (if f (.getName (class f)) "nil") form-type)}))]
        
        (sequential? f)
        (let [ff (first f)
              cc-sym (and ff
                          (instance? clojure.lang.Named ff)
                          (symbol "clojure.core" (name ff)))
              var-info (and cc-sym (var-info-map cc-sym))
;;              _ (println (format "dbx: predicate-forms ff=%s cc-sym=%s var-info=%s"
;;                                 ff cc-sym var-info))
              loc (-> ff meta)]
          (cond
           (and var-info (get var-info :predicate))
           [(util/add-loc-info loc
             {:linter :suspicious-test,
              :msg (format "Found (%s ...) form inside %s.  Did you forget to wrap it in 'is', e.g. (is (%s ...))?"
                           ff form-type ff)})]
           
           (and var-info (get var-info :pure-fn))
           [(util/add-loc-info loc
             {:linter :suspicious-test,
              :msg (format "Found (%s ...) form inside %s.  This is a pure function with no side effects, and its return value is unused.  Did you intend to compare its return value to something else inside of an 'is' expression?"
                           ff form-type)})]
           
           :else nil))
        :else nil)))))

;; suspicious-test used to do its job only examining source forms, but
;; now it goes through the forms on the
;; :eastwood-partly-resolved-forms lists of AST nodes that have such a
;; key.  This is useful for distinguishing occurrences of (is ...)
;; forms that are from clojure.test/is, vs. ones from
;; clojure.core.typed/is from core.typed.

;; For each AST node, we want only the first expression that has a
;; first symbol equal to clojure.test/is.  There can be more than one
;; such expression in the list, because (clojure.test/is expr) macro
;; expands to (clojure.test/is expr nil).  We do not want to duplicate
;; messages, so keep only the one that is closer to what the
;; programmer wrote.  Another disadvantage of looking at the second
;; one is that the 'nil' argument causes warnings about a non-string
;; second argument to be issued, if we check it.

(defn suspicious-test [{:keys [asts]}]
  (let [frms (fn [ast] (remove #(symbol? (second %))
                               (map list
                                    (:eastwood/partly-resolved-forms ast)
                                    (:raw-forms ast))))
        pr-formasts (for [ast (mapcat ast/nodes asts)
                          [pr-form raw-form] (frms ast)]
                      {:pr-form pr-form
                       :raw-form raw-form
                       :ast ast})
        
        pr-first-is-formasts
        (remove nil?
                (for [ast (mapcat ast/nodes asts)]
                  (let [formasts (for [[pr-form raw-form] (frms ast)]
                                   {:pr-form pr-form
                                    :raw-form raw-form
                                    :ast ast})]
                    (first (filter #(= 'clojure.test/is
                                       (first (:pr-form %)))
                                   formasts)))))
        
        ;; To find deftest subexpressions, first filter all of the
        ;; partly-resolved forms for those with a first symbol equal
        ;; to clojure.test/deftest, then get of the first 2 symbols
        ;; from each, which are the deftest and the Var name following
        ;; deftest.
        pr-deftest-subexprs
        (->> pr-formasts
             (filter #(= 'clojure.test/deftest (first (:pr-form %))))
             (mapcat (fn [formast]
                       (for [subexpr (nthnext (:pr-form formast) 2)]
                         (assoc formast :subexpr subexpr)))))
        
        ;; Similarly for testing subexprs as for deftest subexprs.
        ;; TBD: Make a helper function to eliminate the nearly
        ;; duplicated code between deftest and testing.
        pr-testing-subexprs
        (->> pr-formasts
             (filter #(= 'clojure.test/testing (first (:pr-form %))))
             (mapcat (fn [formast]
                       (for [subexpr (nthnext (:pr-form formast) 2)]
                         (assoc formast :subexpr subexpr)))))

        pr-is-formasts pr-first-is-formasts
        pr-is-forms (map :raw-form pr-is-formasts)]
    (concat (suspicious-is-forms pr-is-forms)
            (predicate-forms pr-deftest-subexprs 'deftest)
            (predicate-forms pr-testing-subexprs 'testing))))

;; Suspicious function calls and macro invocations

(def core-first-vars-that-do-little
  '{
    ;; TBD: It seems like = == and not= are redundant with the
    ;; corresponding entries in core-fns-that-do-little below.  See if
    ;; this map can be made only for macros, and that one only for
    ;; functions, and all that is detected now continues to be
    ;; detected.
    =        {1 {:args [x] :ret-val true}}
    ==       {1 {:args [x] :ret-val true}}
    not=     {1 {:args [x] :ret-val false}}
    lazy-cat {0 {:args [] :ret-val ()}}  ; macro where (lazy-cat) expands to (concat).  TBD: add concat to this list?
    ;; Note: (->> x) throws arity exception, so no lint warning for it.
    ->       {1 {:args [x] :ret-val x}}  ; macro where (-> x) expands to x
    ;; Note: (if x) is a compiler error, as is (if a b c d) or more args
    cond     {0 {:args [] :ret-val nil}}  ; macro where (cond) -> nil
    case     {2 {:args [x y] :ret-val y}}  ; macro (case 5 2) -> (let* [x 5] 2)
    condp    {3 {:args [pred test-expr expr] :ret-val expr}}  ;; macro (condp = 5 2) -> (let* [pred = expr 5] 2)
    when     {1 {:args [test] :ret-val nil}} ; macro (when 5) -> (if 5 (do))
    when-not {1 {:args [test] :ret-val nil}} ; macro (when-not 5) -> (if 5 nil (do))
    when-let {1 {:args [[x y]] :ret-val nil}} ; macro (when-let [x 5]) -> (let* [temp 5] (when temp (let [x temp])))
    doseq    {1 {:args [[x coll]] :ret-val nil}} ; macro (doseq [x [1 2 3]]) has big expansion
    dotimes  {1 {:args [[i n]] :ret-val nil}} ; macro (dotimes [i 10]) has medium-sized expansion using loop
    with-out-str {0 {:args [] :ret-val ""}}
    and      {0 {:args []  :ret-val true},  ; macro (and) -> true
              1 {:args [x] :ret-val x}}     ; macro (and 5) -> 5
    or       {0 {:args []  :ret-val nil},   ; macro (or) -> nil
              1 {:args [x] :ret-val x}}     ; macro (or 5) -> 5
    doto     {1 {:args [x] :ret-val x}}     ; macro (doto x) -> (let* [temp x] temp)
    declare  {0 {:args []  :ret-val nil}}   ; macro (declare) -> (do)
    })

;; Note that suspicious-expression-forms is implemented on the
;; original source code, before macro expansion, so it can give false
;; positives for expressions like this:

;; (-> x (doto (method args)))     ; macro expands to (doto x (method args))

;; For most suspicious function calls, we check for them in the ast in
;; suspicious-expression-asts below, but that will not find suspicious
;; calls on macros, or on (is (= expr)) forms because of how is
;; macro-expands.

;; Another possibility is to avoid issuing warnings on the original
;; un-macro-expanded code if one of these expressions appears directly
;; inside of a -> or ->> macro, which is a bit hackish, but should
;; avoid most of the incorrect warnings I have seen so far in real
;; code.

(defn suspicious-expression-forms [{:keys [forms]}]
  (apply
   concat
   (let [fs (-> forms
                util/replace-comments-and-quotes-with-nil
                (util/subforms-with-first-in-set
                 (set (keys core-first-vars-that-do-little))))]
     (for [f fs]
       (let [fn-sym (first f)
             loc (-> fn-sym meta)
             num-args (dec (count f))
             suspicious-args (get core-first-vars-that-do-little fn-sym)
             info (get suspicious-args num-args)]
         (if (contains? suspicious-args num-args)
           [(util/add-loc-info loc
             {:linter :suspicious-expression,
              :msg (format "%s called with %d args.  (%s%s) always returns %s.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"
                           fn-sym num-args fn-sym
                           (if (> num-args 0)
                             (str " " (str/join " " (:args info)))
                             "")
                           (if (= "" (:ret-val info))
                             "\"\""
                             (print-str (:ret-val info))))})]))))))

;; Note: Looking for asts that contain :invoke nodes for the function
;; 'clojure.core/= will not find expressions like (clojure.test/is (=
;; (+ 1 1))), because the is macro changes that to an apply on
;; function = with one arg, which is a sequence of expressions.
;; Finding one-arg = can probably only be done at the source form
;; level.

(def core-fns-that-do-little
  {
   'clojure.core/=        '{1 {:args [x] :ret-val true}}
   'clojure.core/==       '{1 {:args [x] :ret-val true}}
   'clojure.core/not=     '{1 {:args [x] :ret-val false}}
   'clojure.core/<        '{1 {:args [x] :ret-val true}}
   'clojure.core/<=       '{1 {:args [x] :ret-val true}}
   'clojure.core/>        '{1 {:args [x] :ret-val true}}
   'clojure.core/>=       '{1 {:args [x] :ret-val true}}
   'clojure.core/min      '{1 {:args [x] :ret-val x}}
   'clojure.core/max      '{1 {:args [x] :ret-val x}}
   'clojure.core/min-key  '{2 {:args [f x] :ret-val x}}
   'clojure.core/max-key  '{2 {:args [f x] :ret-val x}}
   'clojure.core/dissoc   '{1 {:args [map] :ret-val map}}
   'clojure.core/disj     '{1 {:args [set] :ret-val set}}
   'clojure.core/merge    '{0 {:args [] :ret-val nil},
                            1 {:args [map] :ret-val map}}
   'clojure.core/merge-with '{1 {:args [f] :ret-val nil},
                              2 {:args [f map] :ret-val map}}
   'clojure.core/interleave '{0 {:args [] :ret-val ()}}
   'clojure.core/pr-str   '{0 {:args [] :ret-val ""}}
   'clojure.core/print-str '{0 {:args [] :ret-val ""}}
   'clojure.core/pr       '{0 {:args [] :ret-val nil}}
   'clojure.core/print    '{0 {:args [] :ret-val nil}}
   
   'clojure.core/comp     '{0 {:args [] :ret-val identity}}
   'clojure.core/partial  '{1 {:args [f] :ret-val f}}
   'clojure.core/+        '{0 {:args []  :ret-val 0},   ; inline
                            1 {:args [x] :ret-val x}}
   'clojure.core/+'       '{0 {:args []  :ret-val 0},   ; inline
                            1 {:args [x] :ret-val x}}
   'clojure.core/*        '{0 {:args []  :ret-val 1},   ; inline
                            1 {:args [x] :ret-val x}}
   'clojure.core/*'       '{0 {:args []  :ret-val 1},   ; inline
                            1 {:args [x] :ret-val x}}
   ;; Note: (- x) and (/ x) do something useful
   })

(defn suspicious-expression-asts [{:keys [asts]}]
  (let [fn-sym-set (set (keys core-fns-that-do-little))
        invoke-asts (->> asts
                         (mapcat ast/nodes)
                         (filter #(and (= (:op %) :invoke)
                                       (let [v (-> % :fn :var)]
                                         (contains? fn-sym-set
                                                    (util/var-to-fqsym v))))))]
    (doall
     (remove
      nil?
      (for [ast invoke-asts]
        (let [^clojure.lang.Var fn-var (-> ast :fn :var)
              fn-sym (.sym fn-var)
              fn-fqsym (util/var-to-fqsym fn-var)
              num-args (count (-> ast :args))
              form (-> ast :form)
              loc (-> form meta)
              suspicious-args (core-fns-that-do-little fn-fqsym)
              info (get suspicious-args num-args)]
          (if (contains? suspicious-args num-args)
            (util/add-loc-info loc
             {:linter :suspicious-expression,
              :msg (format "%s called with %d args.  (%s%s) always returns %s.  Perhaps there are misplaced parentheses?"
                           fn-sym num-args fn-sym
                           (if (> num-args 0)
                             (str " " (str/join " " (:args info)))
                             "")
                           (if (= "" (:ret-val info))
                             "\"\""
                             (print-str (:ret-val info))))}))))))))

(defn suspicious-expression [& args]
  (concat
   (apply suspicious-expression-forms args)
   (apply suspicious-expression-asts args)))


(defn logical-true-test
  "Return the ast to report a warning for, if the 'ast' argument
represents not necessarily a constant expression, but definitely an
expression that evaluates as logical true in an if test, e.g. vectors,
maps, and sets, even if they have contents that vary at run time, are
always logical true.  Otherwise, return nil."
  [ast]
  (cond (#{:vector :map :set :quote} (:op ast)) ast
        (= :with-meta (:op ast)) (logical-true-test (:expr ast))
        :else nil))


(defn pure-fn-ast? [ast]
  (and (= :var (:op ast))
       (var? (:var ast))
       (let [sym (util/var-to-fqsym (:var ast))]
         (if (:pure-fn (@var-info-map-delayed sym))
           sym))))


(declare constant-ast)


(defn constant-map? [ast]
  (and (= :map (:op ast))
       (every? constant-ast (:keys ast))
       (every? constant-ast (:vals ast))))


(defn constant-vector-or-set? [ast]
  (and (#{:vector :set} (:op ast))
       (every? constant-ast (:items ast))))


(defn constant-ast
  "Return nil if 'ast' is not one that we can determine to be a
constant value.  Does not do all compile-time evaluation that would be
possible, to keep things relatively simple.  If we can determine it to
be a constant value, return an ast that can be used to issue a
warning, that contains the constant value."
  [ast]
  (cond (= :with-meta (:op ast))    (constant-ast (:expr ast))

      ;;; BEGIN new stuff
;;      (and (= :local (:op ast))
;;           (let [local-sym (:form ast)
;;                 bound-val-binding (-> ast :env :locals local-sym)
;;                 bound-val-ast (if (= :binding (:op bound-val-binding))
;;                                 (:init bound-val-binding))]
;;             (println (format "Found ast op=:local with local-sym=%s val-form=%s line=%s"
;;                              local-sym (:form bound-val-ast)
;;                              (-> bound-val-ast :form meta :line)))
;;             (if bound-val-ast
;;               (constant-ast? bound-val-ast))))
      ;;; END new stuff

      (#{:const :quote} (:op ast))  ast
      (constant-map? ast)           ast
      (constant-vector-or-set? ast) ast

      (= :invoke (:op ast))
      (let [pfn (pure-fn-ast? (:fn ast))]
        (cond (and pfn (every? constant-ast (:args ast)))
              ast
              
              (= 'clojure.core/not pfn)
              (logical-true-test (-> ast :args first))
              
              :else nil))

      :else nil))

(defn assert-expansion? [ast]
  (and (= :if (:op ast))
       (seq (:raw-forms ast))
       (= 'clojure.core/assert (-> ast :eastwood/partly-resolved-forms
                                   first first))))


(defn if-with-predictable-test [ast]
  (if (and (= :if (:op ast))
           (not (assert-expansion? ast)))
    (or (constant-ast (-> ast :test))
        (logical-true-test (-> ast :test)))))


;; Assumption: Only call this function if
;; if-with-predictable-test returned true for the ast.
(defn default-case-at-end-of-cond? [ast]
  ;; I have seen true and :default used in several projects rather
  ;; than :else
  (and (#{:else :default true} (-> ast :test :form))
       (seq (:eastwood/partly-resolved-forms ast))
       (= 'clojure.core/cond
          (-> ast :eastwood/partly-resolved-forms first first))))


(defn constant-test [{:keys [asts]}]
  (let [const-tests (->> asts
                         (mapcat ast/nodes)
                         (keep #(if (default-case-at-end-of-cond? %)
                                  nil
                                  (if-with-predictable-test %))))]
    (for [ast const-tests
          :let [form (-> ast :form)
                form-to-print (if (nil? form) "nil" form)
                loc (or (pass/has-code-loc? (-> ast :form meta))
                        (pass/code-loc (pass/nearest-ast-with-loc ast)))]]
      (util/add-loc-info loc
       {:linter :constant-test
        :msg (format "Test expression is always logical true or always logical false: %s"
                     form-to-print)}))))
