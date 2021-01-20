(ns eastwood.linters.unused
  (:import (java.lang.reflect Method))
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [eastwood.copieddeps.dep10.clojure.tools.reader.edn :as edn]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.parse :as parse]
            [clojure.pprint :as pp]
            [eastwood.util :as util]
            [eastwood.passes :as pass]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]))

;; Unused private vars
(defn- private-non-const-defs [asts]
  (->> asts
       (mapcat ast/nodes)
       (filter (fn [ast]
                 (and (= :def (:op ast))
                      (-> ast :var meta :private)
                      ;; Do not warn about private :const's, since I
                      ;; am not sure I know a good way to track where
                      ;; they are used in asts.
                      (not (-> ast :var meta :const))
                      ;; TBD: Is this good to have?  skip private macros
                      ;;(-> ast :var meta :macro not)
                      )))
       (map :var)))

(defn- vars-used [asts]
  (->> asts
       (mapcat ast/nodes)
       (filter #(#{:var :the-var} (:op %)))
       (map :var)
       set))

(defn- symbols-used [asts]
  (->> asts
       (mapcat ast/nodes)
       (filter #(and (= :const (:op %))
                     (= :symbol (:type %))))
       (map :val)
       set))

(defn- keywords-used [asts]
  (->> asts
       (mapcat ast/nodes)
       (filter #(and (= :const (:op %))
                     (= :keyword (:type %))))
       (map :val)
       set))

(defn macros-invoked [asts]
  (->> asts
       (mapcat ast/nodes)
       (mapcat :raw-forms)
       (map util/fqsym-of-raw-form)
       (remove nil?)
       set))

(defn namespace-for [^Class klass]
  (-> (.getCanonicalName klass)
      (str/replace "_" "-")
      (str/replace #"\.[^\.]+$" "")
      symbol))

(defn classes-used [asts]
  (let [simple-tags (->> asts
                         (mapcat ast/nodes)
                         (keep :o-tag))
        tags-from-meta (->> asts
                            (mapcat ast/nodes)
                            (keep (comp :tag meta :result)))
        tags (into simple-tags tags-from-meta)]
    
    (into #{}
          (remove nil?)
          tags)))

(defn protocols-used [asts]
  (->> asts
       (mapcat ast/nodes)
       (mapcat :interfaces)
       (remove nil?)
       set))

(defn unused-private-vars [{:keys [asts]} opt]
  (let [pdefs (private-non-const-defs asts)
        vars-used-set (vars-used asts)]
    (for [pvar pdefs
          :when (not (vars-used-set pvar))
          :let [loc (meta pvar)]]
      {:loc loc
       :linter :unused-private-vars
       :msg (format "Private var '%s' is never used"
                    (-> pvar util/var-to-fqsym name))})))

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

(defn unused-fn-args [{:keys [asts]} opt]
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
      {:loc loc
       :linter :unused-fn-args
       :msg (format "Function arg %s never used" unused-sym)})))


;; Symbols in let or loop bindings that are unused

;; Note: the let bindings are sequential.  It can happen that the only
;; place a bound symbol is used is in the init expression of a later
;; bound symbol, and not in the let body at all.  This must be handled
;; or we will get incorrect warnings.

(defn- ignore-local-symbol?
  "Return logical true for let/loop symbols that should never be
warned about as unused.

By convention, _ is never reported as unused, and neither is any
symbol with a name that begins with _.  This gives eastwood users a
way to selectively disable such warnings if they wish."
  [arg]
  (.startsWith (name arg) "_"))

(defn all-suffixes
  "Given a coll, returns the coll, then (next coll), then (next (next
coll)), etc., as long as the result is not empty.

Example: (all-suffixes [1 2 3])
         => ([1 2 3] (2 3) (3))"
  [coll]
  (take-while seq (iterate next coll)))


(defn symbols-from-bindings [expr]
  (map (fn [b]
;;         (println (format "  name=%s used-locals=%s init="
;;                          (:name b)
;;                          (used-locals (ast/nodes (:init b)))))
;;         (util/pprint-ast-node (:init b))
         (assoc (select-keys b [:form :name :init])
           :locals-used-in-init-expr (used-locals (ast/nodes (:init b)))))
       (:bindings expr)))


(defn unused-locals* [expr]
  (let [symbols-seq (symbols-from-bindings expr)
        locals-used-in-body (used-locals (ast/nodes (:body expr)))
;;        _ (when (seq symbols-seq)
;;            (println (format "%s-dbg:" (name (:op expr))))
;;            (doseq [s symbols-seq]
;;              (let [loc (or (pass/has-code-loc? (-> s :form meta))
;;                            (pass/code-loc (pass/nearest-ast-with-loc expr)))]
;;                (println (format "    binding form=%s name=%s line=%s col=%s locals-used-in-init=%s"
;;                                 (-> s :form)
;;                                 (-> s :name)
;;                                 (-> loc :line)
;;                                 (-> loc :column)
;;                                 (-> s :locals-used-in-init-expr)))))
;;            (doseq [s (used-locals (ast/nodes (:body expr)))]
;;              (println (format "    use     form=%s name=%s line=%s col=%s"
;;                               (-> s :form)
;;                               (-> s :name)
;;                               (-> s :form meta :line)
;;                               (-> s :form meta :column)))))
        ]
    (loop [unused #{}
           symbols symbols-seq]
      (if-let [sym (first symbols)]
        (recur (set/difference (conj unused (select-keys sym [:form :name]))
                               (:locals-used-in-init-expr sym))
               (next symbols))
        ;; Return them in the same order they appeared in the bindings
        ;; form
        (let [unused-set (set/difference unused locals-used-in-body)]
          (->> symbols-seq
               (map (fn [s] (select-keys s [:form :name])))
               (filter unused-set)))))))


;; Never warn about symbols bound by let's that result from expanding
;; clojure.core/loop.  See function foo2 in namespace
;; testcases.unusedlocals for an example and discussion.

(defn let-ast-from-loop-expansion [ast]
  (and (= :let (:op ast))
       (= 'clojure.core/loop
          (-> ast :raw-forms first util/fqsym-of-raw-form))))


(defn unused-locals [{:keys [asts]} opt]
  (let [exprs (->> asts
                   (mapcat ast/nodes)
                   (filter (fn [ast]
                             (or (and (= :let (:op ast))
                                      (not (let-ast-from-loop-expansion ast)))
                                 (= :loop (:op ast))))))]
    (for [expr exprs
          ;; Without a check like this, defrecord's with no fields
          ;; have macroexpansions containing multiple let's with
          ;; unused symbols that would be warned about.
          :when (not (and (= :let (:op expr))
                          (util/inside-fieldless-defrecord expr)))
          :let [unused (->> (unused-locals* expr)
                            (map :form)
                            (remove ignore-local-symbol?))]
          unused-sym unused
          :let [loc (or (pass/has-code-loc? (-> unused-sym meta))
                        (pass/code-loc (pass/nearest-ast-with-loc expr)))]]
      {:loc loc
       :linter :unused-locals
       :msg (format "%s bound symbol '%s' never used"
                    (-> expr :op name)
                    unused-sym)})))


;; Unused namespaces

(defn required-namespaces [ns-asts]
  (->> ns-asts
       (mapcat #(nnext (first (:raw-forms %))))
       (filter (fn [f] (#{:require :use} (first f))))
       (mapcat rest)
       (mapcat #(#'parse/deps-from-libspec nil %))
       set))


;; (first ns-asts) below will likely find the *only* ns form in the
;; entire file, but it is written to use the first one in case there
;; are more than one.  See :wrong-ns-form linter for more checks on ns
;; forms.

;; The unused-namespaces linter doesn't even try to have line:col
;; info.  We should usually, if not always, be able to get some from
;; the namespace where it was read.  At worst, it should get the
;; beginning of the ns form it is in.

(defn unused-namespaces [{:keys [asts]} opt]
  (let [ns-asts (util/ns-form-asts asts)
        loc (pass/code-loc (first ns-asts))
        curr-ns (-> ns-asts first :form second second second)
        required (required-namespaces ns-asts)
        used-vars (vars-used asts)
        used-symbols (symbols-used asts)
        used-keywords (keywords-used asts)
        used-macros (macros-invoked asts)
        used-protocols (protocols-used asts)
        used-classes (classes-used asts)
;;        _ (do
;;            (println "dbg: required namespaces:")
;;            (pp/pprint required)
;;            (println "dbg: vars used:")
;;            (pp/pprint (map (juxt #(.getName (.ns %))
;;                                  #(type (.getName (.ns %)))
;;                                  #(.sym %))
;;                            used-vars))
;;            (println "dbg: symbols used:")
;;            (pp/pprint (map (juxt identity #(if-let [n (namespace %)]
;;                                              (symbol n))
;;                                  #(symbol (name %)))
;;                            used-symbols))
;;            (println "dbg: keywords used:")
;;            (pp/pprint (map (juxt identity #(if-let [n (namespace %)]
;;                                              (symbol n))
;;                                  #(symbol (name %)))
;;                            used-keywords))
;;            (println "dbg: macros used:")
;;            (pp/pprint used-macros)
;;            (println "dbg: protocols used:")
;;            (pp/pprint used-protocols))
        used-namespaces (set
                         (concat (map #(-> ^clojure.lang.Var % .ns .getName)
                                      used-vars)
                                 (->> used-symbols
                                      (map namespace)
                                      (remove nil?)
                                      (map symbol))
                                 (->> used-keywords
                                      (map namespace)
                                      (remove nil?)
                                      (map symbol))
                                 (keep #(if-let [n (namespace %)] (symbol n))
                                       used-macros)
                                 (map namespace-for used-protocols)
                                 (map namespace-for used-classes)))]
    (for [ns (set/difference required used-namespaces)]
      {:loc loc
       :unused-namespace-sym ns
       :linter :unused-namespaces
       :msg (format "Namespace %s is never used in %s" ns curr-ns)})))


;; Unused return values

(defn make-invoke-val-unused-action-map [rsrc-name]
  (let [sym-info-map (edn/read-string (slurp (io/resource rsrc-name)))]
    (into {}
     (for [[sym properties] sym-info-map]
       [sym
        (cond (:macro properties) nil
              (:side-effect properties) :side-effect
              (:lazy properties) :lazy-fn
              (:pure-fn properties) :pure-fn
              (:pure-fn-if-fn-args-pure properties) :pure-fn-if-fn-args-pure
              (:warn-if-ret-val-unused properties) :warn-if-ret-val-unused
              :else nil)]))))

(defn make-static-method-val-unused-action-map [rsrc-name]
  (let [static-method-info-map (edn/read-string
                                (slurp (io/resource rsrc-name)))]
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


(def warning-unused-invoke-delayed
  (delay
   (make-invoke-val-unused-action-map "var-info.edn")))


(def warning-unused-static-delayed
  (delay
   (make-static-method-val-unused-action-map "jvm-method-info.edn")))


(defn- mark-things-in-defprotocol-expansion-post [ast]
  (if (not (util/ast-expands-macro ast #{'clojure.core/defprotocol}))
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
        ;; If warning-unused-static had no info about method m, but
        ;; its return type is void, that is a fairly sure sign that it
        ;; is intended to be called for side effects.
        action (if (and (= stmt-desc-str "static method call")
                        (not
                         (#{:side-effect :lazy-fn :pure-fn
                            :pure-fn-if-fn-args-pure :warn-if-ret-val-unused}
                          action)))
                 (let [m (pass/get-method stmt)]
                   ;; If pass/get-method could not determine the method,
                   ;; do not give an unused-ret-val warning for it.  We
                   ;; may at some point in the future wish to give a
                   ;; warning that we could not determine which method
                   ;; it is.
                   (cond (not (instance? Method m)) :side-effect
                         (pass/void-method? m) :side-effect
                         :else :warn-if-ret-val-unused))
                 action)
        linter (case location
                 :outside-try :unused-ret-vals
                 :inside-try :unused-ret-vals-in-try)]
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
        {:loc loc
         :linter linter
         linter {:kind (:op stmt), :action action, :ast stmt}
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
                   stmt-desc-str extra-msg form))}

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


(defn op-desc [op]
  (case op
    :const "Constant"
    :var "Var"
    :local "Local"))

;; Note 1: Report unused :const :var and :local only when linter is
;; the regular :unused-ret-vals one, but do so for such values whether
;; they are inside of a try block or not.  If both :unused-ret-vals
;; and :unused-ret-vals-in-try are specified, such values will only be
;; reported once, and if :unused-ret-vals-in-try is used but not the
;; other, they will not be reported.  I expect that :unused-ret-vals
;; will be the more commonly used one, as the :unused-ret-vals-in-try
;; will likely have more false positives from functions being called
;; in unit tests to see if they throw an exception.

;; Note 2: Do not report part of a defprotocol macro expansion that
;; the interface created via the gen-interface call.

;; Note 3: Do not report a nil value for the signature of a protocol
;; with no methods.

;; Note 4: Do not report an interface created via a definterface macro
;; expansion.

;; Note 5: Do not report an unused nil return value if it was caused
;; by a comment or gen-class macro invocation.

(defn unused-ret-vals-2 [location {:keys [asts]} opt]
  (let [warning-unused-invoke @warning-unused-invoke-delayed
        warning-unused-static @warning-unused-static-delayed
        unused-ret-val-exprs (->> asts
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
    (remove nil?
     (for [stmt should-use-ret-val-exprs]
       ;; See Note 1
       (cond
        (and (#{:const :var :local} (:op stmt))
             (= location :outside-try))
        (if (or
             (get stmt :eastwood/defprotocol-expansion-interface)  ; Note 2
             (get stmt :eastwood/defprotocol-expansion-sigs)  ; Note 3
             (util/interface? (:form stmt))  ; Note 4
             (and (nil? (:form stmt))  ; Note 5
                  (util/ast-expands-macro stmt #{'clojure.core/comment
                                                 'clojure.core/gen-class})))
          nil  ; no warning
          (let [name-found? (contains? (-> stmt :env) :name)
                loc (or
                     (pass/has-code-loc? (-> stmt :raw-forms first meta))
                     (if name-found?
                       (-> stmt :env :name meta)
                       (pass/code-loc (pass/nearest-ast-with-loc stmt))))]
            {:loc loc
             :linter :unused-ret-vals
             :unused-ret-vals {:kind (:op stmt), :ast stmt}
             :msg (format "%s value is discarded%s: %s"
                          (op-desc (:op stmt))
                          (if name-found?
                            (str " inside " (-> stmt :env :name))
                            "")
                          (if (nil? (:form stmt))
                            "nil"
                            (str/trim-newline
                             (with-out-str
                               (binding [pp/*print-right-margin* nil]
                                 (pp/pprint (:form stmt)))))))}))

        (util/static-call? stmt)
        (let [m (select-keys stmt [:class :method])
              action (warning-unused-static m)]
          (unused-ret-val-lint-result stmt "static method call"
                                      action m location))

        (util/invoke-expr? stmt)
        (let [v1 (get-in stmt [:fn :var])
              ;; Special case for apply.  Issue a warning based upon
              ;; the 1st arg to apply, not apply itself (if that arg
              ;; is a var).
              arg1 (first (:args stmt))
              v (if (and (= (util/var-to-fqsym v1) 'clojure.core/apply)
                         (= :var (:op arg1)))
                  (:var arg1)
                  v1)
              action (warning-unused-invoke (util/var-to-fqsym v))]
          (unused-ret-val-lint-result stmt "function call"
                                      action v location)))))))


(defn unused-ret-vals* [location {:keys [asts] :as m} opt]
  (let [warnings (unused-ret-vals-2 location m opt)]
    (for [w warnings
          :let [linter (:linter w)
                info (get w linter)
                ast (:ast info)
                allow? (util/allow-warning w opt)]
          :when allow?]
      (do
        (util/debug-warning w ast opt #{:enclosing-macros})
        w))))


(defn unused-ret-vals [& args]
  (apply unused-ret-vals* :outside-try args))

(defn unused-ret-vals-in-try [& args]
  (apply unused-ret-vals* :inside-try args))


;; Unused metadata on macro invocations (depends upon macro
;; definition, but most macros ignore it)

(defn unused-meta-on-macro [{:keys [asts]} opt]
  (let [macro-invokes (->> asts
                           (mapcat ast/nodes)
                           (filter #(and (:raw-forms %)
                                         (-> % :raw-forms first meta))))]
    (for [ast macro-invokes
          :let [orig-form (-> ast :raw-forms first)
                loc (-> orig-form meta)
                ;; The ::resolved-op key was added recently to t.a(.j)
                ;; libs to record the resolution of Var names in
                ;; :raw-forms.
                non-loc-meta-keys (-> (set (keys loc))
                                      (set/difference #{:file :line :column
                                                        :end-line :end-column
                                                        :eastwood.copieddeps.dep1.clojure.tools.analyzer/resolved-op
                                                        }))
                resolved-macro-symbol (-> ast :raw-forms first
                                          util/fqsym-of-raw-form)
                removed-meta-keys
                (cond (= :new (:op ast)) non-loc-meta-keys

                      (#{:instance-call :static-call
                         :instance-field :static-field :host-interop} (:op ast))
                      (disj non-loc-meta-keys :tag)

                      ;; No metadata is removed for invocations of
                      ;; clojure.core/fn -- It is implemented
                      ;; specially to do this by examining the
                      ;; metadata of its &form argument.
                      (= resolved-macro-symbol 'clojure.core/fn) []

                      :else non-loc-meta-keys)

                sorted-removed-meta-keys (sort removed-meta-keys)]
          :when (seq removed-meta-keys)]

      {:loc loc
       :linter :unused-meta-on-macro
       :msg
       (cond
         (= :new (:op ast))
         (format "Java constructor call '%s' has metadata with keys %s.  All metadata is eliminated from such forms during macroexpansion and thus ignored by Clojure."
                 (first orig-form)
                 sorted-removed-meta-keys)

         (#{:instance-call :static-call :instance-field :static-field
            :host-interop} (:op ast))
         (format "Java %s '%s' has metadata with keys %s.  All metadata keys except :tag are eliminated from such forms during macroexpansion and thus ignored by Clojure."
                 (case (:op ast)
                   :instance-call "instance method call"
                   :static-call "static method call"
                   :instance-field "instance field access"
                   :static-field "static field access"
                   :host-interop "instance method/field access")
                 (first orig-form)
                 sorted-removed-meta-keys)

         :else
         (format "Macro invocation of '%s' has metadata with keys %s that are almost certainly ignored."
                 (first orig-form)
                 sorted-removed-meta-keys))})))
