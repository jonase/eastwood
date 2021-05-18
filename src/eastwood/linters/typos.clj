(ns eastwood.linters.typos
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.set :as set]
   [clojure.string :as str]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
   [eastwood.copieddeps.dep10.clojure.tools.reader.edn :as edn]
   [eastwood.passes :as pass]
   [eastwood.util :as util])
  (:import
   (name.fraser.neil.plaintext diff_match_patch)))

;; Typos in keywords

(def debug-keywords-found false)

(defn flattenable?
  [x]
  (or (sequential? x)
      (coll? x)))

(defn flatten-also-colls
  [x]
  (remove flattenable? (rest (tree-seq flattenable? seq x))))

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

(defn keywords-very-similar?
  "Check for the special case of two names differing only by one of
  them being equal to \"_\" followed by the other name.  Apparently
  this is somewhat common for keywords names when interacting with
  Datomic."
  [name-str1 name-str2]
  (let [l1 (count name-str1)
        l2 (count name-str2)
        [^String n1 l1 ^String n2 l2] (if (< l2 l1)
                                        [name-str2 l2 name-str1 l1]
                                        [name-str1 l1 name-str2 l2])]
    (and (== l2 (inc l1))
         (.startsWith n2 "_")
         (.endsWith n2 n1))))

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

(defn keyword-typos [{:keys [asts source]} opt]
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
                     (not (keywords-very-similar? s1 s2))
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

;; This is an alternate way to find the AST of the :message key for
;; the call to clojure.test/do-report than what is used below for
;; calculating message-ast, but it seems much more fragile to possible
;; future changes to tools.analyzer(.jvm) libraries.

;;           (if (or thrown? thrown-with-msg?)
;;             (util/get-in-ast ast ; ast is a :try node if it expanded from clojure.test/is
;;                              [[[:body] :do]
;;                               [[:ret] :try]
;;                               [[:body] :do]
;;                               [[:ret] :invoke] ; of clojure.test/do-report
;;                               [[:args 0] :map]])
;;             (util/get-in-ast ast
;;                              [[[:body] :do]
;;                               [[:ret]  :let]
;;                               [[:body] :do]
;;                               [[:statements 0] :if]
;;                               [[:then] :invoke]  ; of clojure.test/do-report
;;                               [[:args 0] :map]]))

(defn suspicious-is-forms [is-formasts]
  (apply
   concat
   (for [{isf :raw-form, ast :ast} is-formasts]
     (let [is-args (next isf)
           n (count is-args)
           is-arg1 (first is-args)
           thrown? (and (sequential? is-arg1)
                        (= 'thrown? (first is-arg1)))
           thrown-with-msg? (and (sequential? is-arg1)
                                 (= 'thrown-with-msg? (first is-arg1)))
           thrown-args (and thrown? (rest is-arg1))
           thrown-arg2 (and thrown? (nth is-arg1 2))
           is-loc (-> isf first meta)
           pred (fn [{:keys [op]}]
                  (#{:const :map} op))
           first-invoke-do-report-ast (doto (->>
                                             (ast/nodes ast)
                                             (filter #(and
                                                       (= :invoke (:op %))
                                                       (= 'clojure.test/do-report
                                                          (-> % :fn :var
                                                              util/var-to-fqsym))
                                                       (->> % :args (some pred))))
                                             first)
                                        assert)
           const-or-map-ast (doto (->> (get-in first-invoke-do-report-ast [:args])
                                       (filter pred)
                                       first)
                              assert)
           message-val-or-ast (case (:op const-or-map-ast)
                                :const (-> const-or-map-ast :val :message)
                                :map (util/get-val-in-map-ast
                                      (get-in first-invoke-do-report-ast [:args 0])
                                      :message))
           message-tag (if message-val-or-ast
                         (case (:op const-or-map-ast)
                           :const (class message-val-or-ast)
                           :map (:tag message-val-or-ast)))
;;           _ (println (format "dbg line %d msg-tag=%s (%s) string?=%s msg-form=%s"
;;                              (:line is-loc) message-tag (class message-tag)
;;                              (= message-tag java.lang.String)
;;                              isf))
           ]
;;       (when thrown?
;;         (println (format "Found (is (thrown? ...)) with thrown-arg2=%s: %s" thrown-arg2 isf)))
       (cond
         (and (= n 2) (string? is-arg1))
         [{:loc is-loc
           :linter :suspicious-test,
           :msg (format "'is' form has string as first arg.  This will always pass.  If you meant to have a message arg to 'is', it should be the second arg, after the expression to test")}]

         (and (constant-expr-logical-true? is-arg1)
              (not (list? is-arg1)))
         [{:loc is-loc
           :linter :suspicious-test,
           :msg (format "'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test")}]

         (and (= n 2)
              (not= message-tag java.lang.String))
         [{:loc is-loc
           :linter :suspicious-test,
           :msg (format "'is' form has non-string as second arg (inferred type is %s).  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a message string during test time, and you intended this, you may wrap it in a call to (str ...) so this warning goes away."
                        message-tag)}]

         (and thrown? (util/regex? thrown-arg2))
         [{:loc is-loc
           :linter :suspicious-test,
           :msg (format "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?")}]

         (and thrown? (string? thrown-arg2))
         [{:loc is-loc
           :linter :suspicious-test,
           :msg (format "(is (thrown? ...)) form has second thrown? arg that is a string.  This string is ignored.  Did you mean to use thrown-with-msg? instead of thrown?, and a regex instead of the string?")}]

         (and thrown? (some string? thrown-args))
         [{:loc is-loc
           :linter :suspicious-test,
           :msg (format "(is (thrown? ...)) form has a string inside (thrown? ...).  This string is ignored.  Did you mean it to be a message shown if the test fails, like (is (thrown? ...) \"message\")?")}]

         :else nil)))))

(def var-info-map-delayed
  (delay
   ;;(println "Reading var-info.edn for :suspicious-test linter")
    (edn/read-string (slurp (io/resource "var-info.edn")))))

(defn predicate-forms [opt subexpr-maps form-type]
  (let [var-info-map @var-info-map-delayed
        result (for [{:keys [subexpr ast]} subexpr-maps
                     :let [f subexpr]]
                 (cond
                   (and (not (list? f))
                        (constant-expr? f))
                   (let [meta-loc (-> f meta)
                         loc (or (pass/has-code-loc? meta-loc)
                                 (pass/code-loc (pass/nearest-ast-with-loc ast)))
                         warning {:loc loc
                                  :linter :suspicious-test
                                  :suspicious-test {:ast ast}
                                  :qualifier f
                                  :msg (format "Found constant form%s with class %s inside %s.  Did you intend to compare its value to something else inside of an 'is' expresssion?"
                                               (cond (-> meta-loc :line) ""
                                                     (string? f) (str " \"" f "\"")
                                                     :else (str " " f))
                                               (if f (.getName (class f)) "nil") form-type)}]
                     (when (util/allow-warning warning opt)
                       [warning]))

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
                       [{:loc loc
                         :linter :suspicious-test,
                         :msg (format "Found (%s ...) form inside %s.  Did you forget to wrap it in 'is', e.g. (is (%s ...))?"
                                      ff form-type ff)}]

                       (and var-info (get var-info :pure-fn))
                       [{:loc loc
                         :linter :suspicious-test,
                         :msg (format "Found (%s ...) form inside %s.  This is a pure function with no side effects, and its return value is unused.  Did you intend to compare its return value to something else inside of an 'is' expression?"
                                      ff form-type)}]

                       :else nil))
                   :else nil))]
    (->> result
         (keep identity)
         (apply concat))))

;; suspicious-test used to do its job only examining source forms, but
;; now it goes through the forms on the :raw-forms lists of the AST
;; nodes that have such a key.  This is useful for distinguishing
;; occurrences of (is ...)  forms that are from clojure.test/is,
;; vs. ones from clojure.core.typed/is from core.typed.

;; For each AST node, we want only the first expression that has a
;; first symbol equal to clojure.test/is.  There can be more than one
;; such expression in the list, because (clojure.test/is expr) macro
;; expands to (clojure.test/is expr nil).  We do not want to duplicate
;; messages, so keep only the one that is closer to what the
;; programmer wrote.  Another disadvantage of looking at the second
;; one is that the 'nil' argument causes warnings about a non-string
;; second argument to be issued, if we check it.

(defn suspicious-test [{:keys [asts]} opt]
  (let [frms (fn [ast] (remove symbol? (:raw-forms ast)))
        formasts (for [ast (mapcat ast/nodes asts)
                       raw-form (frms ast)]
                   {:raw-form raw-form
                    :ast ast})

        pr-first-is-formasts
        (remove nil?
                (for [ast (mapcat ast/nodes asts)]
                  (let [formasts (for [raw-form (frms ast)]
                                   {:raw-form raw-form
                                    :ast ast})]
                    (first (filter #(= 'clojure.test/is
                                       (util/fqsym-of-raw-form (:raw-form %)))
                                   formasts)))))

        ;; To find deftest subexpressions, first filter all of the raw
        ;; forms for those with a resolved-op symbol equal to
        ;; clojure.test/deftest, then get of the first 2 symbols from
        ;; each, which are the deftest and the Var name following
        ;; deftest.
        pr-deftest-subexprs
        (->> formasts
             (filter #(= 'clojure.test/deftest
                         (util/fqsym-of-raw-form (:raw-form %))))
             (mapcat (fn [formast]
                       (for [subexpr (nthnext (:raw-form formast) 2)]
                         (assoc formast :subexpr subexpr)))))

        ;; Similarly for testing subexprs as for deftest subexprs.
        ;; TBD: Make a helper function to eliminate the nearly
        ;; duplicated code between deftest and testing.
        pr-testing-subexprs
        (->> formasts
             (filter #(= 'clojure.test/testing
                         (util/fqsym-of-raw-form (:raw-form %))))
             (mapcat (fn [formast]
                       (for [subexpr (nthnext (:raw-form formast) 2)]
                         (assoc formast :subexpr subexpr)))))

        pr-is-formasts pr-first-is-formasts]
    (concat (suspicious-is-forms pr-is-formasts)
            (predicate-forms opt pr-deftest-subexprs 'deftest)
            (predicate-forms opt pr-testing-subexprs 'testing))))

;; Suspicious macro invocations.  Any macros in clojure.core that can
;; have 'trivial' expansions are included here, if it can be
;; determined solely by the number of arguments to the macro.

(def core-macros-that-do-little
  '{;; (-> x) expands to x
    clojure.core/->       {1 {:args [x] :ret-val x}}

    ;; (->> x) threw an arity exception for some version of Clojure
    ;; before 1.6.0, but it expands to x in Clojure 1.6.0.
    clojure.core/->>      {1 {:args [x] :ret-val x}}

    ;; (and) -> true
    ;; (and 5) -> 5
    clojure.core/and      {0 {:args []  :ret-val true},
                           1 {:args [x] :ret-val x}}

    ;; (as-> val x) expands to (let [x val] x)
    clojure.core/as->     {2 {:args [expr name] :ret-val expr}}

    ;; (case 5 2) -> (let* [x 5] 2)
    clojure.core/case     {2 {:args [x y] :ret-val y}}

    ;; (cond) -> nil
    clojure.core/cond     {0 {:args [] :ret-val nil}}

    ;; (cond-> x) -> (let [temp x] temp)
    clojure.core/cond->   {1 {:args [x] :ret-val x}}

    ;; (cond->> x) -> (let [temp x] temp)
    clojure.core/cond->>  {1 {:args [x] :ret-val x}}

    ;; (condp = 5 2) -> (let* [pred = expr 5] 2)
    clojure.core/condp    {3 {:args [pred test-expr expr] :ret-val expr}}

    ;; (declare) -> (do)
    clojure.core/declare  {0 {:args [] :ret-val nil}}

    ;; (delay) -> (delay nil)
    clojure.core/delay    {0 {:args [] :ret-val (delay nil)}}

    ;; (doseq [x [1 2 3]]) has big expansion, but doesn't do anything
    ;; useful.
    clojure.core/doseq    {1 {:args [[x coll]] :ret-val nil}}

    ;; (dotimes [i 10]) has medium-sized expansion using loop, but
    ;; doesn't do anything useful.
    clojure.core/dotimes  {1 {:args [[i n]] :ret-val nil}}

    ;; (doto x) -> (let* [temp x] temp)
    clojure.core/doto     {1 {:args [x] :ret-val x}}

    ;; (if x) is a compiler error, as is (if a b c d) or more args.
    ;; Similarly for if-let, if-not, if-some

    ;; (import) -> (do)
    clojure.core/import   {0 {:args [] :ret-val nil}}

    ;; (lazy-cat) expands to (concat).  TBD: add concat to this list?
    clojure.core/lazy-cat {0 {:args [] :ret-val ()}}

    ;; (let [x val]) always returns nil
    clojure.core/let      {1 {:args [bindings] :ret-val nil}}

    ;; letfn with empty body is similar to let with empty body
    clojure.core/letfn    {1 {:args [bindings] :ret-val nil}}

    ;; locking with empty body is similar to let with empty body
    clojure.core/locking  {1 {:args [x] :ret-val nil}}

    ;; loop with empty body is similar to let with empty body
    clojure.core/loop     {1 {:args [bindings] :ret-val nil}}

    ;; (or) -> nil
    ;; (or 5) -> 5
    clojure.core/or       {0 {:args []  :ret-val nil},
                           1 {:args [x] :ret-val x}}

    ;; (pvalues) -> (pcalls)
    clojure.core/pvalues  {0 {:args [] :ret-val ()}}

    ;; (some-> 5) -> (let* [temp 5] temp)
    clojure.core/some->   {1 {:args [expr] :ret-val expr}}

    ;; (some->> 5) -> (let* [temp 5] temp)
    clojure.core/some->>  {1 {:args [expr] :ret-val expr}}

    ;; (when 5) -> (if 5 (do))
    clojure.core/when     {1 {:args [test] :ret-val nil}}

    ;; (when-first [x 5]) has medium-sized expansion, but returns nil
    ;; regardless of value in binding.
    clojure.core/when-first {1 {:args [[x y]] :ret-val nil}}

    ;; (when-let [x 5]) -> (let* [temp 5] (when temp (let [x temp]))),
    ;; returning nil regardless of whether temp is logical true or
    ;; false.
    clojure.core/when-let {1 {:args [[x y]] :ret-val nil}}

    ;; (when-not 5) -> (if 5 nil (do))
    clojure.core/when-not {1 {:args [test] :ret-val nil}}

    ;; (when-some [x 5]) has medium-sized expansion, but returns nil
    ;; regardless of value in binding.
    clojure.core/when-some {1 {:args [[x y]] :ret-val nil}}

    ;; (with-bindings map) has medium-sized expansion, but always
    ;; returns nil.
    clojure.core/with-bindings {1 {:args [map] :ret-val nil}}

    ;; (with-in-str "foo") has medium-sized expansion, but always
    ;; returns nil.
    clojure.core/with-in-str {1 {:args [s] :ret-val nil}}

    ;; (with-local-vars [x val]) has medium-sized expansion, but
    ;; always returns nil.
    clojure.core/with-local-vars {1 {:args [bindings] :ret-val nil}}

    ;; (with-open [x expr]) has medium-sized expansion, but always
    ;; returns nil, unless expr throws an exception, but that would be
    ;; a strange use of with-open to do nothing but that.
    clojure.core/with-open {1 {:args [bindings] :ret-val nil}}

    ;; (with-out-str) has medium-sized expansion, but always returns
    ;; "".
    clojure.core/with-out-str {0 {:args [] :ret-val ""}}

    ;; (with-precision precision) has medium-sized expansion, but
    ;; always returns nil.
    clojure.core/with-precision {1 {:args [precision] :ret-val nil}}

    ;; (with-redefs [var expr]) has medium-sized expansion, but always
    ;; returns nil.
    clojure.core/with-redefs {1 {:args [bindings] :ret-val nil}}})

;; suspicious-macro-invocations was formerly called
;; suspicious-expressions-forms, and implemented to use the 'forms'
;; key, not the asts.  That was closer to the original source code,
;; before macro expansion, so it would give warnings for expressions
;; that we would prefer it not warn, like this one:

;; (-> x (doto (method args)))     ; macro expands to (doto x (method args))

;; The newer version suspicious-macro-invocations uses the asts, which
;; contain forms on the key :raw-forms, to see what the forms looked
;; like before macroexpansion.  This should avoid the issue above.

;; For suspicious function calls, we check for them in the asts in
;; suspicious-fn-calls below, but that will not find suspicious calls
;; on macros, nor on clojure.test/is forms.

;; suspicious-is-try-expr looks for suspicious expressions in calls to
;; clojure.test/is like these examples below.  Special detection code
;; is needed because of the unusual way that clojure.test/is
;; macroexpands.

;; (is (= (+ 1 1)) 2)   warn for (= x)
;; (is (> (+ 1 1)) 1)   warn for (> x)
;; (is (-> 1 (= 1)))    This case is warning as desired already, no warning
;; (is (-> 1 (=)))      Also warning as desired now, warn for (= x) not (=)

(defn raw-form-of-interest? [raw-form core-macros-that-do-little]
  (get core-macros-that-do-little (util/fqsym-of-raw-form raw-form)))

(defn and-or-self-expansion? [ast]
  (let [parent-ast (-> ast :eastwood/ancestors peek)]
    (and (= :if (-> parent-ast :op))
         (= :local (-> parent-ast :test :op))
         (#{'clojure.core/and 'clojure.core/or}
          (-> ast :raw-forms first util/fqsym-of-raw-form)))))

(defn cond-self-expansion? [ast]
  (let [parent-ast (-> ast :eastwood/ancestors peek)]
    (and (= :if (-> parent-ast :op))
         (#{'clojure.core/cond}
          (-> ast :raw-forms first util/fqsym-of-raw-form)))))

(defn suspicious-macro-invocations [{:keys [asts]} opt]
  (let [selected-macro-invoke-asts
        (->> asts
             (mapcat ast/nodes)
             (filter (fn [ast]
                       (some #(raw-form-of-interest? % core-macros-that-do-little)
                             (:raw-forms ast)))))]
    (for [ast selected-macro-invoke-asts
          raw-form (filter #(raw-form-of-interest? % core-macros-that-do-little)
                           (:raw-forms ast))
          :let [macro-sym (util/fqsym-of-raw-form raw-form)
                loc (or (pass/has-code-loc? (-> ast :raw-forms first meta))
                        (pass/code-loc (pass/nearest-ast-with-loc ast)))
                num-args (dec (count raw-form))
                suspicious-args (get core-macros-that-do-little macro-sym)
                info (get suspicious-args num-args)]
          :when (and (contains? suspicious-args num-args)
                     ;; Avoid warning in some special cases of macros
                     ;; that contain suspicious-looking macros in
                     ;; their expansions.
                     (not (and (#{'clojure.core/and 'clojure.core/or} macro-sym)
                               (and-or-self-expansion? ast)))
                     (not (and (#{'clojure.core/cond} macro-sym)
                               (cond-self-expansion? ast)))
                     (not (util/inside-fieldless-defrecord ast)))
          ;; Debugging code useful to enable when getting warnings
          ;; generated by this function, and trying to figure out how
          ;; to disable them in a precise fashion.
;;          :let [_
;;                (do
;;                  (println "\n\njafinger-dbg: raw-form=")
;;                  (util/pprint-form raw-form)
;;                  (println (format "  ancestor ops=%s  in-fieldless-defrecord?=%s"
;;                                   (seq (map :op (:eastwood/ancestors ast)))
;;                                   (boolean (util/inside-fieldless-defrecord ast))
;;                                   ))
;;;;                  (println "  parent ast=")
;;;;                  (util/pprint-ast-node (util/nth-last (-> ast :eastwood/ancestors) 1))
;;                  )]
          :let [w {:loc loc
                   :linter :suspicious-expression
                   :suspicious-expression {:kind :macro-invocation
                                           :ast ast
                                           :macro-symbol macro-sym}
                   :msg (format "%s called with %d args.  (%s%s) always returns %s.  Perhaps there are misplaced parentheses?"
                                (name macro-sym) num-args (name macro-sym)
                                (if (> num-args 0)
                                  (str " " (str/join " " (:args info)))
                                  "")
                                (if (= "" (:ret-val info))
                                  "\"\""
                                  (print-str (:ret-val info))))}
                allow? (util/allow-warning w opt)]
          :when allow?]
      (do
        (util/debug-warning w ast opt #{:enclosing-macros})
        w))))

;; Note: Looking for asts that contain :invoke nodes for the function
;; 'clojure.core/= will not find expressions like (clojure.test/is (=
;; (+ 1 1))), because the is macro changes that to an apply on
;; function = with one arg, which is a sequence of expressions.  Such
;; expressions are looked for by the function suspicious-is-try-expr.

(def core-fns-that-do-little
  {'clojure.core/*        '{0 {:args []  :ret-val 1},   ; inline
                            1 {:args [x] :ret-val x}}
   'clojure.core/*'       '{0 {:args []  :ret-val 1},   ; inline
                            1 {:args [x] :ret-val x}}
   'clojure.core/+        '{0 {:args []  :ret-val 0},   ; inline
                            1 {:args [x] :ret-val x}}
   'clojure.core/+'       '{0 {:args []  :ret-val 0},   ; inline
                            1 {:args [x] :ret-val x}}
   ;; (- x) (-' x) and (/ x) do something useful
   'clojure.core/<        '{1 {:args [x] :ret-val true}}
   'clojure.core/<=       '{1 {:args [x] :ret-val true}}
   'clojure.core/=        '{1 {:args [x] :ret-val true}}
   'clojure.core/==       '{1 {:args [x] :ret-val true}}
   'clojure.core/>        '{1 {:args [x] :ret-val true}}
   'clojure.core/>=       '{1 {:args [x] :ret-val true}}
   'clojure.core/await    '{0 {:args [] :ret-val nil}}

   'clojure.core/not=     '{1 {:args [x] :ret-val false}}
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
   'clojure.core/partial  '{1 {:args [f] :ret-val f}}})

(defn suspicious-fn-calls [{:keys [asts]} opt]
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
            {:loc loc
             :linter :suspicious-expression,
             :msg (format "%s called with %d args.  (%s%s) always returns %s.  Perhaps there are misplaced parentheses?"
                          fn-sym num-args fn-sym
                          (if (> num-args 0)
                            (str " " (str/join " " (:args info)))
                            "")
                          (if (= "" (:ret-val info))
                            "\"\""
                            (print-str (:ret-val info))))})))))))

;; This code may get a bit hackish, trying to recognize exactly the
;; macroexpansion of clojure.test/try-expr, in particular the case
;; where it is of the form (fn args ...), rather than some other
;; expression like a macro invocation, which already seems to be
;; handled well by suspicious-macro-invocations.

(defn suspicious-is-try-expr [{:keys [asts]} opt]
  (let [try-expr-asts
        (->> asts
             (mapcat ast/nodes)
             (filter (fn [ast]
                       (and (= :try (:op ast))
                            (= 'clojure.test/try-expr
                               (-> ast :raw-forms last first))))))]
    (for [ast try-expr-asts
          :let [raw-form (-> ast :raw-forms last)
                let-bindings-ast (-> ast
                                     :body ; in :op :try
                                     :ret  ; in :op :do
                                     :bindings ; in :op :let
                                     )
                fn-args-ast-vec (-> let-bindings-ast
                                    first  ; in vector of 2 bindings
                                    :init  ; in :op :binding
                                    :args  ; in :op :invoke for clojure.core/list
                                    )
                num-args (some-> fn-args-ast-vec count)
                fn-var (-> let-bindings-ast
                           second  ; in vector of 2 bindings
                           :init   ; in :op :binding
                           :args   ; in :op :invoke for clojure.core/apply
                           first   ; in vector of 2 args to apply
                           :var    ; in :op :var
                           )
                fn-sym (util/var-to-fqsym fn-var)
                loc (-> raw-form last meta)
                suspicious-args (core-fns-that-do-little fn-sym)
                info (get suspicious-args num-args)
;;                _ (do
;;                    (println (format "try-expr-dbg: last-arg=%s class(es)=%s"
;;                                     (last raw-form)
;;                                     (if (sequential? (last raw-form))
;;                                       (seq (map class (last raw-form)))
;;                                       (class raw-form))))
;;                    (println (format "    form="))
;;                    (util/pprint-form (-> ast :form))
;;                    (println (format "    (-> ast :body ... :init)="))
;;                    (util/pprint-ast-node (-> let-bindings-ast
;;                                              first   ; in vector of 2 bindings
;;                                              :init  ; in :op :binding
;;                                              ))
;;                    (println (format "    fqsym=%s (%s)  num-args=%s args=%s"
;;                                     fn-sym (class fn-sym)
;;                                     num-args
;;                                     (seq (map :form fn-args-ast-vec))))
;;                    (println (format "    suspicious-args=%s"
;;                                     suspicious-args))
;;                    (println (format "    loc=%s" loc))
;;                    )
                ]
          :when (and num-args
                     (contains? suspicious-args num-args))]
      {:loc loc
       :linter :suspicious-expression
       :msg (format "%s called with %d args.  (%s%s) always returns %s.  Perhaps there are misplaced parentheses?"
                    (name fn-sym) num-args (name fn-sym)
                    (if (> num-args 0)
                      (str " " (str/join " " (:args info)))
                      "")
                    (if (= "" (:ret-val info))
                      "\"\""
                      (print-str (:ret-val info))))})))

(defn suspicious-expression [& args]
  (concat
   (apply suspicious-macro-invocations args)
   (apply suspicious-fn-calls args)
   (apply suspicious-is-try-expr args)))

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

        (= :local (:op ast))
        (let [local-sym (:form ast)
              bound-val-binding (-> ast :env :locals local-sym)
              bound-val-ast (if (and (= :binding (:op bound-val-binding))
                                     ;; loop and fn arg bindings can
                                     ;; never be constants.  Only
                                     ;; examine let bindings, which
                                     ;; might be constants.
                                     (= :let (:local bound-val-binding)))
                              (:init bound-val-binding))]
;;          (println (format "Found ast op=:local with local-sym=%s val-form=%s line=%s"
;;                           local-sym (:form bound-val-ast)
;;                           (-> bound-val-ast :form meta :line)))
          (if bound-val-ast
            (constant-ast bound-val-ast)))

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

(defn assert-false-expansion? [ast]
  (and (= :if (:op ast))
       (seq (:raw-forms ast))
;;       (do
;;         (if-let [x (-> ast :raw-forms first util/fqsym-of-raw-form)]
;;           (println (format "jafinger-dbg: x=%s %s %s"
;;                            x (= 'clojure.core/assert x)
;;                            (-> ast :raw-forms first))))
;;         true)
       (= 'clojure.core/assert (-> ast :raw-forms
                                   first util/fqsym-of-raw-form))
       (= :const (-> ast :test :op))
       (contains? #{false nil} (-> ast :test :val))))

(defn seq-call-from-destructuring?
  "Does `form` contain a `seq` call that was auto-generated from a `let` destructuring macroexpansion?"
  [form]
  (boolean
   (and (sequential? form)
        (let [[_ seq?-call create-call map-sym] form]
          (and (sequential? seq?-call)
               (sequential? create-call)
               (symbol? map-sym)
               (-> map-sym name (.startsWith "map__"))
               (let [[s? m] seq?-call
                     [create-sym seq-call] create-call]
                 (and (#{'clojure.core/seq?} s?)
                      (-> m name (.startsWith "map__"))
                      (#{'clojure.lang.PersistentHashMap/create} create-sym)
                      (sequential? seq-call)
                      (let [[s-call m] seq-call]
                        (and (#{'clojure.core/seq} s-call)
                             (-> m name (.startsWith "map__")))))))))))

(defn if-with-predictable-test [ast]
  (when (and (= :if (:op ast))
             (not (assert-false-expansion? ast))
             (not (-> ast :form seq-call-from-destructuring?)))
    (or (constant-ast (-> ast :test))
        (logical-true-test (-> ast :test)))))

(defn transform-ns-keyword [kw]
  (if (and (keyword? kw) (some? (namespace kw)))
    (keyword (name kw))
    kw))

(defn default-case-at-end-of-cond? [ast]
  ;; I have seen true and :default used in several projects rather
  ;; than :else
  (and (#{:else :default :otherwise true} (transform-ns-keyword
                                           (-> ast :test :form)))
       (seq (:raw-forms ast))
       (= 'clojure.core/cond
          (-> ast :raw-forms first util/fqsym-of-raw-form))))

(defn constant-test [{:keys [asts]} opt]
  (let [const-tests (->> asts
                         (mapcat ast/nodes)
                         (keep #(if (default-case-at-end-of-cond? %)
                                  nil
                                  (if-let [x (if-with-predictable-test %)]
                                    [% x]))))]
    (for [[ast constant-test-ast] const-tests
          :let [test-form (-> constant-test-ast :form)
                form (-> ast :form)
                loc (or (pass/has-code-loc? (-> ast :form meta))
                        (pass/code-loc (pass/nearest-ast-with-loc ast)))
                w {:loc loc
                   :linter :constant-test
                   :qualifier (-> ast :form second)
                   :constant-test {:kind :the-only-kind
                                   :ast ast}
                   :msg (format "Test expression is always logical true or always logical false: %s in form %s"
                                (pr-str test-form) (pr-str form))}
                allow? (util/allow-warning w opt)]
          :when allow?]
      (do
        (util/debug-warning w ast opt #{:enclosing-macros})
        w))))

(defn fn-form-with-pre-post [form ast]
;;  (println "dbg fn-form-with-pre-post:")
;;  (pp/pprint form)
;;  (println "  sequential?" (sequential? form))
;;  (println "  class" (class form))
;;  (println "  fn?" (#{'clojure.core/fn 'fn} (first form)))
  (if (and (sequential? form)
           (not (vector? form))  ; TBD: Is this needed for anything?
           (#{'clojure.core/fn 'fn} (first form)))
    (let [name (if (symbol? (second form)) (second form))
          sigs (if name (nnext form) (next form))
          sigs (if (vector? (first sigs))
                 (list sigs)
                 sigs)
          psig (fn [sig]
                 (let [[params & body] sig
                       conds (when (and (next body) (map? (first body)))
                               (first body))
                       conds (or conds (meta params))
                       pre (:pre conds)
                       post (:post conds)]
                   (if (or pre post)
                     (merge {:form form, :name name, :params params, :ast ast}
                            (if pre {:pre pre})
                            (if post {:post post})))))]
      (->> sigs
           (map psig)
           ;;(map (fn [s] (println "dbg" (:form s)) s))
           (map-indexed (fn [idx m] (if m (assoc m :method-num idx))))
           (remove nil?)))))

(defn fn-ast-with-pre-post [ast]
  ;; Starting in Clojure 1.8.0, the :op :fn AST is usually not the one
  ;; to have the macroexpansion from clojure.core/fn to
  ;; clojure.core/fn*.  Instead it is its parent AST, which
  ;; has :op :with-meta.  fn-form-with-pre-post should have all the
  ;; needed checks on the node to determine whether a
  ;; particular :op :with-meta AST node contains preconditions or not.
  (when (#{:fn :with-meta} (:op ast))
;;    (println (format "dbg6: Found :op %s node with :raw-forms" (:op ast)))
;;    (pp/pprint (:raw-forms ast))
;;    (println (format "     Cleaned ast:"))
;;    (util/pprint-ast-node ast)
;;    (println (format "     Found pre-postconditions:"))
;;    (pp/pprint (mapcat #(map (juxt :pre :post) (fn-form-with-pre-post % ast))
;;                       (:raw-forms ast)))
    (seq (mapcat #(fn-form-with-pre-post % ast)
                 (:raw-forms ast)))))

(defn get-do-body-from-fn-or-with-meta-ast [fn-or-with-meta-ast method-num]
  (case (:op fn-or-with-meta-ast)
    :fn (util/get-in-ast fn-or-with-meta-ast
                         [[[:methods method-num] :fn-method]
                          [[:body] :do]])
    :with-meta (util/get-in-ast fn-or-with-meta-ast
                                [[[:expr] :fn]
                                 [[:methods method-num] :fn-method]
                                 [[:body] :do]])))

(defn ast-of-condition-test
  [kind fn-ast method-num condition-idx condition-form]
  (let [{do-body-ast :ast, s :stop-reason}
        (get-do-body-from-fn-or-with-meta-ast fn-ast method-num)]
;;    (println (format "dbg ast-of-condition-test s=%s ast:" s))
;;    (util/pprint-ast-node do-body-ast)
    (assert (nil? s))
    (let [matching-assert-asts
          (filter (fn [ast]
;;                    (when (:raw-forms ast)
;;                      (println (format "dbg5: Looking for raw-forms containing assert: %s"
;;                                       (pr-str (:raw-forms ast)))))
                    (some (fn [f]
;;                            (println (format "dbg7: assert?=%s matches?=%s condition-form=%s (second f)=%s"
;;                                             (#{'assert 'clojure.core/assert} (first f))
;;                                             (= condition-form (second f))
;;                                             condition-form
;;                                             (second f)))
                            (and (sequential? f)
                                 (#{'assert 'clojure.core/assert}
                                  (first f))
                                 (= condition-form
                                    (second f))))
                          (:raw-forms ast)))
                  (ast/nodes do-body-ast))]
      ;; Don't return more than one, as we might issue incorrect
      ;; warnings.  Hopefully it will be rare that we find multiple
      ;; matching ASTs.
      (if (= 1 (count matching-assert-asts))
        (:test (first matching-assert-asts))
        (println (format "dbg wrong-pre-post: found %d ASTs with :raw-forms containing assert of condition %s"
                         (count matching-assert-asts)
                         (pr-str condition-form)))))))

(defn wrong-pre-post-messages [opt kind conditions method-num ast
                               condition-desc-begin condition-desc-middle]
;;  (println (format "dbg wrong-pre-post-messages: kind=%s method-num=%s conditions=%s (vector? conditions)=%s (class conditions)=%s"
;;                   kind method-num conditions
;;                   (vector? conditions)
;;                   (class conditions)))
  (if (not (vector? conditions))
    [(format "All function %s should be in a vector.  Found: %s"
             condition-desc-middle (pr-str conditions))]

    (remove nil?
            (for [[condition test-ast]
                  (map-indexed (fn [i condition]
                                 [condition
                                  (ast-of-condition-test kind ast method-num i
                                                         condition)])
                               conditions)]
;;       (do
;;         (println (format "dbg3: kind=%s line=%d :op=%s condition=%s :form=%s same?=%s"
;;                          kind (:line (meta conditions))
;;                          (:op test-ast)
;;                          condition (:form test-ast)
;;                          (= condition (:form test-ast))))
              (cond
                (= :const (:op test-ast))
                (format "%s found that is always logical true or always logical false.  Should be changed to function call?  %s"
                        condition-desc-begin (pr-str condition))

                (and (-> test-ast :op #{:var})
                     (-> test-ast :var meta :dynamic not))
                (format "%s found that is probably always logical true or always logical false.  Should be changed to function call?  %s"
                        condition-desc-begin (pr-str condition))

        ;; In this case, probably the developer wanted to assert that
        ;; a function arg was logical true, i.e. neither nil nor
        ;; false.
                (= :local (:op test-ast))
                nil

        ;; The following kinds of things are 'complex' enough that we
        ;; will not try to do any fancy calculation to determine
        ;; whether their results are constant or not.
                (#{:invoke :static-call :let :if :instance? :keyword-invoke} (:op test-ast))
                nil

                :else
                (when (util/debug? :forms opt)
                  (println (format "dbg wrong-pre-post: condition=%s line=%d test-ast :op=%s"
                                   condition (:line (meta conditions))
                                   (:op test-ast)))))))))
;;)

(defn wrong-pre-post [{:keys [asts]} opt]
  (let [fns-with-pre-post (->> asts
                               (mapcat ast/nodes)
                               (mapcat fn-ast-with-pre-post))]
    (concat
     (for [{:keys [ast form name pre method-num]} fns-with-pre-post
           :when pre
           :let [loc (meta pre)
                 msgs (wrong-pre-post-messages opt
                                               :precondition pre
                                               method-num ast
                                               "Precondition"
                                               "preconditions")]
           msg msgs
           :let [w {:loc loc :linter :wrong-pre-post
                    :wrong-pre-post {:kind :pre, :ast ast}
                    :msg msg}]]
       w)
     (for [{:keys [ast form name post method-num]} fns-with-pre-post
           :when post
           :let [loc (meta post)
                 msgs (wrong-pre-post-messages opt
                                               :postcondition post
                                               method-num ast
                                               "Postcondition"
                                               "postconditions")]
           msg msgs
           :let [w {:loc loc
                    :linter :wrong-pre-post
                    :wrong-pre-post {:kind :post, :ast ast}
                    :msg msg}]]
       w))))

(defn arg-vecs-of-fn-raw-form [fn-raw-form]
;;  (println "fn-raw-form -> " fn-raw-form)
;;  (flush)
  (if (and (sequential? fn-raw-form)
           (#{'fn 'clojure.core/fn} (first fn-raw-form)))
    (let [fn-bodies (rest fn-raw-form)
          ;; remove symbol fn name if present
          fn-bodies (if (symbol? (first fn-bodies))
                      (rest fn-bodies)
                      fn-bodies)
          ;; If there is only one fn body, put it in a list so it is
          ;; more uniform with the case where there are multiple fn
          ;; bodies.
          fn-bodies (if (vector? (first fn-bodies))
                      (list fn-bodies)
                      fn-bodies)]
;;      (do
;;        (println "   fn-bodies -> " fn-bodies)
;;        (println "   (map first fn-bodies) -> " (map first fn-bodies))
;;        (flush)
      (map first fn-bodies)
;;        )
      )))

(defn arg-vecs-of-ast [ast]
  (if (and (contains? ast :raw-forms)
           (sequential? (:raw-forms ast)))
    (mapcat arg-vecs-of-fn-raw-form (:raw-forms ast))))

;; Copied a few functions from Clojure 1.9.0, and renamed with 'my-'
;; prefix.  That enables running this code with earlier Clojure
;; versions, and not have name conflicts when running with Clojure
;; 1.9.0.

(defn my-ident? [x]
  (or (keyword? x) (symbol? x)))

(defn my-simple-symbol? [x]
  (and (symbol? x) (nil? (namespace x))))

(defn my-qualified-keyword? [x]
  (boolean (and (keyword? x) (namespace x) true)))

;; Adapted from core.spec.alpha spec ::local-name

(defn my-local-name? [x]
  (and (my-simple-symbol? x) (not= '& x)))

(def non-matching-info {:result false :local-names []})
(declare binding-form-info)

;; x might be a symbol with a namespace, or without a namespace.
;; x might also be a keyword with or without a namespace.

;; In all cases, return a simple symbol, the one that will be locally
;; bound a value when x is used in a vector after :keys during
;; associative/map destructuring.

(defn symbol-or-kw-to-local-name [x]
  (symbol nil (name x)))

(defn better-loc [cur-loc maybe-better-loc-in-meta-of-this-obj]
  (let [m (meta maybe-better-loc-in-meta-of-this-obj)]
    (if (util/contains-loc-info? m)
      m
      cur-loc)))

(defn local-name-with-loc [x loc]
  {:source-name x
   :local-name (symbol-or-kw-to-local-name x)
   :loc (better-loc loc x)})

(defn local-name-info [x loc]
  (if (my-local-name? x)
    {:result true
     :kind :local-name
     :local-names [(local-name-with-loc x loc)]}
    ;; else
    non-matching-info))

;; Adapted from core.spec.alpha spec ::seq-binding-form

(defn seq-binding-form-info [x loc top-level-arg-vector?]
  (if (vector? x)
    (let [n (count x)
          ;; Do not allow :as in top level arg vector of a fn
          has-as? (and (not top-level-arg-vector?)
                       (>= n 2)
                       (= :as (x (- n 2)))
                       (my-local-name? (x (- n 1))))
          as-form-info (if has-as?
                         (local-name-info (x (- n 1)) loc)
                         {})
          x (if has-as?
              (subvec x 0 (- n 2))
              x)
          n (count x)
          ;; _Do_ allow & in top level arg vector of a fn
          has-amp? (and (>= n 2)
                        (= '& (x (- n 2))))
          amp-form-info (if has-amp?
                          (let [amp-form (x (- n 1))]
                            (binding-form-info amp-form
                                               (better-loc loc amp-form)))
                          {})
          x (if has-amp?
              (subvec x 0 (- n 2))
              x)
          initial-forms (map #(binding-form-info % (better-loc loc %)) x)
          all-good? (every? :result initial-forms)

          all-forms (concat initial-forms [amp-form-info as-form-info])
          local-names (mapcat #(if (:nested-info %)
                                 (-> % :nested-info :local-names)
                                 (-> % :local-names))
                              all-forms)
          warnings (mapcat #(if (:nested-info %)
                              (-> % :nested-info :warnings)
                              (-> % :warnings))
                           all-forms)]
      {:result all-good?
       :kind :seq-binding-form
       :has-amp? has-amp?
       :has-as? has-as?
       :local-names local-names
       :warnings warnings})
    ;; else
    non-matching-info))

(defn map-binding-value-for-keys-syms-strs [v pred loc]
  (if (and (vector? v) (every? pred v))
    {:result true, :local-names (map #(local-name-with-loc % loc) v),
     :kind :map-keys-syms-strs}
    non-matching-info))

(defn one-map-binding-form-info [[k v] loc]
  (cond
    (= k :keys) (map-binding-value-for-keys-syms-strs v my-ident? loc)
    (= k :syms) (map-binding-value-for-keys-syms-strs v symbol? loc)
    (= k :strs) (map-binding-value-for-keys-syms-strs v my-simple-symbol? loc)
    (and (my-qualified-keyword? k)
         (-> k name #{"keys" "syms"})) (map-binding-value-for-keys-syms-strs
                                        v my-simple-symbol? loc)

    (= k :or) (if (and (map? v) (every? my-simple-symbol? (keys v)))
                {:result true, :or-map v, :kind :map-or}
                non-matching-info)

    (= k :as) (if (my-local-name? v)
                {:result true, :as-name v, :kind :map-as}
                non-matching-info)

    (my-local-name? k) {:result true,
                        :local-names [(local-name-with-loc k loc)],
                        :kind :map-bind-local-name}

    :else (let [info (binding-form-info k (better-loc loc k))]
            (if (:result info)
              ;; Return a value with the original info on
              ;; key :nested-info, to signal to the caller that any
              ;; local names or other data is inside of a nested
              ;; destructuring form, either sequential or
              ;; associative/map, since some Eastwood warnings need
              ;; this distinction.
              {:result (:result info), :nested-info info,
               :kind :map-sub-destructure}
              info))))

(defn map-binding-form-info [x loc]
  (let [ret

        (if (map? x)
          (let [infos (map #(one-map-binding-form-info % (better-loc loc %)) x)]
            (if (every? :result infos)
        ;; Check for warnings to issue for this one associative/map
        ;; destructuring form, independent of any destructuring forms
        ;; that might be nested within it, or what this destructuring
        ;; form might be nested within.
              (let [{:keys [map-bind-local-name map-keys-syms-strs
                            map-sub-destructure map-or map-as]}
                    (group-by :kind infos)
                    as-local-name (if map-as (-> map-as first :as-name))
                    or-names (if map-or
                               (-> map-or first :or-map keys)
                               [])
                    or-names-set (set or-names)
                    bound-local-names (->> (concat map-bind-local-name
                                                   map-keys-syms-strs)
                                           (mapcat :local-names)
                                           (map :local-name))
                    as-or-warning
                    (if (and as-local-name
                             (contains? or-names-set as-local-name))
                      [{:loc (better-loc loc as-local-name)
                        :linter :unused-or-default
                        :unused-or-default {}
                        :msg (format "Name %s after :as is also in :or map of associative destructuring.  The default value in the :or will never be used."
                                     as-local-name)}])

              ;; Warn about any keys in an :or map that are not
              ;; elsewhere in the _same_ map binding form's map.  For
              ;; this purpose, ignore any symbol associated with
              ;; the :as key.
;              _ (do
;                  (println "dbg or-names-set " or-names-set)
;                  (println "    bound-local-names " bound-local-names)
;                  (println "    as-local-name " as-local-name)
;                  (flush))
                    unused-or-names (set/difference or-names-set
                                                    (conj (set bound-local-names)
                                                          as-local-name))
                    unused-or-name-warnings
                    (map (fn [unused-or-name]
                           {:loc (better-loc loc unused-or-name)
                            :linter :unused-or-default
                            :unused-or-default {}
                            :msg (format "Name %s with default value in :or map of associative destructuring does not appear elsewhere in that same destructuring expression.  The default value in the :or will never be used."
                                         unused-or-name)})
                         unused-or-names)
                    sub-infos (map :nested-info map-sub-destructure)]
                {:result true,
                 :local-names (concat (mapcat :local-names infos)
                                      (mapcat :local-names sub-infos)
                                      (if as-local-name
                                        [(local-name-with-loc as-local-name loc)]
                                        [])),
                 :warnings (concat as-or-warning unused-or-name-warnings
                                   (mapcat :warnings sub-infos))
                 :kind :map-binding-form})
        ;; else
              non-matching-info))
    ;; else
          non-matching-info)]
;;    (println "dbg map-binding-form-info x " x)
;;    (println "     ret " ret)
;;    (flush)

    ret))

(defn binding-form-info [x loc]
  (let [info (local-name-info x loc)]
    (if (:result info)
      info
      (let [info (seq-binding-form-info x (better-loc loc x) false)]
        (if (:result info)
          info
          (map-binding-form-info x (better-loc loc x)))))))

(defn dont-warn-for-symbol?
  "Return logical true for symbols in arg vectors that should never be
warned about as duplicates.

By convention, _ is a parameter intended to be ignored, and often
occurs multiple times in the same arg vector when it is used.  Also do
not warn about any other symbols that begin with _.  This gives
Eastwood users a way to selectively disable such warnings if they
wish."
  [sym]
  (.startsWith (name sym) "_"))

(defn duplicate-local-names [symbols]
  (->> symbols
       (group-by :local-name)
       (util/filter-vals #(> (count %) 1))
       vals
       ;; Prefer to keep the second symbol among any duplicates, so
       ;; that we get the line/column metadata for that one instead of
       ;; the first.
       (map second)))

(defn duplicate-params [{:keys [asts]} opt]
  (let [arg-vecs (->> asts
                      (mapcat ast/nodes)
                      (mapcat arg-vecs-of-ast))]
    (doall
     (apply concat
            (for [arg-vec arg-vecs
                  :let [loc (meta arg-vec)]]
              (let [{:keys [result local-names warnings] :as info}
                    (seq-binding-form-info arg-vec loc true)]
                (if result
                  (let [;                 _ (do
;                     (println "dbg arg-vec " arg-vec)
;                     (println "dbg (type local-names):" (type local-names))
;                     (pp/pprint local-names)
;                     (flush))
                        dups (->> (duplicate-local-names local-names)
                                  (remove #(dont-warn-for-symbol? (:local-name %))))]
                    (concat
                     (map (fn [dup]
                            {:loc (:loc dup)
                             :linter :duplicate-params
                             :duplicate-params {}
                             :msg (if (= (:source-name dup) (:local-name dup))
                                    (format "Local name `%s` occurs multiple times in the same argument vector"
                                            (:source-name dup))
                                    (format "Local name `%s` (part of full name `%s`) occurs multiple times in the same argument vector"
                                            (:local-name dup) (:source-name dup)))})
                          dups)
                     warnings))
           ;; else
                  [{:loc loc
                    :linter :duplicate-params
                    :duplicate-params {}
                    :msg (format "Unrecognized argument vector syntax %s" arg-vec)}])))))))
