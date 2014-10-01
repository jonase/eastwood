(ns eastwood.linters.unused
  (:import [java.lang.reflect Method Type])
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [eastwood.copieddeps.dep10.clojure.tools.reader.edn :as edn]
            [clojure.pprint :as pp]
            [eastwood.util :as util]
            [eastwood.passes :as pass]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]))

;; Unused private vars
(defn- private-defs [exprs]
  (->> (mapcat ast/nodes exprs)
       (filter #(and (= :def (:op %))
                     (-> % :var meta :private)
                     (-> % :var meta :macro not))) ;; skip private macros
       (map :var)))

(defn- var-freq [exprs]
  (->> (mapcat ast/nodes exprs)
       (filter #(contains? #{:var :the-var} (:op %)))
       (map :var)
       frequencies))

(defn unused-private-vars [{:keys [asts]}]
  (let [pdefs (private-defs asts)
        vfreq (var-freq asts)]
    (for [pvar pdefs
          :when (nil? (vfreq pvar))]
      {:linter :unused-private-vars
       :msg (format "Private var %s is never used" pvar)
       :file (-> pvar :env :file)
       :line (-> pvar :env :line)
       :column (-> pvar :env :column)})))

;; Unused fn args

(defn- ignore-arg?
  "Return logical true for args that should never be warned about as
unused, such as &form and &env that are 'hidden' args of macros.

By convention, _ is never reported as unused, and neither is any arg
with a name that begins with _.  This gives eastwood users a way to
selectively disable such warnings if they wish."
  [arg]
  (or (contains? #{'&env '&form} arg)
      (.startsWith (name arg) "_")))

(defn- params [fn-method]
  (let [params (:params fn-method)]
    (set (map #(select-keys % [:form :name]) params))))

(defn- used-locals [exprs]
  (set
   (->> exprs
        (filter (util/op= :local))
        (map #(select-keys % [:form :name])))))

(defn- unused-fn-args* [fn-expr]
  (reduce set/union
          (for [method (:methods fn-expr)]
            (let [args (params method)]
              (set/difference args (used-locals (ast/nodes (:body method))))))))

(defn unused-fn-args [{:keys [asts]}]
  (let [fn-exprs (->> asts
                      (map util/enhance-extend-invocations)
                      (mapcat ast/nodes)
                      (filter (util/op= :fn)))]
    (for [expr fn-exprs
          :let [unused (->> (unused-fn-args* expr)
                            (map :form)
                            (remove ignore-arg?)
                            set)]
          unused-sym unused
          :let [loc (-> unused-sym meta)]]
      {:linter :unused-fn-args
       :msg (format "Function arg %s never used" unused-sym)
       :file (-> loc :file)
       :line (-> loc :line)
       :column (-> loc :column)})))


;; Unused namespaces

;; If require is called outside of an ns form, on an argument that is
;; not a constant, the #(-> % :expr :form) step below can introduce
;; nil values into the sequence.  For now, just filter those out to
;; avoid warnings about namespace 'null' not being used.  Think about
;; if there is a better way to handle such expressions.  Examples are
;; in core namespace clojure.main, and contrib library namespaces
;; clojure.tools.namespace.reload and clojure.test.generative.runner.
(defn required-namespaces [exprs]
  (->> (mapcat ast/nodes exprs)
       (filter #(and (= (:op %) :invoke)
                     (let [v (-> % :fn :var)]
                       (or (= v #'clojure.core/require)
                           (= v #'clojure.core/use)))))
       (mapcat :args)
       (map #(-> % :expr :form))
       (remove nil?)
       (map #(if (coll? %) (first %) %))
       (remove keyword?)
       (into #{})))

(defn unused-namespaces [{:keys [asts]}]
  (let [curr-ns (-> asts first :statements first :args first :expr :form)
        required (required-namespaces asts)
        used (set (map #(-> ^clojure.lang.Var % .ns .getName) (keys (var-freq asts))))]
    (for [ns (set/difference required used)]
      {:linter :unused-namespaces
       :msg (format "Namespace %s is never used in %s" ns curr-ns)})))


;; Unused return values

(defn make-invoke-val-unused-action-map [rsrc-name]
  (let [sym-info-map (edn/read-string (slurp (io/resource rsrc-name)))]
    (into {}
          (for [[sym properties] sym-info-map]
            [(resolve sym)
             (cond (:macro properties) nil
                   (:side-effect properties) :side-effect
                   (:lazy properties) :lazy-fn
                   (:pure-fn properties) :pure-fn
                   (:pure-fn-if-fn-args-pure properties) :pure-fn-if-fn-args-pure
                   (:warn-if-ret-val-unused properties) :warn-if-ret-val-unused
                   :else nil)]))))

(defn make-static-method-val-unused-action-map [rsrc-name]
  (let [static-method-info-map (edn/read-string (slurp (io/resource rsrc-name)))]
    (into {}
          (for [[static-method properties] static-method-info-map]
            [(assoc static-method
               :class (Class/forName (str (:class static-method))))
             (cond (:side-effect properties) :side-effect
                   (:lazy properties) :lazy-fn
                   (:pure-fn properties) :pure-fn
                   (:pure-fn-if-fn-args-pure properties) :pure-fn-if-fn-args-pure
                   (:warn-if-ret-val-unused properties) :warn-if-ret-val-unused
                   :else nil)]))))

(def ^:dynamic *warning-if-invoke-ret-val-unused* {})
(def ^:dynamic *warning-if-static-ret-val-unused* {})

(defn- mark-things-in-defprotocol-expansion-post [{:keys [env] :as ast}]
  (if (not-any? #(= % 'clojure.core/defprotocol) (map #(and (seq? %) (first %)) (:eastwood/partly-resolved-forms ast)))
    ast
    (let [defprotocol-var (get-in ast [:ret :expr :val])
          ;; Mark the second statement, the interface
          ast (update-in ast
                         [:statements 1 :eastwood/defprotocol-expansion-interface]
                         (constantly defprotocol-var))
          sigs (get-in ast [:statements 3])]
      ;; If the 4th statement, the signatures, is nil, mark that ast
      ;; node, too.
      (if (nil? (:form sigs))
        (update-in ast [:statements 3 :eastwood/defprotocol-expansion-sigs]
                   (constantly defprotocol-var))
        ast))))

(defn mark-things-in-defprotocol-expansion
  "Return an ast that is identical to the argument, except that
expressions that appear to have been generated via 'defprotocol' will
have their 4th subexpression ast node marked specially with the
key :eastwood/defprotocol-expansion-sigs with the name of the
protocol.  There does not seem to be an easier way to avoid printing
out :unused-ret-vals warning messages of the form 'Constant value is
discarded inside null: null'."
  [ast]
  (ast/postwalk ast mark-things-in-defprotocol-expansion-post))

(defn unused-exprs-to-check [ast-node]
  (case (:op ast-node)
    (:const :var :local) [ast-node]
    :invoke (if (util/invoke-expr? ast-node)
              [ast-node]
              [])
    :static-call (if (util/static-call? ast-node)
                   [ast-node]
                   [])
    ;; If a do node has an unused ret value, then even its return
    ;; value is unused.
    :do (unused-exprs-to-check (:ret ast-node))
    ;; Digging into let exprs may be the cause of many many
    ;; auto-generated names appearing in the :unused-ret-vals linter
    ;; output during recent testing.  Too noisy to be useful like
    ;; that.  Try without it.
;    ;; Similarly for let, except everything in its body is unused.
;    :let (unused-exprs-to-check (:body ast-node))
    ;; If a :set node has an unused ret value, then all of its
    ;; elements have unused ret values.
    :set (mapcat unused-exprs-to-check (:items ast-node))
    ;; Same for :vector
    :vector (mapcat unused-exprs-to-check (:items ast-node))
    ;; Similar for :map, except check both :keys and :vals
    :map (concat
          (mapcat unused-exprs-to-check (:keys ast-node))
          (mapcat unused-exprs-to-check (:vals ast-node)))
    ;; For a :with-meta node, check its :expr child node
    :with-meta (unused-exprs-to-check (:expr ast-node))
    ;; Otherwise, nothing to check
    []))

(defn debug-unknown-fn-methods [fn-or-method stmt-desc-str stmt]
  (println (format "Unrecognized %s %s in this ast-node:"
                   stmt-desc-str fn-or-method))
  (when (map? fn-or-method)
    (println (format "(class (:class fn-or-method))=%s (class (:method fn-or-method))=%s"
                     (class (:class fn-or-method))
                     (class (:method fn-or-method)))))
  (println (format "   VVV ast-node="))
  (binding [*print-level* 7
            *print-length* 50
            *print-meta* true]
    (pp/pprint stmt))
  (println (format "   ^^^ ast-node=")))

(defn unused-ret-val-lint-result [stmt stmt-desc-str action fn-or-method
                                  location]
  (let [stmt-in-try-body? (util/statement-in-try-body? stmt)
        extra-msg (if stmt-in-try-body?
                    " inside body of try"
                    "")
        form (:form stmt)
        loc (or (pass/has-code-loc?
                 (case stmt-desc-str
                   "function call" (-> stmt :meta)
                   "static method call" (-> stmt :form meta)))
                (pass/code-loc (pass/nearest-ast-with-loc stmt)))
        ;; If there is no info about the method m in
        ;; *warning-if-static-ret-val-unused*, use reflection to see
        ;; if the return type of the method is void.  That is a fairly
        ;; sure sign that it is intended to be called for side
        ;; effects.
        action (if (and (= stmt-desc-str "static method call")
                        (not (#{:side-effect :lazy-fn :pure-fn :pure-fn-if-fn-args-pure :warn-if-ret-val-unused}
                              action)))
                 (let [^Method m (pass/get-method stmt)
                       ^Type ret-val (.getGenericReturnType m)]
                   (if (= ret-val Void/TYPE)
                     :side-effect
                     :warn-if-ret-val-unused))
                 action)]
    (if (or (and stmt-in-try-body?
                 (= location :inside-try))
            (and (not stmt-in-try-body?)
                 (= location :outside-try)))
      (case action
        ;; No warning - function/method is intended to be called for
        ;; its side effects, and ignoring the return value is normal.
        :side-effect
        nil

        (:lazy-fn :pure-fn :pure-fn-if-fn-args-pure :warn-if-ret-val-unused)
        {:linter (case location
                   :outside-try :unused-ret-vals
                   :inside-try :unused-ret-vals-in-try)
         :msg
         (case action
           :lazy-fn
           (format "Lazy %s return value is discarded%s: %s"
                   stmt-desc-str extra-msg form)
           :pure-fn
           (format "Pure %s return value is discarded%s: %s"
                   stmt-desc-str extra-msg form)
           :pure-fn-if-fn-args-pure
           (format "Return value is discarded for a %s that only has side effects if the functions passed to it as args have side effects%s: %s"
                   stmt-desc-str extra-msg form)
           :warn-if-ret-val-unused
           (format "Should use return value of %s, but it is discarded%s: %s"
                   stmt-desc-str extra-msg form))
         :file (-> loc :file)
         :line (-> loc :line)
         :column (-> loc :column)}

        ;; default case, where we have no information about the type
        ;; of function or method it is.  Note that for Clojure
        ;; function invocations on functions that are not known,
        ;; action will be nil here, and there will be no warning about
        ;; them.
        ;; TBD: Consider adding 'opts' to the API for all linters, so
        ;; this linter can receive options for what to do in this
        ;; case.
        (if (= stmt-desc-str "static method call")
          (debug-unknown-fn-methods fn-or-method stmt-desc-str stmt))))))

(defn unused-ret-vals-2 [location {:keys [asts]}]
  (binding [*warning-if-invoke-ret-val-unused*
            (make-invoke-val-unused-action-map "var-info.edn")
            *warning-if-static-ret-val-unused*
            (make-static-method-val-unused-action-map "jvm-method-info.edn")]
    (let [unused-ret-val-exprs (->> asts
                                    (map util/mark-exprs-in-try-body)
                                    (map mark-things-in-defprotocol-expansion)
                                    (mapcat ast/nodes)
                                    (mapcat :statements)
                                    (mapcat unused-exprs-to-check))
          should-use-ret-val-exprs
          (->> unused-ret-val-exprs
               (filter #(or (#{:const :var :local} (:op %))
                            (util/invoke-expr? %)
                            (util/static-call? %))))]
      (doall
       (remove
        nil?
        (for [stmt should-use-ret-val-exprs]
          ;; Note: Report unused :const :var and :local only when
          ;; linter is the regular :unused-ret-vals one, but do so for
          ;; such values whether they are inside of a try block or
          ;; not.  If both :unused-ret-vals and
          ;; :unused-ret-vals-in-try are specified, such values will
          ;; only be reported once, and if :unused-ret-vals-in-try is
          ;; used but not the other, they will not be reported.  I
          ;; expect that :unused-ret-vals will be the more commonly
          ;; used one, as the :unused-ret-vals-in-try will likely have
          ;; more false positives from functions being called in unit
          ;; tests to see if they throw an exception.
          (cond (and (#{:const :var :local} (:op stmt))
                     (= location :outside-try))
                (cond
                 ;; do not report part of a defprotocol macro
                 ;; expansion that the interface created via the
                 ;; gen-interface call
                 (get stmt :eastwood/defprotocol-expansion-interface)
                 nil

                 ;; do not report a nil value for the signature of a
                 ;; protocol with no methods
                 (get stmt :eastwood/defprotocol-expansion-sigs)
                 nil

                 ;; do not report an interface created via a
                 ;; definterface macro expansion
                 (util/interface? (:form stmt))
                 nil

                 ;; do not report an unused nil return value if it was
                 ;; caused by a comment or gen-class macro invocation.
                 (and (nil? (:form stmt))
                      (some #{'clojure.core/comment 'clojure.core/gen-class}
                            (map #(and (seq? %) (first %)) (:eastwood/partly-resolved-forms stmt))))
                 nil

                 :else
                 (let [name-found? (contains? (-> stmt :env) :name)
                       loc (if name-found?
                             (-> stmt :env :name meta)
                             (pass/code-loc (pass/nearest-ast-with-loc stmt)))]
                   {:linter :unused-ret-vals
                    :msg (format "%s value is discarded%s: %s"
                                 (case (:op stmt)
                                   :const "Constant"
                                   :var "Var"
                                   :local "Local")
                                 (if name-found?
                                   (str " inside " (-> stmt :env :name))
                                   "")
                                 (if (nil? (:form stmt))
                                   "nil"
                                   (str/trim-newline
                                    (with-out-str
                                      (binding [pp/*print-right-margin* nil]
                                        (pp/pprint (:form stmt)))))))
                    :file (-> loc :file)
                    :line (-> loc :line)
                    :column (-> loc :column)}))

                (util/static-call? stmt)
                (let [cls (:class stmt)
                      method (:method stmt)
                      m {:class cls :method method}
                      action (get *warning-if-static-ret-val-unused* m)]
                  (unused-ret-val-lint-result stmt "static method call"
                                              action m location))

                (util/invoke-expr? stmt)
                (let [v1 (get-in stmt [:fn :var])
                      ;; Special case for apply.  Issue a warning
                      ;; based upon the 1st arg to apply, not apply
                      ;; itself (if that arg is a var).
                      arg1 (first (:args stmt))
                      v (if (and (= v1 #'clojure.core/apply)
                                 (= :var (:op arg1)))
                          (:var arg1)
                          v1)
                      action (get *warning-if-invoke-ret-val-unused* v)]
                  (unused-ret-val-lint-result stmt "function call"
                                              action v location)))))))))

(defn unused-ret-vals [& args]
  (apply unused-ret-vals-2 :outside-try args))

(defn unused-ret-vals-in-try [& args]
  (apply unused-ret-vals-2 :inside-try args))


;; TODO: Unused locals
