(ns eastwood.linters.unused
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.reader.edn :as edn]
            [clojure.pprint :as pp]
            [eastwood.util :as util]
            [clojure.tools.analyzer.ast :as ast]))

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
       :line (-> pvar :env :line)})))

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
          :when (not-empty unused)]
      {:linter :unused-fn-args
       :msg (format "Function args [%s] of (or within) %s are never used"
                    (str/join " " (map (fn [sym]
                                         (if-let [l (-> sym meta :line)]
                                           (format "%s (line %s)" sym l)
                                           sym))
                                       unused))
                    (-> expr :env :name))
       :line (-> expr :env :name meta :line)})))


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

(defn unused-exprs-to-check [ast-node]
  (case (:op ast-node)
    :invoke (if (util/invoke-expr? ast-node)
              [ast-node]
              [])
    :const [ast-node]
    :static-call (if (util/static-call? ast-node)
                   [ast-node]
                   [])
    ;; If a do node has an unused ret value, then even its return
    ;; value is unused.
    :do (unused-exprs-to-check (:ret ast-node))
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
        line (case stmt-desc-str
               "function call" (-> stmt :meta :line)
               "static method call" (-> stmt :form meta :line))]
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
         :line line}

        ;; default case, where we have no information about the type
        ;; of function or method it is.  TBD: Consider adding 'opts'
        ;; to the API for all linters, so this linter can receive
        ;; options for what to do in this case.
        (if (= stmt-desc-str "static method call")
          (debug-unknown-fn-methods fn-or-method stmt-desc-str stmt))))))

(defn unused-ret-vals-2 [location {:keys [asts]}]
  (binding [*warning-if-invoke-ret-val-unused*
            (make-invoke-val-unused-action-map "var-info.edn")
            *warning-if-static-ret-val-unused*
            (make-static-method-val-unused-action-map "jvm-method-info.edn")]
    (let [unused-ret-val-exprs (->> asts
                                    (map util/mark-exprs-in-try-body)
                                    (mapcat ast/nodes)
                                    (mapcat :statements)
                                    (mapcat unused-exprs-to-check))
          should-use-ret-val-exprs
          (->> unused-ret-val-exprs
               (filter #(or (= :const (:op %))
                            (util/invoke-expr? %)
                            (util/static-call? %))))]
      (doall
       (remove
        nil?
        (for [stmt should-use-ret-val-exprs]
          (cond (and (= :const (:op stmt))
                     (= location :outside-try))
                (if (util/interface? (:form stmt))
                  ;; Then assume this is an interface created by the
                  ;; gen-interface within the macro invocation of
                  ;; defprotocol, and do not report it.  More precise
                  ;; would be to do a walk of the ast, look for
                  ;; macroexpansions of forms that appear to be from
                  ;; defprotocol, and add an annotation to the sub-ast
                  ;; with :op equal to :const for the interface so it
                  ;; is not reported, but for now this should be close
                  ;; enough.
                  nil
                  {:linter :unused-ret-vals
                   :msg (format "Constant value is discarded inside %s: %s"
                                (-> stmt :env :name)
                                (:form stmt))
                   :line (-> stmt :env :name meta :line)})

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
