(ns eastwood.lint
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [eastwood.analyze-ns :as analyze]
            [eastwood.copieddeps.dep11.clojure.java.classpath :as classpath]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.dir :as dir]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.file :as file]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.find :as find]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.track :as track]
            [eastwood.error-messages :as msgs]
            [eastwood.linters.deprecated :as deprecated]
            [eastwood.linters.implicit-dependencies :as implicit-dependencies]
            [eastwood.linters.misc :as misc]
            [eastwood.linters.typetags :as typetags]
            [eastwood.linters.typos :as typos]
            [eastwood.linters.unused :as unused]
            [eastwood.reporting-callbacks :as reporting]
            [eastwood.util :as util]
            [eastwood.version :as version])
  (:import java.io.File))

;; Note: Linters below with nil for the value of the key :fn,
;; e.g. :no-ns-form-found, can be enabled/disabled from the opt map
;; like other linters, but they are a bit different in their
;; implementation as they have no separate function to call on each
;; namespace.  They are done very early, and are not specific to a
;; namespace.

(def linter-info
  [
   {:name :no-ns-form-found,          :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#no-ns-form-found",
    :fn (constantly nil)}
   {:name :non-clojure-file,          :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#non-clojure-file",
    :fn (constantly nil)}
   {:name :misplaced-docstrings,      :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#misplaced-docstrings",
    :fn misc/misplaced-docstrings}
   {:name :deprecations,              :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#deprecations",
    :fn deprecated/deprecations}
   {:name :duplicate-params,          :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#duplicate-params",
    :fn typos/duplicate-params}
   {:name :redefd-vars,               :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#redefd-vars",
    :fn misc/redefd-vars}
   {:name :def-in-def,                :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#def-in-def",
    :fn misc/def-in-def}
   {:name :wrong-arity,               :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#wrong-arity",
    :fn misc/wrong-arity}
   {:name :bad-arglists,              :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#bad-arglists",
    :fn misc/bad-arglists}
   {:name :local-shadows-var,         :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#local-shadows-var",
    :fn misc/local-shadows-var}
   {:name :suspicious-test,           :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#suspicious-test",
    :fn typos/suspicious-test}
   {:name :suspicious-expression,     :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#suspicious-expression",
    :fn typos/suspicious-expression}
   {:name :constant-test,             :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#constant-test",
    :fn typos/constant-test}
   {:name :unused-ret-vals,           :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#unused-ret-vals",
    :fn unused/unused-ret-vals}
   {:name :unused-ret-vals-in-try,    :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#unused-ret-vals",
    :fn unused/unused-ret-vals-in-try}
   {:name :unused-private-vars,       :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#unused-private-vars",
    :fn unused/unused-private-vars}
   {:name :unused-fn-args,            :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#unused-fn-args",
    :fn unused/unused-fn-args}
   {:name :unused-locals,             :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#unused-locals",
    :fn unused/unused-locals}
   {:name :unused-namespaces,         :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#unused-namespaces",
    :fn unused/unused-namespaces}
   {:name :unused-meta-on-macro,      :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#unused-meta-on-macro",
    :fn unused/unused-meta-on-macro}
   {:name :unlimited-use,             :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#unlimited-use",
    :fn misc/unlimited-use}
   {:name :wrong-ns-form,             :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#wrong-ns-form",
    :fn misc/wrong-ns-form}
   {:name :wrong-pre-post,            :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#wrong-pre-post",
    :fn typos/wrong-pre-post}
   {:name :wrong-tag,                 :enabled-by-default true,
    :url "https://github.com/jonase/eastwood#wrong-tag",
    :fn typetags/wrong-tag}
   {:name :keyword-typos,             :enabled-by-default false,
    :url "https://github.com/jonase/eastwood#keyword-typos",
    :fn typos/keyword-typos}
   {:name :non-dynamic-earmuffs,      :enabled-by-default false,
    :url nil,
    :fn misc/non-dynamic-earmuffs}
   {:name :implicit-dependencies,     :enabled-by-default true,
    :url nil,
    :fn implicit-dependencies/implicit-dependencies}])


(def linter-name->info (into {} (for [{:keys [name] :as info} linter-info]
                                  [name info])))

(def default-linters
  (->> linter-info
       (filter :enabled-by-default)
       (map :name)))

(def all-linters
  (->> linter-info
       (map :name)))

(defn namespace-info [ns-sym cwd-file]
  (let [uri (util/to-uri (analyze/uri-for-ns ns-sym))]
    (merge
     {:namespace-sym ns-sym}
     (util/file-warn-info uri cwd-file))))


(defn- handle-lint-result [linter ns-info {:keys [msg loc] :as result}]
  {:kind :lint-warning,
   :warn-data (merge result
                     ns-info
                     (select-keys loc #{:file :line :column})
                     (when-let [url (:url linter)]
                       {"warning-details-url" url}))})

(defn- run-linter [linter analyze-results ns-sym opts]
  (let [ns-info (namespace-info ns-sym (:cwd opts))]
    (try
      (doall (->> ((:fn linter) analyze-results opts)
                  (map (partial handle-lint-result linter ns-info))))
      (catch Throwable e
        [{:kind :lint-error
          :warn-data (format "Exception thrown by linter %s on namespace %s" (:name linter) ns-sym)
          :exception e}]))))

(defn lint-ns* [ns-sym analyze-results opts linter]
  (let [[results elapsed] (util/timeit (run-linter linter analyze-results ns-sym opts))]
    (->> results
         (group-by :kind)
         (merge {:elapsed elapsed
                 :linter linter}))))

(defn lint-ns [ns-sym linters opts]
  (let [[result elapsed] (util/timeit (analyze/analyze-ns ns-sym :opt opts))
        {:keys [analyze-results exception exception-phase exception-form]} result]
    {:ns ns-sym
     :analysis-time elapsed
     :lint-results (some->> linters
                            (keep linter-name->info)
                            (map (partial lint-ns* ns-sym analyze-results opts)))
     :analyzer-exception (when exception
                           (msgs/report-analyzer-exception exception exception-phase exception-form ns-sym))}))

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


(defn filename-namespace-mismatches [dir-name-strs]
  (let [files-by-dir (into {} (for [dir-name-str dir-name-strs]
                                [dir-name-str (:clojure-files
                                               (#'dir/find-files [dir-name-str]
                                                                 find/clj))]))
        fd-by-dir (util/map-vals (fn [files]
                                   (#'file/files-and-deps files (:read-opts
                                                                 find/clj)))
                                 files-by-dir)]
    (into
     {}
     (for [[dir fd] fd-by-dir,
           [f namespace] (:filemap fd)
           :let [dir-with-sep (str dir File/separator)
                 fname (util/remove-prefix (str f) dir-with-sep)
                 desired-ns (filename-to-ns fname)
                 desired-fname-set (ns-to-filename-set namespace
                                                       (:extensions find/clj))]
           :when (not (contains? desired-fname-set fname))]
       [fname {:dir dir, :namespace namespace,
               :recommended-fnames desired-fname-set,
               :recommended-namespace desired-ns}]))))

(defn nss-in-dirs [dir-name-strs]
  (let [dir-name-strs (set (map util/canonical-filename dir-name-strs))
        mismatches (filename-namespace-mismatches dir-name-strs)]
    (when (seq mismatches)
      (throw (ex-info "namespace-file-name-mismatch"
                      {:err :namespace-filename-mismatch
                       :err-data {:mismatches mismatches}})))
    (let [tracker (if (seq dir-name-strs)
                    (dir/scan-dirs (track/tracker) dir-name-strs)
                    ;; Use empty tracker if dir-name-strs is empty.
                    ;; Calling dir/scan-all will use complete Java
                    ;; classpath if called with an empty sequence.
                    (track/tracker))]
      {:dirs dir-name-strs
       :non-clojure-files (::dir/non-clojure-files tracker)
       :files (set (::dir/files tracker))
       :file-map (::file/filemap tracker)
       :namespaces (::track/load tracker)})))

(defn expand-ns-keywords
  "Expand any keyword in `namespaces` with values from `expanded-namespaces`"
  [expanded-namespaces namespaces]
  (mapcat (fn [x] (get expanded-namespaces x [x])) namespaces))

(defn setup-lint-paths
  "Return a map containing `:source-path` and `:test-paths` which
  contains the set of values in each. If both `source-paths` and `test-paths`
  are empty then `:source-path` is set to all the directories on the classpath,
  while `:test-paths` is the empty set."
  [source-paths test-paths]
  (if-not (or (seq source-paths) (seq test-paths))
    {:source-paths (set (classpath/classpath-directories))
     :test-paths #{}}
    {:source-paths (set source-paths)
     :test-paths (set test-paths)}))

;; If you do not specify :namespaces in the options, it defaults to
;; the same as if you specified [:source-paths :test-paths].  If you
;; specify a list of namespaces explicitly, perhaps mingled with
;; occurrences of :source-paths and/or :test-paths, then the
;; namespaces will be linted in the order you specify, even if this
;; violates dependency order according to the ns form contents.  No
;; warning will be detected or printed about this.

;; TBD: It would be nice if the default behavior would instead be to
;; put the specified namespaces into an order that honors all declared
;; dependencies between namespaces.  If this is implemented, it might
;; also be nice (perhaps only for debugging purposes) to implement a
;; keyword :force-order that preserves the specified namespace order
;; regardless of dependencies.

;; TBD: Abort with an easily understood error message if a namespace
;; is given that cannot be found.

(defn effective-namespaces [exclude-namespaces namespaces
                          {:keys [source-paths test-paths]}]
  ;; If keyword :source-paths occurs in namespaces or
  ;; excluded-namespaces, replace it with all namespaces found in
  ;; the directories in (:source-paths opts), in an order that
  ;; honors dependencies, and similarly for :test-paths.
  ;; nss-in-dirs traverses part of the file system, so only call it
  ;; once for each of :source-paths and :test-paths, and only if
  ;; needed.
  (let [all-ns (concat namespaces exclude-namespaces)
        sp (if (some #{:source-paths} all-ns)
             (nss-in-dirs source-paths))
        tp (if (some #{:test-paths} all-ns)
             (nss-in-dirs test-paths))
        expanded-namespaces {:source-paths (:namespaces sp)
                             :test-paths (:namespaces tp)}
        excluded-namespaces (set (expand-ns-keywords expanded-namespaces
                                                     exclude-namespaces))]
    {:namespaces (->> namespaces
                      (expand-ns-keywords expanded-namespaces)
                      distinct
                      (remove excluded-namespaces))
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
   linter-name->info default-linters]
  (let [linters-orig (linter-seq->set linters)
        excluded-linters (linter-seq->set exclude-linters)
        add-linters (linter-seq->set add-linters)
        linters-requested (-> (set/difference linters-orig excluded-linters)
                              (set/union add-linters))
        known-linters (set (keys linter-name->info))
        unknown-linters (set/difference (set/union linters-requested
                                                   excluded-linters)
                                        known-linters)]
    (when (and (seq unknown-linters)
             (not disable-linter-name-checks))
      (throw (ex-info "unknown-linter"
              {:err :unknown-linter,
               :err-data {:unknown-linters unknown-linters
                          :known-linters known-linters}})))

    (set/intersection linters-requested known-linters)))

(defn- dirs-scanned [reporter cwd dirs]
  (when dirs
    (reporting/note reporter "Directories scanned for source files:")
    (reporting/note reporter " ")
    (->> dirs
         (map #(util/file-warn-info % cwd))
         (map :uri-or-file-name)
         (str/join " ")
         (reporting/note reporter))))


(defn- lint-namespace [reporter namespace linters opts]
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

(defn eastwood-core
  "Lint a sequence of namespaces using a specified collection of linters.

Prerequisites:
+ eastwood.lint namespace is in your classpath
+ TBD: Eastwood resources directory is in your classpath
+ eastwood.lint namespace and its dependencies have been loaded.

Arguments:
+ TBD: to be documented

Side effects:
+ Reads source files, analyzes them, generates Clojure forms from
  analysis results, and eval's those forms (which if there are bugs in
  tools.analyzer or tools.analyzer.jvm, may not be identical to the
  original forms read.  If require'ing your source files launches the
  missiles, so will this.
+ Does create-ns on all namespaces specified, even if an exception
  during linting causes this function to return before reading all of
  them.  See the code for why.
+ Should not print output to any output files/streams/etc., unless
  this occurs due to eval'ing the code being linted.

Return value:
+ TBD
"
  [reporter opts cwd {:keys [namespaces dirs files file-map
                             non-clojure-files] :as info} linters]
  (let [stop-on-exception? (not (:continue-on-exception opts))]
    (dirs-scanned reporter cwd dirs)
    (let [no-ns-forms (misc/no-ns-form-found-files dirs files file-map linters cwd)
          non-clojure-files (misc/non-clojure-files non-clojure-files linters cwd)]
      (reporting/report-result reporter no-ns-forms)
      (reporting/report-result reporter non-clojure-files)
      (when (seq linters)
        (reporting/debug-namespaces reporter namespaces)
        ;; Create all namespaces to be analyzed.  This can help in some
        ;; (unusual) situations, such as when namespace A requires B,
        ;; so Eastwood analyzes B first, but eval'ing B assumes that
        ;; A's namespace has been created first because B contains
        ;; (alias 'x 'A)
        (doseq [n namespaces] (create-ns n))
        (reduce (fn [results namespace]
                  (reporting/note reporter (str "== Linting " namespace " =="))
                  (let [result (lint-namespace reporter namespace linters opts)
                        results (conj results result)]
                    (if (and stop-on-exception?
                             (or (:lint-runtime-exception result)
                                 (:analyzer-exception result)))
                      (do
                        (reporting/stopped-on-exception reporter namespaces results result)
                        (reduced results))
                      (do
                        (reporting/report-result reporter result)
                        results))))
                [no-ns-forms non-clojure-files]
                namespaces)))))

(def default-builtin-config-files
  ["clojure.clj"
   "clojure-contrib.clj"
   "third-party-libs.clj"])

(def default-opts {:cwd (.getCanonicalFile (io/file "."))
                   :linters default-linters
                   :debug #{}
                   :source-paths #{}
                   :test-paths #{}
                   :namespaces #{:source-paths :test-paths}
                   :exclude-namespaces #{}
                   :config-files #{}
                   :builtin-config-files default-builtin-config-files})

(defn last-options-map-adjustments [opts reporter]
  (let [opts (merge default-opts opts)
        opts (-> opts
                 (update :debug set)
                 (update :namespaces set)
                 (update :source-paths set)
                 (update :test-paths set)
                 (update :exclude-namespaces set))
        ;; Changes below override anything in the caller-provided
        ;; options map.
        opts (assoc opts :warning-enable-config
                    (util/init-warning-enable-config
                     (:builtin-config-files opts)
                     (:config-files opts) opts))]
    (reporting/debug reporter
                     :options (with-out-str
                                (println "\nOptions map after filling in defaults:")
                                (pp/pprint (into (sorted-map) opts))))

    ;; throw an error if any keywords appear in the namespace lists
    ;; that are not recognized.
    (unknown-ns-keywords (:namespaces opts) #{:source-paths :test-paths} ":namespaces")
    (unknown-ns-keywords (:exclude-namespaces opts) #{:source-paths :test-paths} ":exclude-namespaces")
    opts))

(defn summary [results]
  (apply merge-with into results))

(defn counted-summary [summary]
  {:warning-count (count (:lint-warnings summary))
   :error-count (+ (count (:lint-errors summary))
                   (count (:lint-runtime-exception summary))
                   (count (:analyzer-exception summary)))
   :lint-time (apply + (mapcat vals(:lint-times summary)))
   :analysis-time (apply + (:analysis-time summary))})

(defn make-report [reporter start-time {:keys [warning-count error-count] :as result}]
  (reporting/note reporter (format "== Linting done in %d ms ==" (- (System/currentTimeMillis)
                                                                    start-time)))
  (reporting/note reporter (format "== Warnings: %d (not including reflection warnings)  Exceptions thrown: %d"
                                   warning-count
                                   error-count))
  {:some-warnings (or (> warning-count 0)
                      (> error-count 0))})

(defn eastwood
  ([opts] (eastwood opts (reporting/printing-reporter opts)))
  ([opts reporter]
   (try
     (reporting/note reporter (version/version-string))
     (let [start-time (System/currentTimeMillis)
           {:keys [exclude-namespaces
                   namespaces
                   source-paths
                   test-paths
                   cwd] :as opts} (last-options-map-adjustments opts reporter)
           namespaces-info (effective-namespaces exclude-namespaces namespaces
                                                 (setup-lint-paths source-paths test-paths))
           linter-info (select-keys opts [:linters :exclude-linters :add-linters :disable-linter-name-checks])]
       (reporting/debug reporter :var-info (with-out-str
                                             (util/print-var-info-summary @typos/var-info-map-delayed opts)))
       (reporting/debug reporter :compare-forms
                        "Writing files forms-read.txt and forms-emitted.txt")
       (->> (effective-linters linter-info linter-name->info default-linters)
            (eastwood-core reporter opts cwd namespaces-info)
            summary
            counted-summary
            (make-report reporter start-time)))
     (catch Exception e
       (reporting/show-error reporter (or (ex-data e) e))
       {:some-warnings true}))))

(defn eastwood-from-cmdline [opts]
  (let [ret (eastwood opts)]
    (if (:some-warnings ret)
      ;; Exit with non-0 exit status for the benefit of any shell
      ;; scripts invoking Eastwood that want to know if there were no
      ;; errors, warnings, or exceptions.
      (System/exit 1)
      ;; Eastwood does not use future, pmap, or clojure.shell/sh now
      ;; (at least not yet), but it may evaluate code that does when
      ;; linting a project.  Call shutdown-agents to avoid the
      ;; 1-minute 'hang' that would otherwise occur.
      (shutdown-agents))))

(defn lint
  "Invoke Eastwood from REPL or other Clojure code, and return a map
containing these keys:

  :warnings - a sequence of maps representing individual warnings.
      The warning map contents are documented below.

  :err - nil if there were no exceptions thrown or other errors that
      stopped linting before it completed.  A keyword identifying a
      kind of error if there was.  See the source file
      src/eastwood/lint.clj inside Eastwood for defmethod's of
      error-msg.  Each is specialized on a keyword value that is one
      possible value the :err key can take.  The body of each method
      shows how Eastwood shows to the user each kind of error when it
      is invoked from the command line via Leiningen, serves as a kind
      of documentation for what the value of the :err-data key
      contains.

  :err-data - Some data describing the error if :err's value is not
      nil.  See :err above for where to find out more about its
      contents.

  :versions - A nested map with its own keys containing information
      about JVM, Clojure, and Eastwood versions.

Keys in a warning map:

  :uri-or-file-name - string containing file name where warning
      occurs, relative to :cwd directory of options map, if it is a
      file inside of that directory, or a URI object,
      e.g. \"cases/testcases/f02.clj\"

  :line - line number in file for warning, e.g. 20.  The first line in
      the file is 1, not 0.  Note: In some cases this key may not be
      present, or the value may be nil.  This is an area where
      Eastwood will probably improve in the future, but best to handle
      it for now, perhaps by replacing it with line 1 as a
      placeholder.

  :column - column number in file for warning, e.g. 42.  The first
      character of each line is column 1, not 0.  Same comments apply
      for :column as for :line.

  :linter - keyword identifying the linter, e.g. :def-in-def

  :msg - string describing the warning message, e.g. \"There is a def
      of i-am-inner-defonce-sym nested inside def
      i-am-outer-defonce-sym\"

  :uri - object with class URI of the file, *or* a URI within a JAR
       file, e.g.  #<URI file:/Users/jafinger/clj/eastwood/0.2.0/eastwood/cases/testcases/f02.clj>

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
                   cwd] :as opts} (last-options-map-adjustments opts reporter)
           namespaces-info (effective-namespaces exclude-namespaces namespaces
                                               (setup-lint-paths source-paths test-paths))
           linter-info (select-keys opts [:linters :exclude-linters :add-linters :disable-linter-name-checks])
           {:keys [error error-data
                   lint-warnings
                   namespace] :as ret}
           (->> (effective-linters linter-info linter-name->info default-linters)
                (eastwood-core reporter opts cwd namespaces-info)
                summary)]
       {:namespaces namespace
        :warnings (seq lint-warnings)
        :err error
        :err-data error-data
        :versions (version/versions)})
     (catch Exception e
       {:err (ex-data e)
        :versions (version/versions)}))))

(defn insp
  "Read, analyze, and eval a file specified by namespace as a symbol,
e.g. 'testcases.f01.  Return a value that has been 'cleaned up', by
removing some keys from ASTs, so that it is more convenient to call
clojure.inspector/inspect-tree on it.  Example in REPL:

(require '[eastwood.lint :as l] '[clojure.inspector :as i])
(i/inspect-tree (l/insp 'testcases.f01))"
  [nssym]
  (let [a (analyze/analyze-ns nssym :opt {:callback (fn [_]) :debug #{}})]
    (update-in a [:analyze-results :asts]
               (fn [ast] (mapv util/clean-ast ast)))))

(defn -main
  ([] (-main (pr-str default-opts)))
  ([opts]
   (eastwood-from-cmdline  (edn/read-string opts))))
