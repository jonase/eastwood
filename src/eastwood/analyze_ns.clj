(ns eastwood.analyze-ns
  (:refer-clojure :exclude [macroexpand-1])
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.env :as env]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.passes :refer [schedule]]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.passes.trim :refer [trim]]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.passes.warn-earmuff :refer [warn-earmuff]]
   [eastwood.copieddeps.dep10.clojure.tools.reader :as reader]
   [eastwood.copieddeps.dep10.clojure.tools.reader.reader-types :as reader-types]
   [eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm :as jvm]
   [eastwood.copieddeps.dep2.clojure.tools.analyzer.passes.jvm.emit-form :refer [emit-form]]
   [eastwood.copieddeps.dep2.clojure.tools.analyzer.passes.jvm.warn-on-reflection :refer [warn-on-reflection]]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.move :as move]
   [eastwood.passes :as pass]
   [eastwood.util :as util])
  (:import
   (clojure.lang Namespace)))

;; uri-for-ns, pb-reader-for-ns were copied from library
;; jvm.tools.analyzer, then later probably diverged from each other.

(defn ^:private ns-resource-name
  "clojure.java.io/resource and Java in general expects components of
  a resource path name to be separated by '/' characters, regardless of
  the value of File/separator for the platform."
  [ns-sym extension]
  (-> (name ns-sym)
      (string/replace "." "/")
      (string/replace "-" "_")
      (str extension)))

(defn uri-for-ns
  "Returns a URI representing the namespace. Throws an
  exception if URI not found."
  ^java.net.URL
  [ns-sym]
  (let [rsrc-path-clj (ns-resource-name ns-sym ".clj")
        rsrc-path-cljc (ns-resource-name ns-sym ".cljc")
        uri-clj (io/resource rsrc-path-clj)
        uri-cljc (io/resource rsrc-path-cljc)
        uri (or uri-clj uri-cljc)]
    (when-not uri
      (throw (Exception. (str "No file found for namespace " ns-sym))))
    uri))

(defn pb-reader-for-ns
  "Returns an IndexingReader for namespace ns-sym"
  [ns-sym]
  (let [uri (uri-for-ns ns-sym)]
    (reader-types/indexing-push-back-reader (java.io.PushbackReader. (io/reader uri))
                                            1 (#'move/ns-file-name ns-sym))))

(defn all-ns-names-set []
  (set (map str (all-ns))))

(defn gen-interface-form? [form]
  (and (seq? form)
       (contains? #{'gen-interface 'clojure.core/gen-interface}
                  (first form))))

;; Avoid macroexpand'ing a gen-interface form more than once, since it
;; causes an exception to be thrown.
(defn dont-expand-twice? [form]
  (gen-interface-form? form))

(defn pre-analyze-debug [asts form _env ns opt]
  (when (util/debug? :ns opt)
    (let [print-normally? (util/debug? :forms opt)
          pprint? (util/debug? :forms-pprint opt)]
      (when (or print-normally? pprint?)
        (println (format "dbg pre-analyze #%d ns=%s (meta ns)=%s"
                         (count asts) (str ns) (meta ns)))
        (when pprint?
          (println "    form before macroexpand:")
          (pp/pprint form))
        (when print-normally?
          (println "    form before macroexpand, with metadata (some elided for brevity):")
          (util/pprint-meta-elided form))
        (println "\n    --------------------")
        (if (dont-expand-twice? form)
          (when print-normally?
            (println "    form is gen-interface, so avoiding macroexpand on it"))
          (let [exp (macroexpand form)]
            (when pprint?
              (println "    form after macroexpand:")
              (pp/pprint exp))
            (when print-normally?
              (println "    form after macroexpand, with metadata (some elided for brevity):")
              (util/pprint-meta-elided exp))))
        (println "\n    --------------------")))))

(def ^:dynamic *forms-read-writer* nil)
(def ^:dynamic *forms-analyzed-writer* nil)
(def ^:dynamic *forms-emitted-writer* nil)

(defn- with-form-writers [opt f]
  (if-not (util/debug? :compare-forms opt)
    (f)
    (binding [*forms-read-writer* (io/writer "forms-read.txt")
              *forms-analyzed-writer* (io/writer "forms-analyzed.txt")
              *forms-emitted-writer* (io/writer "forms-emitted.txt")]
      (f))))

(defn- debug-forms [form ast]
  (when *forms-read-writer*
    (binding [*out* *forms-read-writer*]
      (util/pprint-form form)))

  (when *forms-analyzed-writer*
    (binding [*out* *forms-analyzed-writer*]
      (util/pprint-form (:form ast))))

  (when *forms-emitted-writer*
    (binding [*out* *forms-emitted-writer*]
      (util/pprint-form (emit-form ast)))))

(defn- debug-forms-new-file [filename]
  (let [s (format "\n\n== Analyzing file '%s'\n" filename)]

    (when *forms-read-writer*
      (binding [*out* *forms-read-writer*]
        (println s)))

    (when *forms-analyzed-writer*
      (binding [*out* *forms-analyzed-writer*]
        (println s)))

    (when *forms-emitted-writer*
      (binding [*out* *forms-emitted-writer*]
        (println s)))))

(defn post-analyze-debug [_filename asts form ast ns opt]
  (when (util/debug? :ns opt)
    (let [show-ast? (util/debug? :ast opt)]
      (when (or show-ast? (util/debug? :progress opt))
        (println (format "dbg anal'd %d ns=%s%s"
                         (count asts) (str ns)
                         (if show-ast? " ast=" ""))))
      (when show-ast?
        (util/pprint-ast-node ast))
      ;; TBD: Change this to macroexpand form, at least if
      ;; dont-expand-twice? returns false.
      (when (util/debug? :compare-forms opt)
        (debug-forms form ast)))))

(defn begin-file-debug [filename _ns opt]
  (when (and (util/debug? :ns opt)
             (util/debug? :compare-forms opt))
    (debug-forms-new-file filename)))

(defn before-analyze-file-debug [source-path opt]
  (when (util/debug? :ns opt)
    (println (format "all-ns before (analyze-file \"%s\") begins:"
                     source-path))
    (pp/pprint (sort (all-ns-names-set)))))

(defn eastwood-wrong-tag-handler [t ast]
  (let [tag (if (= t :name/tag)
              (-> ast :name meta :tag)
              (get ast t))]

    ;; Key/value pairs to be merged into ast for later code to find
    ;; and issue warnings. We use a different map key for each
    ;; different value of t, because if we used the same map key, only
    ;; the last one merged in would remain, the way tools.analyzer.jvm
    ;; uses this return value.

    ;; Remember the value of 'tag' calculated here in the map, since
    ;; it seems that some later tools.analyzer(.jvm) pass may be
    ;; changing the values of the keys :tag :o-tag etc. that existed
    ;; when this function was called.
    (case t
      :name/tag {:eastwood/name-tag tag}
      :tag {:eastwood/tag tag}
      :o-tag {:eastwood/o-tag tag}
      :return-tag {:eastwood/return-tag tag})))

;; eastwood-passes is a cut-down version of run-passes in
;; tools.analyzer.jvm. It eliminates phases that are not needed for
;; linting, and which can cause analysis to throw exceptions, where in
;; Eastwood we would prefer to give linter warnings.

(def eastwood-passes
  "Set of passes that will be run by default on the AST by #'run-passes"
  (disj jvm/default-passes
        ;; `clojure.core/eval` in `analyze+eval` already generates reflection warnings from the Clojure compiler.
        ;; So `#'warn-on-reflection` would lead to duplicate warnings:
        #'warn-on-reflection
        #'trim
        ;; Remove redundant output in face of our own `:non-dynamic-earmuffs` linter:
        #'warn-earmuff))

(def scheduled-eastwood-passes
  (schedule eastwood-passes))

(defn run-passes
  "Function that will be invoked on the AST tree immediately after it has been constructed,
   by default set-ups and runs the default passes declared in #'default-passes"
  [ast]
  (scheduled-eastwood-passes ast))

(def eastwood-passes-opts
  (merge jvm/default-passes-opts
         {:validate/wrong-tag-handler eastwood-wrong-tag-handler}))

;; TBD: Consider changing how the functions called within
;; eastwood-ast-additions are defined so that they can be added to
;; eastwood-passes above instead.

(defn eastwood-ast-additions [ast ast-idx]
  (-> ast
      (pass/add-path [ast-idx])
      pass/add-ancestors))

(defn wrapped-exception? [result]
  (when (instance? eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm.ExceptionThrown result)
    (.e ^eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm.ExceptionThrown result)))

(defn asts-with-eval-exception
  "tools.analyzer.jvm/analyze+eval returns an AST with a :result key
  being a specific class of Exception object, if an exception occurred
  while eval'ing the corresponding expression. This is easy to check at
  the top level of an AST, but since analyze+eval recurses into
  top-level do forms, analyzing and eval'ing each independently,
  checking whether any of them threw an exception during eval requires
  recursing into ASTs with :op equal to :do"
  [ast]
  (filter #(wrapped-exception? (:result %))
          (ast/nodes ast)))

(defn remaining-forms [pushback-reader forms]
  (let [eof (reify)
        reader-opts {:read-cond :allow :features #{:clj} :eof eof}]
    (loop [forms forms]
      (let [form (reader/read reader-opts pushback-reader)]
        (if (identical? form eof)
          forms
          (recur (conj forms form)))))))

(defn- replace-path-in-compiler-error
  [msg cwd]
  (let [[linter
         kind
         [match
          pre
          _
          ^String path
          line-col
          post]] (or (when-let [[^String s p :as m]
                                (re-matches #"((Reflection|Performance|Boxed math) warning), (.*?)(:\d+:\d+)(.*)"
                                            msg)]
                       (let [l (case p
                                 "Boxed math warning" :boxed-math
                                 "Performance warning" :performance
                                 "Reflection warning" :reflection)]

                         [l
                          (when (= :performance l)
                            (cond
                              (-> s (.contains "case has int tests, but tested expression"))
                              :case

                              (-> s (.contains "hash collision of some case test constants"))
                              :hash))
                          m]))
                     (when-let [[m f l c x y] (re-matches #"(.*?)(:\d+)(:\d+)?( recur arg for primitive local)(.*)"
                                                          msg)]
                       [:performance
                        :recur
                        [m
                         "Performance warning"
                         :_
                         f
                         (str l (or c ":1"))
                         (str x y)]]))
        [line column] (some->> (some-> line-col (string/replace #"^:" "") (string/split #":"))
                               (mapv (fn [s]
                                       (Long/parseLong s))))
        alt-path (when (some-> path
                               (.endsWith ".clj"))
                   ;; The Clojure compiler can report files that originally are .cljc as .clj,
                   ;; which would hinder our `:reflection` linter:
                   (str path "c"))
        ;; For recur warnings, the clojure compiler can print opaque filenames.
        ;; For those, we fall back:
        recur-path (when (= kind :recur)
                     *file*)
        url (and match
                 (or (io/resource path)
                     (some-> alt-path io/resource)
                     (some-> recur-path io/resource)))
        uri (some-> url
                    (util/file-warn-info cwd)
                    :uri-or-file-name)]
    (if uri
      {:type linter
       :kind kind
       :pre pre
       :uri uri
       :line line
       :column column
       :post post}
      (when-not (or (re-find #"not declared dynamic and thus is not dynamically rebindable, but its name suggests otherwise" msg)
                    (re-find #"Auto-boxing loop arg" msg))
        msg))))

(defn- do-eval-output-callbacks [out-msgs-str err-msgs-str cwd]
  (when-not (= out-msgs-str "")
    (doseq [s (string/split-lines out-msgs-str)]
      (println s)))
  (let [reflection-warnings (atom [])
        boxed-math-warnings (atom [])
        performance-warnings (atom [])]
    (when-not (= err-msgs-str "")
      (doseq [s (string/split-lines err-msgs-str)
              :let [v (replace-path-in-compiler-error s cwd)]]
        (if (map? v)
          (swap! (case (:type v)
                   :reflection reflection-warnings
                   :boxed-math boxed-math-warnings
                   :performance performance-warnings)
                 conj
                 v)
          (some-> v println))))
    [@reflection-warnings @boxed-math-warnings @performance-warnings]))

(defn cleanup [form]
  (let [should-change? (and (-> form list?)
                            (some-> form first symbol?)
                            (some-> form first name (.startsWith "def"))
                            (some-> form second symbol?)
                            (-> form second meta :const))]
    (cond-> form
      should-change? vec
      should-change? (update 1 vary-meta dissoc :const)
      should-change? seq)))

(defn var->symbol [var]
  (if (util/clojure-1-10-or-later)
    ;; use the most accurate method (as it can't be deceived by external metadata mutation):
    (-> var symbol)
    (let [^Namespace ns (-> var meta :ns)]
      (symbol (-> ns .-name name)
              (-> var meta :name name)))))

(defn skip-form? [form]
  (and (list? form)
       (= 3 (count form))
       (let [[a b c] form]
         (and (false? c)
              (= 'set! a)
              (= (ns-resolve *ns* b)
                 #'*warn-on-reflection*)))))

(defn meta-or-val [x]
  (if (instance? clojure.lang.IObj x)
    (meta x)
    x))

(def ^:dynamic *analyze+eval* nil)

(defn analyze-file
  "Takes a file path and optionally a pushback reader. Returns a map
  with at least the following keys:

  :forms - a sequence of forms as read in, with any forms within a
      top-level do, or do forms nested within a top-level do,
      'flattened' to the top level themselves. This sequence will
      include all forms in the file, as far as the file could be
      successfully read, even if an exception was thrown earlier than
      that during analysis or evaluation of the forms.

  :asts - a sequence of ASTs of the forms that were successfully
      analyzed without exception. They correspond one-to-one with
      forms in the :forms sequence.

  :exception - nil if no exception occurred. An Exception object if
      an exception was thrown during analysis, emit-form, or eval.

  If :exception is not nil, then the following keys will also be part
  of the returned map:

  :exception-phase - If an exception was thrown, this is a keyword
      indicating in what portion of analyze-file's operation this
      exception occurred. Always :analyze+eval or :eval

  :exception-form - If an exception was thrown, the current form being
      processed when the exception occurred.

  Options:
  - :reader  a pushback reader to use to read the namespace forms
  - :opt     a map of analyzer options
    - `:abort-on-core-async-exceptions?` (default: falsey)
      - if true, analyze+eval exceptions stemming from `go` usage will abort execution.`
      - currently, execution is not aborted, rationale being https://github.com/jonase/eastwood/issues/395
    - :debug A set of keywords.
      - :all Enable all of the following debug messages.
      - :progress Print simple progress messages as analysis proceeds.
      - :ns Print all namespaces that exist according to (all-ns)
            before analysis begins, and then only when that set of
            namespaces changes after each form is analyzed.
      - :forms Print forms just before analysis, both before and after
               macroexpanding them.
      - :forms-pprint Pretty-print forms just before analysis, both
                      before and after macroexpanding them.
      - :ast Print complete ASTs just after analysis of each form.

  eg. (analyze-file \"my/ns.clj\" :opt {:debug-all true})"
  [source-nsym source-path & {:keys [reader opt]}]
  (let [linting-boxed-math? (:eastwood/linting-boxed-math? opt)
        eof (reify)
        reader-opts {:read-cond :allow :features #{:clj} :eof eof}
        source-path-str (str source-path)]
    (before-analyze-file-debug source-path opt)

    ;; prevent t.ana from possibly altering *ns* or such:
    (binding [*assert* *assert*
              *command-line-args* *command-line-args*
              *compile-path* *compile-path*
              *data-readers* *data-readers*
              *default-data-reader-fn* *default-data-reader-fn*
              *file* source-path-str
              *math-context* *math-context*
              *ns* *ns*
              *print-length* *print-length*
              *print-level* *print-level*
              *print-meta* *print-meta*
              *unchecked-math* *unchecked-math*
              *warn-on-reflection* *warn-on-reflection*
              reader/*data-readers* *data-readers*]
      (env/with-env (jvm/global-env)
        (begin-file-debug *file* *ns* opt)

        ;; https://github.com/jonase/eastwood/issues/419
        (in-ns source-nsym)
        (do
          (doseq [alias (keys (ns-aliases *ns*))]
            (try
              (ns-unalias *ns* alias)
              (catch Exception _)))
          (doseq [alias (keys (ns-refers *ns*))]
            (try
              (ns-unmap *ns* alias)
              (catch Exception _))))

        (loop [forms []
               asts []
               reflection-warnings-plural []
               boxed-math-warnings-plural []
               performance-warnings-plural []]
          (let [form (cleanup (reader/read reader-opts reader))]
            (if (identical? form eof)
              {:forms forms
               :asts asts
               :exception nil
               :reflection-warnings (distinct reflection-warnings-plural)
               :boxed-math-warnings (distinct boxed-math-warnings-plural)
               :performance-warnings (distinct performance-warnings-plural)}
              (let [cur-env (env/deref-env)
                    _ (pre-analyze-debug asts form cur-env *ns* opt)
                    [exc ast reflection-warnings boxed-math-warnings performance-warnings]
                    (try
                      (let [skip? (skip-form? form)
                            {:keys [val out err]}
                            (if skip?
                              [::omit "" ""]
                              (util/with-out-str2
                                (binding [jvm/run-passes run-passes]
                                  (let [{:keys [result] :as v}
                                        (binding [*warn-on-reflection* true
                                                  *unchecked-math* (if linting-boxed-math?
                                                                     :warn-on-boxed
                                                                     *unchecked-math*)]
                                          (*analyze+eval* form
                                                          (jvm/empty-env)
                                                          {:passes-opts eastwood-passes-opts}
                                                          (-> form meta-or-val)
                                                          (-> *ns* str)))]
                                    (when (and (var? result)
                                               (-> result meta :arglists vector?)
                                               (some-> result meta :arglists first seq?)
                                               (some-> result meta :arglists first first #{'quote}))
                                      ;; Cleanup odd arglists such as git.io/Jnidv
                                      ;; (as they have unexpected quotes, and a swapped choice of vectors/lists):
                                      (alter-meta! result (fn [{:keys [arglists]
                                                                :as m}]
                                                            (assoc m
                                                                   :arglists
                                                                   (->> arglists
                                                                        (mapv (fn [x]
                                                                                (cond-> x
                                                                                  (and (seq? x)
                                                                                       (-> x count #{2})
                                                                                       (-> x first #{'quote}))
                                                                                  last)))
                                                                        (list))))))
                                    v))))
                            [reflection-warnings boxed-math-warnings performance-warnings]
                            (if skip?
                              [[] [] []]
                              (binding [*file* source-path-str]
                                (do-eval-output-callbacks out err (:cwd opt))))]
                        [nil val reflection-warnings boxed-math-warnings performance-warnings])
                      (catch Exception e
                        (let [had-go-call? (atom false)]
                          (when-not (:abort-on-core-async-exceptions? opt)
                            (->> form
                                 (walk/postwalk (fn [x]
                                                  (when (and (not @had-go-call?) ;; performance optimization
                                                             (seq? x)
                                                             (-> x first symbol?)
                                                             (= 'clojure.core.async/go
                                                                (try
                                                                  (some->> x
                                                                           first
                                                                           (ns-resolve *ns*)
                                                                           var->symbol)
                                                                  ;; ns-resolve can fail pre Clojure 1.10:
                                                                  (catch Exception _
                                                                    nil))))
                                                    (reset! had-go-call? true))
                                                  x))))
                          (if @had-go-call?
                            (try
                              ;; eval the form (without tools.analyzer),
                              ;; increasing the chances that its result can be useful, queried, etc
                              (eval form)
                              [nil ::omit]
                              (catch Exception _
                                ;; if the `eval` failed, return the tools.analyzer exception - not the `eval` one:
                                [e nil]))
                            [e nil]))))]
                (if exc
                  {:forms nil
                   :asts asts,
                   :exception exc,
                   :exception-phase :analyze+eval,
                   :exception-form form}

                  (if-let [first-exc-ast (first (asts-with-eval-exception ast))]
                    {:forms (remaining-forms reader (conj forms form)),
                     :asts asts,
                     :exception (-> first-exc-ast :result wrapped-exception?),
                     :exception-phase :eval,
                     :exception-form (if-let [f (-> first-exc-ast :raw-forms first)]
                                       f
                                       (:form first-exc-ast))}
                    (let [conj? (not= ast ::omit)]
                      (post-analyze-debug source-path asts form ast *ns* opt)
                      (recur (cond-> forms
                               conj? (conj form))
                             (cond-> asts
                               conj? (conj (eastwood-ast-additions
                                            ast (count asts))))
                             (cond-> reflection-warnings-plural
                               conj? (into reflection-warnings))
                             (cond-> boxed-math-warnings-plural
                               conj? (into boxed-math-warnings))
                             (cond-> performance-warnings-plural
                               conj? (into performance-warnings))))))))))))))

(defn analyze-ns
  "Returns a map of results of analyzing the namespace.
  The map contains these keys:

  :analyze-results - The value associated with this key is itself a
      map with the following keys:
      :namespace - The source-nsym argument to this fn
      :source - A string containing the source read in from the
          namespace's source file.
      :forms - See analyze-file docs for details
      :asts - See analyze-file
  :exception, :exception-phase, :exception-form - See analyze-file

  Options:
  - :reader  a pushback reader to use to read the namespace forms
  - :opt     a map of analyzer options
    - same as analyze-file."
  [source-nsym & {:keys [reader opt]
                  :or {reader (pb-reader-for-ns source-nsym)}}]
  (with-form-writers opt
    (fn []
      (let [source-path (#'move/ns-file-name source-nsym)
            {:keys [reflection-warnings
                    boxed-math-warnings
                    performance-warnings]
             :as m}     (analyze-file source-nsym source-path :reader reader :opt opt)
            source      (-> source-nsym uri-for-ns slurp)]
        (-> m
            (dissoc :forms :asts :reflection-warnings)
            (assoc :analyze-results {:source        source
                                     :namespace     source-nsym
                                     :exeption-form (:exception-form m)
                                     :forms         (:forms m)
                                     :reflection-warnings reflection-warnings
                                     :boxed-math-warnings boxed-math-warnings
                                     :performance-warnings performance-warnings
                                     :asts          (->> m
                                                         :asts
                                                         (mapv (fn [m]
                                                                 (assoc m
                                                                        :eastwood/ns-source source
                                                                        :eastwood/ns-sym source-nsym))))}))))))
