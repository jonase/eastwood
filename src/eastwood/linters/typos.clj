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
           first-invoke-do-report-ast (->>
                                       (ast/nodes ast)
                                       (filter #(and
                                                 (= :invoke (:op %))
                                                 (= 'clojure.test/do-report
                                                    (-> % :fn :var
                                                        util/var-to-fqsym))))
                                       first)
           const-or-map-ast (get-in first-invoke-do-report-ast [:args 0])
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
        [(util/add-loc-info is-loc
          {:linter :suspicious-test,
           :msg (format "'is' form has string as first arg.  This will always pass.  If you meant to have a message arg to 'is', it should be the second arg, after the expression to test")})]
        
        (and (constant-expr-logical-true? is-arg1)
             (not (list? is-arg1)))
        [(util/add-loc-info is-loc
          {:linter :suspicious-test,
           :msg (format "'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test")})]
        
        (and (= n 2)
             (not= message-tag java.lang.String))
        [(util/add-loc-info is-loc
          {:linter :suspicious-test,
           :msg (format "'is' form has non-string as second arg (inferred type is %s).  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a string during test time, and you intended this, then ignore this warning."
                        message-tag)})]
        
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
                                       (util/fqsym-of-raw-form (:raw-form %)))
                                   formasts)))))
        
        ;; To find deftest subexpressions, first filter all of the raw
        ;; forms for those with a resolved-op symbol equal to
        ;; clojure.test/deftest, then get of the first 2 symbols from
        ;; each, which are the deftest and the Var name following
        ;; deftest.
        pr-deftest-subexprs
        (->> pr-formasts
             (filter #(= 'clojure.test/deftest
                         (util/fqsym-of-raw-form (:raw-form %))))
             (mapcat (fn [formast]
                       (for [subexpr (nthnext (:raw-form formast) 2)]
                         (assoc formast :subexpr subexpr)))))
        
        ;; Similarly for testing subexprs as for deftest subexprs.
        ;; TBD: Make a helper function to eliminate the nearly
        ;; duplicated code between deftest and testing.
        pr-testing-subexprs
        (->> pr-formasts
             (filter #(= 'clojure.test/testing
                         (util/fqsym-of-raw-form (:raw-form %))))
             (mapcat (fn [formast]
                       (for [subexpr (nthnext (:raw-form formast) 2)]
                         (assoc formast :subexpr subexpr)))))

        pr-is-formasts pr-first-is-formasts]
    (concat (suspicious-is-forms pr-is-formasts)
            (predicate-forms pr-deftest-subexprs 'deftest)
            (predicate-forms pr-testing-subexprs 'testing))))

;; Suspicious macro invocations.  Any macros in clojure.core that can
;; have 'trivial' expansions are included here, if it can be
;; determined solely by the number of arguments to the macro.

(def core-macros-that-do-little
  '{
    ;; (-> x) expands to x
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
    clojure.core/with-redefs {1 {:args [bindings] :ret-val nil}}
    })

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


(defn safe-first [x]
  (try
    (first x)
    (catch Exception e
      nil)))


(defn raw-form-of-interest? [raw-form core-macros-that-do-little]
  (get core-macros-that-do-little (util/fqsym-of-raw-form raw-form)))


(defn and-or-self-expansion-old? [ast]
  (let [parent-ast (-> ast :eastwood/ancestors peek)]
    (and (= :if (-> parent-ast :op))
         (= :local (-> parent-ast :test :op))
         (#{'clojure.core/and 'clojure.core/or}
          (-> ast :eastwood/partly-resolved-forms first first)))))


(defn and-or-self-expansion-new? [ast]
  (let [parent-ast (-> ast :eastwood/ancestors peek)]
    (and (= :if (-> parent-ast :op))
         (= :local (-> parent-ast :test :op))
         (#{'clojure.core/and 'clojure.core/or}
          (-> ast :raw-forms first util/fqsym-of-raw-form)))))


(defn and-or-self-expansion? [ast]
  (let [old (and-or-self-expansion-old? ast)
        new (and-or-self-expansion-new? ast)]
    (when (not= old new)
      (println (format "dbg and-or-self-expansion?: old=%s != new=%s"
                       old new))
      (-> ast util/clean-ast util/pprint-ast-node))
    new))


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
          :let [w (util/add-loc-info
                   loc
                   {:linter :suspicious-expression
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
                                   (print-str (:ret-val info))))})
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
  {
   'clojure.core/*        '{0 {:args []  :ret-val 1},   ; inline
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
   'clojure.core/partial  '{1 {:args [f] :ret-val f}}
   })

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
                num-args (count fn-args-ast-vec)
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
          :when (contains? suspicious-args num-args)]
      (util/add-loc-info
       loc
       {:linter :suspicious-expression
        :msg (format "%s called with %d args.  (%s%s) always returns %s.  Perhaps there are misplaced parentheses?"
                     (name fn-sym) num-args (name fn-sym)
                     (if (> num-args 0)
                       (str " " (str/join " " (:args info)))
                       "")
                     (if (= "" (:ret-val info))
                       "\"\""
                       (print-str (:ret-val info))))}))))


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


(defn if-with-predictable-test [ast]
  (if (and (= :if (:op ast))
           (not (assert-false-expansion? ast)))
    (or (constant-ast (-> ast :test))
        (logical-true-test (-> ast :test)))))


(defn default-case-at-end-of-cond? [ast]
  ;; I have seen true and :default used in several projects rather
  ;; than :else
  (and (#{:else :default true} (-> ast :test :form))
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
                test-form-to-print (if (nil? test-form) "nil" test-form)
                form (-> ast :form)
                form-to-print (if (nil? form) "nil" form)
                loc (or (pass/has-code-loc? (-> ast :form meta))
                        (pass/code-loc (pass/nearest-ast-with-loc ast)))
                w (util/add-loc-info
                   loc
                   {:linter :constant-test
                    :constant-test {:kind :the-only-kind
                                    :ast ast}
                    :msg (format "Test expression is always logical true or always logical false: %s in form %s"
                                 test-form-to-print form-to-print)})
                allow? (util/allow-warning w opt)]
          :when allow?]
      (do
        (util/debug-warning w ast opt #{:enclosing-macros})
        w))))
