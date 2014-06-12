(ns eastwood.linters.typos
  (:require [clojure.pprint :as pp])
  (:require [eastwood.util :as util]
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
                   util/replace-comments-with-nil
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
        [{:linter :suspicious-test,
          :msg (format "'is' form has string as first arg.  This will always pass.  If you meant to have a message arg to 'is', it should be the second arg, after the expression to test")
          :file (:file is-loc)
          :line (:line is-loc)
          :column (:column is-loc)}]
        
        (and (constant-expr-logical-true? is-arg1)
             (not (list? is-arg1)))
        [{:linter :suspicious-test,
          :msg (format "'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test")
          :file (:file is-loc)
          :line (:line is-loc)
          :column (:column is-loc)}]
        
        (and (= n 2)
             (let [arg2 (second is-args)]
               (not (or (string? arg2)
                        (and (sequential? arg2)
                             (fn-call-returns-string? (first arg2)))))))
        [{:linter :suspicious-test,
          :msg (format "'is' form has non-string as second arg.  The second arg is an optional message to print if the test fails, not a test expression, and will never cause your test to fail unless it throws an exception.  If the second arg is an expression that evaluates to a string during test time, and you intended this, then ignore this warning.")
          :file (:file is-loc)
          :line (:line is-loc)
          :column (:column is-loc)}]
        
        (and thrown? (util/regex? thrown-arg2))
        [{:linter :suspicious-test,
          :msg (format "(is (thrown? ...)) form has second thrown? arg that is a regex.  This regex is ignored.  Did you mean to use thrown-with-msg? instead of thrown?")
          :file (:file is-loc)
          :line (:line is-loc)
          :column (:column is-loc)}]
        
        (and thrown? (string? thrown-arg2))
        [{:linter :suspicious-test,
          :msg (format "(is (thrown? ...)) form has second thrown? arg that is a string.  This string is ignored.  Did you mean to use thrown-with-msg? instead of thrown?, and a regex instead of the string?")
          :file (:file is-loc)
          :line (:line is-loc)
          :column (:column is-loc)}]
        
        (and thrown? (some string? thrown-args))
        [{:linter :suspicious-test,
          :msg (format "(is (thrown? ...)) form has a string inside (thrown? ...).  This string is ignored.  Did you mean it to be a message shown if the test fails, like (is (thrown? ...) \"message\")?")
          :file (:file is-loc)
          :line (:line is-loc)
          :column (:column is-loc)}]
        
        :else nil)))))

(def ^:dynamic *var-info-map* nil)

(defn predicate-forms [forms form-type]
  (apply
   concat
   (for [f forms]
     (cond
      (and (not (list? f))
           (constant-expr? f))
      [(let [file (-> f meta :file)
             line (-> f meta :line)
             column (-> f meta :column)]
         {:linter :suspicious-test,
          :msg (format "Found constant form%s with class %s inside %s.  Did you intend to compare its value to something else inside of an 'is' expresssion?"
                       (cond line ""
                             (string? f) (str " \"" f "\"")
                             :else (str " " f))
                       (if f (.getName (class f)) "nil") form-type)
          :file file
          :line line
          :column column})]
      
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
           :file (-> ff meta :file)
           :line (-> ff meta :line)
           :column (-> ff meta :column)}]
         
         (and var-info (get var-info :pure-fn))
         [{:linter :suspicious-test,
           :msg (format "Found (%s ...) form inside %s.  This is a pure function with no side effects, and its return value is unused.  Did you intend to compare its return value to something else inside of an 'is' expression?"
                        ff form-type)
           :file (-> ff meta :file)
           :line (-> ff meta :line)
           :column (-> ff meta :column)}]
         
         :else nil))
      :else nil))))

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

(defn suspicious-test [{:keys [forms asts]}]
  (binding [*var-info-map* (edn/read-string (slurp (io/resource "var-info.edn")))]
    (doall
     (let [pr-formasts (for [ast (mapcat ast/nodes asts)
                             [pr-form raw-form]
                             (map list
                                  (:eastwood/partly-resolved-forms ast)
                                  (:raw-forms ast))]
                         {:pr-form pr-form
                          :raw-form raw-form
                          :ast ast})

           pr-first-is-formasts
           (remove nil?
            (for [ast (mapcat ast/nodes asts)]
              (let [formasts (for [[pr-form raw-form]
                                   (map list
                                        (:eastwood/partly-resolved-forms ast)
                                        (:raw-forms ast))]
                               {:pr-form pr-form
                                :raw-form raw-form
                                :ast ast})]
                (first (filter #(= 'clojure.test/is
                                   (first (:pr-form %)))
                               formasts)))))

;;           _ (do
;;               (doseq [pr-formast pr-formasts]
;;                 (clojure.pprint/pprint
;;                  {:pr-form (:pr-form pr-formast)
;;                   :raw-form (:raw-form pr-formast)
;;                   :ast (select-keys (:ast pr-formast)
;;                                     [:op :env :form :raw-forms])})
;;                 (println "----------------------------------------"))
;;               )

           pr-is-formasts pr-first-is-formasts
           pr-deftest-formasts (filter #(= (first (:pr-form %)) 'clojure.test/deftest)
                               pr-formasts)
           pr-testing-formasts (filter #(= (first (:pr-form %)) 'clojure.test/testing)
                               pr-formasts)
;;           _ (println (format "dbx: Found %d ct/is %d ct/deftest %d ct/testing (ct=clojure.test)"
;;                              (count pr-is-formasts)
;;                              (count pr-deftest-formasts)
;;                              (count pr-testing-formasts)))
           pr-is-forms (map :raw-form pr-is-formasts)
           pr-deftest-subexprs (apply concat
                                      (map #(nthnext (:pr-form %) 2) pr-deftest-formasts))
           pr-testing-subexprs (apply concat
                                      (map #(nthnext (:pr-form %) 2) pr-testing-formasts))

;;           _ (do
;;               (binding [*print-meta* true]
;;                 (println (format "dbx: %d pr-is-forms:"
;;                                  (count pr-is-forms)))
;;                 (clojure.pprint/pprint pr-is-forms)
;;                 (println "----------------------------------------")
;;                 (println (format "dbx: %d pr-raw-is-forms:"
;;                                  (count pr-is-forms)))
;;                 (clojure.pprint/pprint (map :raw-form pr-is-formasts))
;;                 (println "----------------------------------------")
;;                 (println "----------------------------------------")

;;                 (println (format "dbx: %d pr-deftest-subexprs:"
;;                                  (count pr-deftest-subexprs)))
;;                 (clojure.pprint/pprint pr-deftest-subexprs)
;;                 (println "----------------------------------------")
;;                 (println "----------------------------------------")
                 
;;                 (println (format "dbx: %d pr-testing-subexprs:"
;;                                  (count pr-testing-subexprs)))
;;                 (clojure.pprint/pprint pr-testing-subexprs)
;;                 (println "----------------------------------------")
;;                 (println "----------------------------------------")
;;                 )
;;               )
           ]
       (concat (suspicious-is-forms pr-is-forms)
               (predicate-forms pr-deftest-subexprs 'deftest)
               (predicate-forms pr-testing-subexprs 'testing))))))

;; Suspicious function calls and macro invocations

(def core-first-vars-that-do-little
  '{
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
                util/replace-comments-with-nil
                (util/subforms-with-first-in-set
                 (set (keys core-first-vars-that-do-little))))]
     (for [f fs]
       (let [fn-sym (first f)
             num-args (dec (count f))
             suspicious-args (get core-first-vars-that-do-little fn-sym)
             info (get suspicious-args num-args)]
         (if (contains? suspicious-args num-args)
           [{:linter :suspicious-expression,
             :msg (format "%s called with %d args.  (%s%s) always returns %s.  Perhaps there are misplaced parentheses?  The number of args may actually be more if it is inside of a macro like -> or ->>"
                          fn-sym num-args fn-sym
                          (if (> num-args 0)
                            (str " " (str/join " " (:args info)))
                            "")
                          (if (= "" (:ret-val info))
                            "\"\""
                            (print-str (:ret-val info))))
             :file (-> fn-sym meta :file)
             :line (-> fn-sym meta :line)
             :column (-> fn-sym meta :column)}]))))))

;; Note: Looking for asts that contain :invoke nodes for the function
;; #'clojure.core/= will not find expressions like (clojure.test/is (=
;; (+ 1 1))), because the is macro changes that to an apply on
;; function = with one arg, which is a sequence of expressions.
;; Finding one-arg = can probably only be done at the source form
;; level.

(def core-fns-that-do-little
  {
   #'clojure.core/=        '{1 {:args [x] :ret-val true}}
   #'clojure.core/==       '{1 {:args [x] :ret-val true}}
   #'clojure.core/not=     '{1 {:args [x] :ret-val false}}
   #'clojure.core/<        '{1 {:args [x] :ret-val true}}
   #'clojure.core/<=       '{1 {:args [x] :ret-val true}}
   #'clojure.core/>        '{1 {:args [x] :ret-val true}}
   #'clojure.core/>=       '{1 {:args [x] :ret-val true}}
   #'clojure.core/min      '{1 {:args [x] :ret-val x}}
   #'clojure.core/max      '{1 {:args [x] :ret-val x}}
   #'clojure.core/min-key  '{2 {:args [f x] :ret-val x}}
   #'clojure.core/max-key  '{2 {:args [f x] :ret-val x}}
   #'clojure.core/dissoc   '{1 {:args [map] :ret-val map}}
   #'clojure.core/disj     '{1 {:args [set] :ret-val set}}
   #'clojure.core/merge    '{0 {:args [] :ret-val nil},
                             1 {:args [map] :ret-val map}}
   #'clojure.core/merge-with '{1 {:args [f] :ret-val nil},
                               2 {:args [f map] :ret-val map}}
   #'clojure.core/interleave '{0 {:args [] :ret-val ()}}
   #'clojure.core/pr-str   '{0 {:args [] :ret-val ""}}
   #'clojure.core/print-str '{0 {:args [] :ret-val ""}}
   #'clojure.core/pr       '{0 {:args [] :ret-val nil}}
   #'clojure.core/print    '{0 {:args [] :ret-val nil}}
   
   #'clojure.core/comp     '{0 {:args [] :ret-val identity}}
   #'clojure.core/partial  '{1 {:args [f] :ret-val f}}
   #'clojure.core/+        '{0 {:args []  :ret-val 0},   ; inline
                             1 {:args [x] :ret-val x}}
   #'clojure.core/+'       '{0 {:args []  :ret-val 0},   ; inline
                             1 {:args [x] :ret-val x}}
   #'clojure.core/*        '{0 {:args []  :ret-val 1},   ; inline
                             1 {:args [x] :ret-val x}}
   #'clojure.core/*'       '{0 {:args []  :ret-val 1},   ; inline
                             1 {:args [x] :ret-val x}}
   ;; Note: (- x) and (/ x) do something useful
   })

(defn suspicious-expression-asts [{:keys [asts]}]
  (let [fn-var-set (set (keys core-fns-that-do-little))
        invoke-asts (->> asts
                         (mapcat ast/nodes)
                         (filter #(and (= (:op %) :invoke)
                                       (let [v (-> % :fn :var)]
                                         (contains? fn-var-set v)))))]
    (doall
     (remove
      nil?
      (for [ast invoke-asts]
        (let [^clojure.lang.Var fn-var (-> ast :fn :var)
              fn-sym (.sym fn-var)
              num-args (count (-> ast :args))
              form (-> ast :form)
              suspicious-args (get core-fns-that-do-little fn-var)
              info (get suspicious-args num-args)]
          (if (contains? suspicious-args num-args)
            {:linter :suspicious-expression,
             :msg (format "%s called with %d args.  (%s%s) always returns %s.  Perhaps there are misplaced parentheses?"
                          fn-sym num-args fn-sym
                          (if (> num-args 0)
                            (str " " (str/join " " (:args info)))
                            "")
                          (if (= "" (:ret-val info))
                            "\"\""
                            (print-str (:ret-val info))))
             :file (-> form meta :file)
             :line (-> form meta :line)
             :column (-> form meta :column)})))))))

(defn suspicious-expression [& args]
  (concat
   (apply suspicious-expression-forms args)
   (apply suspicious-expression-asts args)))
