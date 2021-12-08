(ns eastwood.lint
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [eastwood.analyze-ns :as analyze-ns]
   [eastwood.copieddeps.dep10.clojure.tools.reader :as reader]
   [eastwood.copieddeps.dep11.clojure.java.classpath :as classpath]
   [eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm :as jvm]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.dir :as dir]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.file :as file]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.find :as find]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.parse :as parse]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.track :as track]
   [eastwood.error-messages :as msgs]
   [eastwood.exit :refer [exit-fn]]
   [eastwood.linters.boxed-math :as boxed-math]
   [eastwood.linters.deprecated :as deprecated]
   [eastwood.linters.implicit-dependencies :as implicit-dependencies]
   [eastwood.linters.misc :as misc]
   [eastwood.linters.performance :as performance]
   [eastwood.linters.reflection :as reflection]
   [eastwood.linters.typetags :as typetags]
   [eastwood.linters.typos :as typos]
   [eastwood.linters.unused :as unused]
   [eastwood.reporting-callbacks :as reporting]
   [eastwood.util :as util]
   [eastwood.util.ns :refer [topo-sort]]
   [eastwood.util.parallel :refer [partitioning-pmap]]
   [eastwood.version :as version])
  (:import
   (java.io File)))

(declare effective-namespaces setup-lint-paths)

;; Note: Linters below with nil for the value of the key :fn,
;; e.g. :no-ns-form-found, can be enabled/disabled from the opt map
;; like other linters, but they are a bit different in their
;; implementation as they have no separate function to call on each
;; namespace. They are done very early, and are not specific to a
;; namespace.

;; :ignore-faults-from-foreign-macroexpansions? is selectively added here for the specific linters where:
;;   * the warning can plausibly arise from a foreign macroexpansion, and
;;   * the warning isn't too important to omit.
(def linter-info
  [{:name :no-ns-form-found
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#no-ns-form-found",
    :fn (constantly nil)}

   {:name :non-clojure-file,
    :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#non-clojure-file",
    :fn (constantly nil)}

   {:name :misplaced-docstrings,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#misplaced-docstrings",
    :fn misc/misplaced-docstrings}

   {:name :deprecations,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#deprecations",
    :fn deprecated/deprecations
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :duplicate-params,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#duplicate-params",
    :fn typos/duplicate-params
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :redefd-vars,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#redefd-vars",
    :fn misc/redefd-vars
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :def-in-def,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#def-in-def",
    :fn misc/def-in-def
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :wrong-arity,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#wrong-arity",
    :fn misc/wrong-arity
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :bad-arglists,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#bad-arglists",
    :fn misc/bad-arglists
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :local-shadows-var,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#local-shadows-var",
    :fn misc/local-shadows-var
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :suspicious-test,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#suspicious-test",
    :fn typos/suspicious-test}

   {:name :suspicious-expression,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#suspicious-expression",
    :fn typos/suspicious-expression
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :constant-test,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#constant-test",
    :fn typos/constant-test
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :unused-ret-vals,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#unused-ret-vals",
    :fn unused/unused-ret-vals
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :unused-ret-vals-in-try,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#unused-ret-vals",
    :fn unused/unused-ret-vals-in-try
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :unused-private-vars,
    :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#unused-private-vars",
    :fn unused/unused-private-vars}

   {:name :unused-fn-args,
    :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#unused-fn-args",
    :fn unused/unused-fn-args
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :unused-locals,
    :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#unused-locals",
    :fn unused/unused-locals
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :unused-namespaces,
    :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#unused-namespaces",
    :fn unused/unused-namespaces}

   {:name :unused-meta-on-macro,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#unused-meta-on-macro",
    :fn unused/unused-meta-on-macro
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :unlimited-use,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#unlimited-use",
    :fn misc/unlimited-use}

   {:name :wrong-ns-form,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#wrong-ns-form",
    :fn misc/wrong-ns-form}

   {:name :wrong-pre-post,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#wrong-pre-post",
    :fn typos/wrong-pre-post}

   {:name :wrong-tag,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#wrong-tag",
    :fn typetags/wrong-tag}

   {:name :keyword-typos,
    :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#keyword-typos",
    :fn typos/keyword-typos
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :non-dynamic-earmuffs,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#non-dynamic-earmuffs"
    :fn misc/non-dynamic-earmuffs
    :ignore-faults-from-foreign-macroexpansions? true}

   {:name :implicit-dependencies,
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#implicit-dependencies",
    :fn implicit-dependencies/implicit-dependencies}

   {:name :reflection
    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#reflection",
    ;; NOTE :ignore-faults-from-foreign-macroexpansions? is useless for this specific linter,
    ;; since reflection detection doesn't work at tools.analyzer level, so one doesn't get to inspect macroexpasions for this purpose.
    :fn reflection/linter}

   {:name :performance
    :enabled-by-default false
    :url "https://github.com/jonase/eastwood#performance",
    ;; NOTE :ignore-faults-from-foreign-macroexpansions? is useless for this specific linter,
    ;; since reflection detection doesn't work at tools.analyzer level, so one doesn't get to inspect macroexpasions for this purpose.
    :fn performance/linter}

   {:name :boxed-math
    ;; Disabled by default as it's not customary or excessively justified to always fix these:
    :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#boxed-math",
    ;; NOTE :ignore-faults-from-foreign-macroexpansions? is useless for this specific linter,
    ;; since reflection detection doesn't work at tools.analyzer level, so one doesn't get to inspect macroexpasions for this purpose.
    :fn boxed-math/linter}])

(def linter-name->info
  (->> (for [{:keys [name] :as info} linter-info]
         [name info])
       (into {})))

(def default-linters
  (->> linter-info
       (filter :enabled-by-default)
       (map :name)))

(def all-linters
  (->> linter-info
       (map :name)))

(defn namespace-info [ns-sym cwd-file]
  (let [uri (util/to-uri (analyze-ns/uri-for-ns ns-sym))]
    (merge
     {:namespace-sym ns-sym}
     (util/file-warn-info uri cwd-file))))

(defn- handle-lint-result [linter
                           {original-uri-or-file-name :uri-or-file-name
                            :as ns-info}
                           {:keys [exclude-namespaces]
                            project-namespaces :eastwood/project-namespaces}
                           {:keys [loc uri-or-file-name] :as result}]
  {:pre [(set? project-namespaces)]}
  (if (and uri-or-file-name
           original-uri-or-file-name
           (not= (str uri-or-file-name)
                 (str original-uri-or-file-name))
           (not (contains? (set/difference project-namespaces (set exclude-namespaces))
                           (->> uri-or-file-name
                                io/reader
                                slurp
                                (reader/read-string {:read-cond :allow :features #{:clj}})
                                parse/name-from-ns-decl))))
    nil
    {:kind :lint-warning,
     :warn-data (merge result
                       ns-info
                       (select-keys loc #{:file :line :column})
                       (when-let [url (:url linter)]
                         {"warning-details-url" url})
                       (when uri-or-file-name ;; let linters override the affected file (not often needed; it is for reflection warnings)
                         {:uri-or-file-name uri-or-file-name}))}))

(defn ignore-fault? [ignored-faults {{:keys [namespace-sym column line linter]} :warn-data}]
  ;; I've made the mistake in the past of having warn-data locs as strings:
  (when line
    (assert (number? line)))
  (when column
    (assert (number? column)))
  (let [matches (get-in ignored-faults [linter namespace-sym])
        matches (cond-> matches
                  (not (sequential? matches))
                  ;; The syntax is a bit lenient - generally we expect vectors, but if a map was passed, we simply wrap it:
                  vector)]
    (->> matches
         (some (fn [match]
                 (let [candidates (cond-> #{true
                                            {:line line :column column}}
                                    (and match
                                         (not (true? match))
                                         (not (:column match)))
                                    (conj {:line line}))]
                   (candidates match))))
         (boolean))))

(defn- run-linter [linter analyze-results ns-sym {expanded-exclude-linters :eastwood/exclude-linters
                                                  :as opts}]
  {:pre [expanded-exclude-linters]}
  (let [ns-info (namespace-info ns-sym (:cwd opts))]
    (try
      (->> ((:fn linter) analyze-results opts)
           (keep (partial handle-lint-result linter ns-info opts))
           (remove (partial ignore-fault? (:ignored-faults opts)))
           (remove (fn [{{:keys [linter kind]} :warn-data}]
                     {:pre [linter]}
                     (if-not kind
                       false
                       (util/excludes-kind? [linter kind] expanded-exclude-linters))))
           doall)
      (catch Throwable e
        [{:kind :lint-error
          :warn-data (format "Exception thrown by linter %s on namespace %s" (:name linter) ns-sym)
          :exception e}]))))

(defn lint-ns* [ns-sym analyze-results opts linter]
  (let [[results elapsed] (util/timeit
                            (run-linter linter analyze-results ns-sym opts))]
    (->> results
         (group-by :kind)
         (merge {:elapsed elapsed
                 :linter linter}))))

(def ^{:dynamic true
       :arglists '([dir-name-strs modified-since search-mismatches? context-name])}
  *nss-in-dirs* nil)

(declare nss-in-dirs)

(defn with-memoization-bindings* [f]
  (binding [eastwood.copieddeps.dep9.clojure.tools.namespace.file/*read-file-ns-decl*
            (memoize eastwood.copieddeps.dep9.clojure.tools.namespace.file/read-file-ns-decl)

            eastwood.copieddeps.dep9.clojure.tools.namespace.parse/*read-ns-decl*
            (memoize eastwood.copieddeps.dep9.clojure.tools.namespace.parse/read-ns-decl)

            analyze-ns/*analyze+eval*
            (memoize (fn [form env passes-opts _form-meta _ns-str] ;; has extra args for safe memoization
                       (jvm/analyze+eval form env passes-opts)))

            *nss-in-dirs*
            (memoize nss-in-dirs)]
    (f)))

(defmacro with-memoization-bindings
  {:style/indent 0}
  [& forms]
  `(with-memoization-bindings* (fn []
                                 (do
                                   ~@forms))))

(defn lint-ns [ns-sym linters {:keys [exclude-namespaces
                                      exclude-linters
                                      namespaces
                                      source-paths
                                      test-paths]
                               :as opts}]
  (with-memoization-bindings
    (let [namespaces (conj (set namespaces) ns-sym)
          effective-linters (some->> linters
                                     (remove (set exclude-linters))
                                     (keep linter-name->info))
          all-source-paths (setup-lint-paths []
                                             source-paths
                                             test-paths)
          lint-paths (setup-lint-paths namespaces
                                       source-paths
                                       test-paths)
          opts (cond-> opts
                 ;; this key is generally present, except when invoking `lint-ns` directly (which is a less usual API):
                 (not (:eastwood/project-namespaces opts))
                 (assoc :eastwood/project-namespaces (:project-namespaces (effective-namespaces exclude-namespaces
                                                                                                namespaces
                                                                                                all-source-paths
                                                                                                lint-paths
                                                                                                0)))
                 ;; same
                 (not (:eastwood/linter-info opts))
                 (assoc :eastwood/linter-info linter-info))
          opts (assoc opts :eastwood/linting-boxed-math? (contains? (into #{} (map :name) effective-linters)
                                                                    :boxed-math))
          [result elapsed] (util/timeit
                             (analyze-ns/analyze-ns ns-sym :opt opts))
          {:keys [analyze-results exception exception-phase exception-form]} result]
      {:ns ns-sym
       :analysis-time elapsed
       :lint-results (some->> effective-linters
                              (@util/linter-executor-atom (partial lint-ns* ns-sym analyze-results opts)))
       :analyzer-exception (when exception
                             (msgs/report-analyzer-exception exception exception-phase exception-form ns-sym))})))

(defn unknown-ns-keywords [namespaces known-ns-keywords desc]
  (let [keyword-set (set (filter keyword? namespaces))
        unknown-ns-keywords (set/difference keyword-set known-ns-keywords)]
    (when-not (empty? unknown-ns-keywords)
      (throw (ex-info "unknown-ns-keywords"
                      {:err :unknown-ns-keywords,
                       :err-data {:for-option desc
                                  :unknown-ns-keywords unknown-ns-keywords
                                  :allowed-ns-keywords known-ns-keywords}})))))

(defn filename-to-ns [fname]
  (-> fname
      (util/separate-suffix (:extensions find/clj))
      first
      (str/replace "_" "-")
      (str/replace File/separator ".")
      symbol))

(defn ns-to-filename-set [namespace extensions]
  (let [basename (-> namespace
                     str
                     (str/replace "-" "_")
                     (str/replace "." File/separator))]
    (set (map #(str basename %) extensions))))

(defn find-mismatch [dir [f namespace]]
  (let [dir-with-sep (str dir File/separator)
        fname (util/remove-prefix (str f) dir-with-sep)
        desired-ns (filename-to-ns fname)
        desired-fname-set (ns-to-filename-set namespace
                                              (:extensions find/clj))]
    (when-not (contains? desired-fname-set fname)
      [fname {:dir dir
              :namespace namespace,
              :recommended-fnames desired-fname-set,
              :recommended-namespace desired-ns}])))

(defn find-mismatches [[dir {:keys [filemap]}]]
  (->> filemap
       (partitioning-pmap (fn [f]
                            (find-mismatch dir f)))
       (keep identity)))

(defn filename-namespace-mismatches [dir-name-strs]
  (->> dir-name-strs
       (map (fn [dir-name-str]
              [dir-name-str
               (-> [dir-name-str]
                   (#'dir/find-files find/clj)
                   :clojure-files
                   (#'file/files-and-deps (:read-opts find/clj)))]))
       (mapcat find-mismatches)
       (into {})))

(defn nss-in-dirs [dir-name-strs modified-since search-mismatches? context-name]
  {:pre [(instance? Boolean search-mismatches?)
         (string? context-name)]}
  (let [dir-name-strs (set (map util/canonical-filename dir-name-strs))
        mismatches    (if-not search-mismatches?
                        []
                        (filename-namespace-mismatches dir-name-strs))]
    (when (seq mismatches)
      (throw (ex-info (str "Detected a mismatch between filenames and namespaces while "
                           context-name)
                      {:err      :namespace-filename-mismatch
                       :err-data {:mismatches mismatches}})))
    (let [tracker (assoc (track/tracker) ::dir/time modified-since)
          tracker (if (seq dir-name-strs)
                    (dir/scan-dirs tracker dir-name-strs)
                    ;; Use empty tracker if dir-name-strs is empty.
                    ;; Calling dir/scan-all will use complete Java
                    ;; classpath if called with an empty sequence.
                    tracker)]
      {:dirs              dir-name-strs
       :non-clojure-files (::dir/non-clojure-files tracker)
       :files             (set (::dir/files tracker))
       :file-map          (::file/filemap tracker)
       :namespaces        (::track/load tracker)
       :deps              (::track/deps tracker)})))

(defn expand-ns-keywords
  "Expand any keyword in `namespaces` with values from `expanded-namespaces`"
  [expanded-namespaces namespaces]
  (->> namespaces
       (mapcat (fn [x]
                 (get expanded-namespaces x [x])))))

(defn setup-lint-paths
  "Returns a map containing `:source-path` and `:test-paths` which
  contains the set of values in each. If both `source-paths` and `test-paths`
  are empty then `:source-path` is set to all the directories on the classpath,
  while `:test-paths` is the empty set."
  [namespaces source-paths test-paths]
  (if (or (seq source-paths)
          (seq test-paths)
          ;; :source-paths / :test-paths don't count for this criterion:
          (seq (remove keyword? namespaces)))
    {:source-paths (set source-paths)
     :test-paths (set test-paths)}
    {:source-paths (->> (or (seq (classpath/classpath-directories))
                            ;; fallback, because the above can fail in presence of certain libs or scenarios:
                            (classpath/system-classpath))
                        (filter (fn [^File f]
                                  (-> f .isDirectory)))
                        ;; remove dirs representing Lein checkouts
                        ;; (which cannot be directly detected as symlinks, since Lein resolves them first):
                        (remove util/dir-outside-root-dir?)
                        ;; resources (whether vanilla, dev-only or test-only) should not be analyzed,
                        ;; or account for `:ignore-faults-from-foreign-macroexpansions?`:
                        (remove (fn [^File f]
                                  (let [s (-> f .toString)]
                                    (or (-> s (.contains "resources"))
                                        (-> s (.contains "target"))
                                        ;; https://github.com/jonase/eastwood/issues/409
                                        (-> s (.contains ".gitlibs"))))))
                        (set))
     :test-paths #{}}))

;; If you do not specify :namespaces in the options, it defaults to
;; the same as if you specified [:source-paths :test-paths]. If you
;; specify a list of namespaces explicitly, perhaps mingled with
;; occurrences of :source-paths and/or :test-paths, then the
;; namespaces will be linted in the order you specify, even if this
;; violates dependency order according to the ns form contents. No
;; warning will be detected or printed about this.

;; TBD: It would be nice if the default behavior would instead be to
;; put the specified namespaces into an order that honors all declared
;; dependencies between namespaces. If this is implemented, it might
;; also be nice (perhaps only for debugging purposes) to implement a
;; keyword :force-order that preserves the specified namespace order
;; regardless of dependencies.

;; TBD: Abort with an easily understood error message if a namespace
;; is given that cannot be found.

(defn effective-namespaces [exclude-namespaces
                            namespaces
                            {all-source-paths :source-paths
                             all-test-paths :test-paths}
                            {:keys [source-paths test-paths]}
                            modified-since]
  ;; If keyword :source-paths occurs in namespaces or
  ;; excluded-namespaces, replace it with all namespaces found in
  ;; the directories in (:source-paths opts), in an order that
  ;; honors dependencies, and similarly for :test-paths.
  ;; nss-in-dirs traverses part of the file system, so only call it
  ;; once for each of :source-paths and :test-paths, and only if
  ;; needed.
  (let [all-ns (concat namespaces exclude-namespaces)
        sp (when (some #{:source-paths} all-ns)
             (*nss-in-dirs* source-paths modified-since true "loading :source-paths from `all-ns`"))
        tp (when (some #{:test-paths} all-ns)
             (*nss-in-dirs* test-paths modified-since true "loading :test-paths from `all-ns`"))
        expanded-namespaces {:source-paths (:namespaces sp)
                             :test-paths (:namespaces tp)}
        excluded-namespaces (set (expand-ns-keywords expanded-namespaces
                                                     exclude-namespaces))
        corpus (->> namespaces
                    (expand-ns-keywords expanded-namespaces)
                    (set))
        namespaces-from-refresh-paths (if (System/getProperty "eastwood.internal.dev-profile-active")
                                        ;; remove these, which can make profiling unrealistically slow
                                        ;; (because of excessive calls to t.n file-ns-decl):
                                        #{}
                                        (or (some-> 'clojure.tools.namespace.repl/refresh-dirs
                                                    resolve
                                                    deref
                                                    seq
                                                    (*nss-in-dirs* 0 true "loading tools.namespace `refresh-dirs`")
                                                    :namespaces
                                                    set)
                                            #{}))
        all-project-namespaces (set/union corpus ;; namespaces explicitly asked to be linted
                                          (some-> all-source-paths ;; source-paths per Eastwood/Lein config
                                                  seq

                                                  (*nss-in-dirs* 0 false "loading :source-paths for `all-project-namespaces`")
                                                  :namespaces
                                                  set)
                                          (some-> all-test-paths ;; test-paths per Eastwood/Lein config
                                                  seq
                                                  (*nss-in-dirs* 0 false "loading :test-paths for `all-project-namespaces`")
                                                  :namespaces
                                                  set)
                                          ;; t.n integration:
                                          namespaces-from-refresh-paths)
        project-namespaces (set/union corpus ;; namespaces explicitly asked to be linted
                                      (some-> source-paths ;; source-paths per Eastwood/Lein config
                                              seq
                                              (*nss-in-dirs* 0 true "loading :source-paths for `project-namespaces`")
                                              :namespaces
                                              set)
                                      (some-> test-paths ;; test-paths per Eastwood/Lein config
                                              seq
                                              (*nss-in-dirs* 0 true "loading :test-paths for `project-namespaces`")
                                              :namespaces
                                              set)
                                      ;; t.n integration:
                                      namespaces-from-refresh-paths)
        non-lintable-namespaces-from-t-n (set/difference namespaces-from-refresh-paths
                                                         namespaces)]

    ;; If the t.n refresh-dirs were set, all transitively-depended-on namespaces
    ;; that are *not* part of the namespaces to be linted will be `require`d.
    ;; This way, any reflection warnings from those will not affect Eastwood results:
    (when-let [corpus (seq non-lintable-namespaces-from-t-n)]
      (binding [*warn-on-reflection* false]
        (apply require corpus)))

    {;; what will be linted:
     :namespaces (->> corpus
                      (remove excluded-namespaces)
                      (topo-sort project-namespaces))
     ;; the set of project namespaces, or `namespaces` if :namespaces were explicitly provided and non-empty:
     :project-namespaces project-namespaces
     ;; the set of project namespaces, regardless of the `namespaces` value.
     ;; Linter faults caused by namespaces outside this set may be ignored:
     :all-project-namespaces all-project-namespaces
     :test-deps (:deps tp)
     :src-deps (:deps sp)
     :dirs (concat (:dirs sp) (:dirs tp))
     :files (set (concat (:files sp) (:files tp)))
     :file-map (merge (:file-map sp) (:file-map tp))
     :non-clojure-files (set/union (:non-clojure-files sp)
                                   (:non-clojure-files tp))}))

(defn replace-linter-keywords [linters all-linters default-linters]
  (mapcat (fn [x]
            (cond (= :all x) all-linters
                  (= :default x) default-linters
                  :else [x]))
          linters))

(defn linter-seq->set [linter-seq]
  (set (replace-linter-keywords linter-seq all-linters default-linters)))

(defn effective-linters
  [{:keys [linters exclude-linters add-linters disable-linter-name-checks]}
   linter-name->info _default-linters]
  (let [linters-orig (linter-seq->set linters)
        excluded-linters (linter-seq->set exclude-linters)
        add-linters (linter-seq->set add-linters)
        linters-requested (-> (set/difference linters-orig excluded-linters)
                              (set/union add-linters))
        known-linters (set (keys linter-name->info))
        unknown-linters (set/difference (set/union linters-requested
                                                   (into #{}
                                                         (filter keyword?)
                                                         excluded-linters))
                                        known-linters)]
    (when (and (seq unknown-linters)
               (not disable-linter-name-checks))
      (throw (ex-info "unknown-linter"
                      {:err :unknown-linter,
                       :err-data {:unknown-linters unknown-linters
                                  :known-linters known-linters}})))

    (set/intersection linters-requested known-linters)))

(defn- dirs-scanned [reporter cwd dirs]
  (some->> dirs
           seq ;; shortcircuit printing
           (map #(util/file-warn-info % cwd))
           (map :uri-or-file-name)
           (str/join " ")
           (str "Directories scanned for source files: ")
           (reporting/note reporter)))

(defn- lint-namespace [_reporter namespace linters opts]
  (try
    (let [{:keys [analyzer-exception lint-results analysis-time]} (lint-ns namespace linters opts)]
      {:namespace [namespace]
       :lint-warnings (mapcat :lint-warning lint-results)
       :lint-errors (mapcat :lint-error lint-results)
       :lint-times [(into {} (map (juxt (comp :name :linter) :elapsed) lint-results))]
       :analysis-time [analysis-time]
       :analyzer-exception (when analyzer-exception
                             [analyzer-exception])})
    (catch RuntimeException e
      {:lint-runtime-exception [e]
       :namespace [namespace]})))

(defn debug-namespaces-to-be-reported [reporter namespaces]
  (reporting/debug reporter :ns (format "Namespaces to be linted:"))
  (doseq [n namespaces]
    (reporting/debug reporter :ns (format "    %s" n))))

(defmulti lint-namespaces (fn [& args] (first args)))

(defmethod lint-namespaces :none [_parallel? reporter {:keys [namespaces]} linters opts]
  (let [stop-on-exception? (not (:continue-on-exception opts))]
    (reduce (fn [results namespace]
              (reporting/note reporter (str "== Linting " namespace " =="))
              (let [result (lint-namespace reporter namespace linters opts)
                    results (conj results result)]
                (if (and stop-on-exception?
                         (or (:lint-runtime-exception result)
                             (:analyzer-exception result)))
                  (do
                    (reporting/stopped-on-exception reporter namespaces results result (:rethrow-exceptions? opts))
                    (reduced results))
                  (do
                    (reporting/report-result reporter result)
                    results))))
            []
            namespaces)))

(defmethod lint-namespaces :naive [_parallel? reporter x y z]
  (reporting/note reporter ":parallel? :naive is deprecated.
It has never been a safe option; code analysis should't be performed in parallel.
Eastwood has other forms of effective, safe parallelism now. Falling back to sequential analysis.")
  (lint-namespaces :none reporter x y z))

(defn eastwood-core
  "Lint a sequence of namespaces using a specified collection of linters.
  Side-effects:

  * Reads source files, analyzes them, generates Clojure forms from
  analysis results, and evals those forms (i.e. if `require`ing your source files launches the
  missiles, so will this).

  * `Does create-ns` on all namespaces specified, even if an exception
  during linting causes this function to return before reading all of
  them.

  * Should not print output to any output files/streams/etc, unless
  this occurs due to evaling the code being linted."
  [reporter
   opts
   cwd
   {:keys [namespaces dirs files file-map non-clojure-files all-project-namespaces]
    :as effective-namespaces}
   lint-paths
   linters]

  (->> lint-paths
       (vals)
       (reduce into #{}) ;; merge :source-paths and :test-paths
       (sort)
       (dirs-scanned reporter cwd))

  (let [no-ns-forms (misc/no-ns-form-found-files dirs files file-map linters cwd opts)
        non-clojure-files (misc/non-clojure-files non-clojure-files linters cwd)]

    (assert (seq linters) "No :linters configured")

    (reporting/report-result reporter no-ns-forms)
    (reporting/report-result reporter non-clojure-files)

    (spit ".eastwood" (System/currentTimeMillis))

    (reporting/debug-namespaces reporter namespaces)

    ;; Create all namespaces to be analyzed. This can help in some
    ;; (unusual) situations, such as when namespace A requires B,
    ;; so Eastwood analyzes B first, but eval'ing B assumes that
    ;; A's namespace has been created first because B contains (alias 'x 'A):
    (doseq [n namespaces]
      (create-ns n))

    (->> (lint-namespaces (:parallel? opts)
                          reporter
                          effective-namespaces
                          linters
                          (assoc opts
                                 :eastwood/project-namespaces all-project-namespaces
                                 :eastwood/linter-info linter-info))
         (into [no-ns-forms non-clojure-files]))))

(def default-builtin-config-files
  ["clojure.clj"
   "clojure-contrib.clj"
   "third-party-libs.clj"])

(def default-opts {:cwd (.getCanonicalFile (io/file "."))
                   :linters default-linters
                   :debug #{}
                   :only-modified false
                   :modified-since 0
                   :parallel? :none
                   :source-paths #{}
                   :test-paths #{}
                   :namespaces #{:source-paths :test-paths}
                   :exclude-namespaces #{}
                   :exclude-linters #{;; exclude only a sub :kind for a specific linter:
                                      [:suspicious-test :second-arg-is-not-string]}
                   :config-files #{}
                   :builtin-config-files default-builtin-config-files
                   :rethrow-exceptions? false
                   :ignored-faults {}
                   :ignore-faults-from-foreign-macroexpansions? true})

(defn last-options-map-adjustments [opts _reporter]
  (let [{:keys [_namespaces] :as opts} (merge default-opts opts)
        distinct* (fn [x] ;; distinct but keeps original coll type
                    (->> (into (empty x) (distinct) x)
                         (into (empty x)))) ;; restore list order
        {:keys [exclude-linters]
         :as opts} (-> opts
                       (update :debug set)
                       (update :namespaces distinct*)
                       (update :source-paths set)
                       (update :test-paths set)
                       (update :exclude-namespaces set))
        ;; Changes below override anything in the caller-provided
        ;; options map.
        opts (assoc opts
                    :warning-enable-config
                    (util/init-warning-enable-config
                     (:builtin-config-files opts)
                     (:config-files opts) opts)

                    :eastwood/exclude-linters
                    (util/expand-exclude-linters exclude-linters))]
    ;; throw an error if any keywords appear in the namespace lists
    ;; that are not recognized.
    (unknown-ns-keywords (:namespaces opts) #{:source-paths :test-paths} ":namespaces")
    (unknown-ns-keywords (:exclude-namespaces opts) #{:source-paths :test-paths} ":exclude-namespaces")
    (let [ts-file (File. ".eastwood")]
      (assoc opts :modified-since (if (and (.exists ts-file) (:only-modified opts))
                                    (edn/read-string (slurp ts-file))
                                    0)))))

(defn summary [results]
  (apply merge-with into results))

(defn counted-summary [summary]
  {:warning-count (count (:lint-warnings summary))
   :error-count (+ (count (:lint-errors summary))
                   (count (:lint-runtime-exception summary))
                   (count (:analyzer-exception summary)))
   :lint-time (apply + (mapcat vals (:lint-times summary)))
   :analysis-time (apply + (:analysis-time summary))})

(defn make-report [reporter
                   ^long start-time
                   {:keys [namespaces]}
                   {:keys [warning-count error-count]}]
  (reporting/note reporter (format "== Linting done in %d ms ==" (- (System/currentTimeMillis)
                                                                    start-time)))
  (reporting/note reporter (format "== Warnings: %d. Exceptions thrown: %d"
                                   warning-count
                                   error-count))
  (let [error-count (long error-count)
        warning-count (long warning-count)
        has-errors? (> error-count 0)
        nothing-was-linted? (-> namespaces count zero?)]
    (when nothing-was-linted?
      (reporting/note reporter "== No namespaces were linted. This might indicate a misconfiguration."))
    {:some-warnings (or (> warning-count 0)
                        has-errors?
                        nothing-was-linted?)
     :some-errors has-errors?}))

(defn eastwood
  ([opts]
   (eastwood opts (reporting/printing-reporter opts)))

  ([{:keys [rethrow-exceptions?] :as opts} reporter]
   (with-memoization-bindings
     (try
       (reporting/note reporter (version/version-string))
       (let [start-time (System/currentTimeMillis)
             {:keys [exclude-namespaces
                     namespaces
                     source-paths
                     test-paths
                     modified-since
                     cwd] :as opts} (last-options-map-adjustments opts reporter)
             all-source-paths (setup-lint-paths [] source-paths test-paths)
             lint-paths (setup-lint-paths namespaces source-paths test-paths)
             namespaces-info (effective-namespaces exclude-namespaces
                                                   namespaces
                                                   all-source-paths
                                                   lint-paths
                                                   modified-since)
             linter-info (select-keys opts [:linters :exclude-linters :add-linters :disable-linter-name-checks])]
         (reporting/debug reporter :var-info (with-out-str
                                               (util/print-var-info-summary @typos/var-info-map-delayed)))
         (reporting/debug reporter :compare-forms
                          "Writing files forms-read.txt and forms-emitted.txt")
         (->> default-linters
              (effective-linters linter-info linter-name->info)
              (eastwood-core reporter opts cwd namespaces-info lint-paths)
              summary
              counted-summary
              (make-report reporter start-time namespaces-info)))
       (catch Exception e
         (reporting/show-error reporter (or (ex-data e) e))
         (if rethrow-exceptions?
           (throw e)
           {:some-warnings true
            :some-errors true}))))))

(defn eastwood-from-cmdline [opts]
  (let [ret (eastwood opts)]
    (if (:some-warnings ret)
      ;; Exit with non-0 exit status for the benefit of any shell
      ;; scripts invoking Eastwood that want to know if there were no
      ;; errors, warnings, or exceptions.
      ((exit-fn) (:forced-exit-code opts 1))
      ;; Eastwood does not use future, pmap, or clojure.shell/sh now
      ;; (at least not yet), but it may evaluate code that does when
      ;; linting a project. Call shutdown-agents to avoid the
      ;; 1-minute 'hang' that would otherwise occur.
      (shutdown-agents))))

(defn lint
  "Invoke Eastwood from REPL or other Clojure code, and return a map
  containing these keys:

  :warnings - a sequence of maps representing individual warnings.
      The warning map contents are documented below.

  :err - nil if there were no exceptions thrown or other errors that
      stopped linting before it completed. A keyword identifying a
      kind of error if there was. See the source file
      src/eastwood/lint.clj inside Eastwood for defmethod's of
      error-msg. Each is specialized on a keyword value that is one
      possible value the :err key can take. The body of each method
      shows how Eastwood shows to the user each kind of error when it
      is invoked from the command line via Leiningen, serves as a kind
      of documentation for what the value of the :err-data key
      contains.

  :err-data - Some data describing the error if :err's value is not
      nil. See :err above for where to find out more about its
      contents.

  :versions - A nested map with its own keys containing information
      about JVM, Clojure, and Eastwood versions.

  Keys in a warning map:

  :uri-or-file-name - string containing file name where warning
      occurs, relative to :cwd directory of options map, if it is a
      file inside of that directory, or a URI object,
      e.g. \"cases/testcases/f02.clj\"

  :line - line number in file for warning, e.g. 20. The first line in
      the file is 1, not 0. Note: In some cases this key may not be
      present, or the value may be nil. This is an area where
      Eastwood will probably improve in the future, but best to handle
      it for now, perhaps by replacing it with line 1 as a
      placeholder.

  :column - column number in file for warning, e.g. 42. The first
      character of each line is column 1, not 0. Same comments apply
      for :column as for :line.

  :linter - keyword identifying the linter, e.g. :def-in-def

  :msg - string describing the warning message, e.g. \"There is a def
      of i-am-inner-defonce-sym nested inside def
      i-am-outer-defonce-sym\"

  :uri - object with class URI of the file, *or* a URI within a JAR
       file, e.g. #<URI file:/Users/jafinger/clj/eastwood/0.2.0/eastwood/cases/testcases/f02.clj>

  :namespace-sym - symbol containing namespace, e.g. testcases.f02,

  :file - string containing resource name, relative to some
      unspecified path in the Java classpath,
      e.g. \"testcases/f02.clj\""
  ([opts] (lint opts (reporting/silent-reporter opts)))
  ([opts reporter]
   (try
     (let [{:keys [exclude-namespaces
                   namespaces
                   source-paths
                   test-paths
                   modified-since
                   cwd] :as opts} (last-options-map-adjustments opts reporter)
           all-source-paths (setup-lint-paths [] source-paths test-paths)
           lint-paths (setup-lint-paths namespaces source-paths test-paths)
           namespaces-info (effective-namespaces exclude-namespaces
                                                 namespaces
                                                 all-source-paths
                                                 lint-paths
                                                 modified-since)
           linter-info (select-keys opts [:linters :exclude-linters :add-linters :disable-linter-name-checks])
           {:keys [error error-data
                   lint-warnings
                   namespace]}
           (->> (effective-linters linter-info linter-name->info default-linters)
                (eastwood-core reporter opts cwd namespaces-info lint-paths)
                summary)]
       {:namespaces namespace
        :warnings (seq lint-warnings)
        :err error
        :err-data error-data
        :versions (version/versions)})
     (catch Exception e
       {:err (or (ex-data e) e)
        :versions (version/versions)}))))

(defn insp
  "Read, analyze, and eval a file specified by namespace as a symbol,
  e.g. 'testcases.f01. Return a value that has been 'cleaned up', by
  removing some keys from ASTs, so that it is more convenient to call
  clojure.inspector/inspect-tree on it. Example in REPL:

  (require '[eastwood.lint :as l] '[clojure.inspector :as i])
  (i/inspect-tree (l/insp 'testcases.f01))"
  [nssym]
  (let [a (analyze-ns/analyze-ns nssym :opt {:callback (fn [_]) :debug #{}})]
    (update-in a [:analyze-results :asts]
               (fn [ast] (mapv util/clean-ast ast)))))

(defn -main
  ([]
   (-> (->> default-opts
            (keep (fn [[k v]]
                    (when-not (->> [File]
                                   (some (fn [c]
                                           (instance? c v))))
                      [k v])))
            (into {}))
       pr-str
       -main))

  ([& opts]
   (if (and
        (= 1 (count opts))
        (string? (first opts)))
     (eastwood-from-cmdline (edn/read-string (first opts)))
     (let [parsed (->> opts (interpose " ") (apply str) edn/read-string)]
       (eastwood-from-cmdline parsed)))))
