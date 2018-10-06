(ns eastwood.lint
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.set :as set]
            [clojure.string :as str]
            [eastwood.analyze-ns :as analyze]
            [eastwood.copieddeps.dep11.clojure.java.classpath :as classpath]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.dir :as dir]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.file :as file]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.find :as find]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.track :as track]
            [eastwood.error-messages :as msgs]
            [eastwood.linters.deprecated :as deprecated]
            [eastwood.linters.misc :as misc]
            [eastwood.linters.typetags :as typetags]
            [eastwood.linters.typos :as typos]
            [eastwood.linters.unused :as unused]
            [eastwood.linters.implicit-dependencies :as implicit-dependencies]
            [eastwood.reporting-callbacks :as reporting]
            [eastwood.util :as util]
            [eastwood.version :as version])
  (:import java.io.File))

(def ^:dynamic *eastwood-version*
  {:major version/major, :minor version/minor, :incremental version/patch, :qualifier version/pre-release})

(defn eastwood-version [] version/string)

(defmulti error-msg
  "Given a map describing an Eastwood error result, which should
always have at least the keys :err and :err-data, return a string
describing the error."
  :err)

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

(defn make-lint-warning [kw msg opts file]
  {:kind :lint-warning,
   :warn-data (let [inf (util/file-warn-info file (:cwd opts))]
                (merge
                 {:linter kw
                  :msg (format (str msg " '%s'.  It will not be linted.")
                               (:uri-or-file-name inf))}
                 inf))})

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
          :warn-data (format "Exception thrown by linter %s on namespace %s" (:name linter) ns-sym)}]))))

(defn report-warnings [cb warning-count warnings]
  (swap! warning-count + (count warnings))
  (doseq [warning warnings]
    (cb warnings)))

(defn- report-analyzer-exception [exception exception-phase exception-form ns-sym]
  (let [[strings error-cb] (msgs/string-builder)]
    (error-cb (str "Exception thrown during phase " exception-phase
                   " of linting namespace " ns-sym))
    (let [{:keys [msgs info]} (msgs/format-exception ns-sym exception)]
      (swap! strings into msgs)
      (when (= info :show-more-details)
        (error-cb "\nThe following form was being processed during the exception:")
        ;; TBD: Replace this binding with util/pprint-form variation
        ;; that does not print metadata?
        (error-cb (with-out-str (binding [*print-level* 7
                                          *print-length* 50]
                                  (pp/pprint exception-form))))
        (error-cb "\nShown again with metadata for debugging (some metadata elided for brevity):")
        (error-cb (with-out-str (util/pprint-form exception-form)))))
    (error-cb
     (str "\nAn exception was thrown while analyzing namespace " ns-sym "
Lint results may be incomplete.  If there are compilation errors in
your code, try fixing those.  If not, check above for info on the
exception."))
    {:exception exception
     :msgs @strings}))

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
     :exception (when exception
                  (report-analyzer-exception exception exception-phase exception-form ns-sym))}))

(declare last-options-map-adjustments)


(defn unknown-ns-keywords [namespaces known-ns-keywords desc]
  (let [keyword-set (set (filter keyword? namespaces))
        unknown-ns-keywords (set/difference keyword-set known-ns-keywords)]
    (if (empty? unknown-ns-keywords)
      nil
      {:err :unknown-ns-keywords,
       :err-data {:for-option desc
                  :unknown-ns-keywords unknown-ns-keywords
                  :allowed-ns-keywords known-ns-keywords}})))

(defmethod error-msg :unknown-ns-keywords [err-info]
  (let [{:keys [for-option unknown-ns-keywords allowed-ns-keywords]}
        (:err-data err-info)]
    (with-out-str
      (println (format "The following keywords appeared in the namespaces specified after %s :"
                       for-option))
      (println (format "    %s" (seq unknown-ns-keywords)))
      (println (format "The only keywords allowed in this list of namespaces are: %s"
                       (seq allowed-ns-keywords))))))


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

(defn canonical-filename
  "Returns the canonical file name for the given file name.  A
canonical file name is platform dependent, but is both absolute and
unique.  See the Java docs for getCanonicalPath for some more details,
and the examples below.

    http://docs.oracle.com/javase/7/docs/api/java/io/File.html#getCanonicalPath%28%29

Examples:

Context: A Linux or Mac OS X system, where the current working
directory is /Users/jafinger/clj/dolly

user=> (canonical-filename \"README.md\")
\"/Users/jafinger/clj/dolly/README.md\"

user=> (canonical-filename \"../../Documents/\")
\"/Users/jafinger/Documents\"

user=> (canonical-filename \"../.././clj/../Documents/././\")
\"/Users/jafinger/Documents\"

Context: A Windows 7 system, where the current working directory is
C:\\Users\\jafinger\\clj\\dolly

user=> (canonical-filename \"README.md\")
\"C:\\Users\\jafinger\\clj\\dolly\\README.md\"

user=> (canonical-filename \"..\\..\\Documents\\\")
\"C:\\Users\\jafinger\\Documents\"

user=> (canonical-filename \"..\\..\\.\\clj\\..\\Documents\\.\\.\\\")
\"C:\\Users\\jafinger\\Documents\""
  [fname]
  (let [^java.io.File f (if (instance? java.io.File fname)
                          fname
                          (java.io.File. ^String fname))]
    (.getCanonicalPath f)))

(defn nss-in-dirs [dir-name-strs opt warning-count]
  (let [dir-name-strs (map canonical-filename dir-name-strs)
        mismatches (filename-namespace-mismatches dir-name-strs)]
    (if (seq mismatches)
      {:err :namespace-filename-mismatch
       :err-data {:mismatches mismatches}}
      (let [tracker (if (seq dir-name-strs)
                      (dir/scan-dirs (track/tracker) dir-name-strs)
                      ;; Use empty tracker if dir-name-strs is empty.
                      ;; Calling dir/scan-all will use complete Java
                      ;; classpath if called with an empty sequence.
                      (track/tracker))
            files-no-ns-form-found
            (when (some #{:no-ns-form-found} (:enabled-linters opt))
              (let [tfiles (-> tracker
                               :eastwood.copieddeps.dep9.clojure.tools.namespace.dir/files
                               set)
                    tfilemap (-> tracker
                                 :eastwood.copieddeps.dep9.clojure.tools.namespace.file/filemap
                                 keys
                                 set)
                    maybe-data-readers (->> dir-name-strs
                                            (map #(File.
                                                   (str % File/separator
                                                        "data_readers.clj")))
                                            set)]
                (set/difference tfiles tfilemap maybe-data-readers)))]
        {:err nil
         :dirs (map #(util/file-warn-info % (:cwd opt)) dir-name-strs)
         :non-clojure-files
         (:eastwood.copieddeps.dep9.clojure.tools.namespace.dir/non-clojure-files
          tracker)
         :no-ns-form-found-files files-no-ns-form-found
         :namespaces
         (:eastwood.copieddeps.dep9.clojure.tools.namespace.track/load
          tracker)}))))


(defmethod error-msg :namespace-filename-mismatch [err-info]
  (let [{:keys [mismatches]} (:err-data err-info)]
    (with-out-str
      (println "The following file(s) contain ns forms with namespaces that do not correspond
with their file names:")
      (doseq [[fname {:keys [dir namespace recommended-fnames recommended-namespace]}]
              mismatches]
        (println (format "Directory: %s" dir))
        (println (format "    File                   : %s" fname))
        (println (format "    has namespace          : %s" namespace))
        (if (= namespace recommended-namespace)
          ;; Give somewhat clearer message in this case
          (println (format "    should be in file(s)   : %s"
                           (str/join "\n                             "
                                     recommended-fnames)))
          (do
            (println (format "    should have namespace  : %s"
                             recommended-namespace))
            (println (format "    or should be in file(s): %s"
                             (str/join "\n                             "
                                       recommended-fnames))))))
      (println "
No other linting checks will be performed until these problems have
been corrected.

The 'should have namespace' and 'should be in file' messages above are
merely suggestions.  It may be better in your case to rename both the
file and namespace to avoid name collisions."))))


(defn replace-ns-keywords [namespaces source-paths test-paths]
  (mapcat (fn [x]
            (if (keyword? x)
              (case x
                :source-paths source-paths
                :test-paths test-paths
                ;;:force-order []
                )
              [x]))
          namespaces))

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

(defn opts->namespaces [opts warning-count]
  (let [namespaces1 (distinct (:namespaces opts))
        sp-included? (some #{:source-paths} namespaces1)
        tp-included? (some #{:test-paths} namespaces1)
        excluded-namespaces (set (:exclude-namespaces opts))]
    ;; Return an error if any keywords appear in the namespace lists
    ;; that are not recognized.
    (or
     (unknown-ns-keywords namespaces1 #{:source-paths :test-paths}
                          ":namespaces")
     (unknown-ns-keywords excluded-namespaces #{:source-paths :test-paths}
                          ":exclude-namespaces")
     ;; If keyword :source-paths occurs in namespaces1 or
     ;; excluded-namespaces, replace it with all namespaces found in
     ;; the directories in (:source-paths opts), in an order that
     ;; honors dependencies, and similarly for :test-paths.
     ;; nss-in-dirs traverses part of the file system, so only call it
     ;; once for each of :source-paths and :test-paths, and only if
     ;; needed.
     (let [all-ns (concat namespaces1 excluded-namespaces)
           sp (if (some #{:source-paths} all-ns)
                (nss-in-dirs (:source-paths opts) opts warning-count))
           tp (if (some #{:test-paths} all-ns)
                (nss-in-dirs (:test-paths opts) opts warning-count))]
       (cond
        (:err sp) sp
        (:err tp) tp
        :else
        (let [source-paths (:namespaces sp)
              test-paths (:namespaces tp)
              namespaces (replace-ns-keywords namespaces1
                                              source-paths test-paths)
              namespaces (distinct namespaces)
              excluded-namespaces (set (replace-ns-keywords excluded-namespaces
                                                            source-paths
                                                            test-paths))
              namespaces (remove excluded-namespaces namespaces)]
          {:err nil,
           :namespaces namespaces,
           :dirs (distinct (concat (if sp-included? (:dirs sp))
                                   (if tp-included? (:dirs tp))))
           :no-ns-form-found-files
           (concat
            (if sp-included? (:no-ns-form-found-files sp))
            (if tp-included? (:no-ns-form-found-files tp)))
           :non-clojure-files (concat
                               (if sp-included? (:non-clojure-files sp))
                               (if tp-included? (:non-clojure-files tp)))}))))))


(defn replace-linter-keywords [linters all-linters default-linters]
  (mapcat (fn [x]
            (cond (= :all x) all-linters
                  (= :default x) default-linters
                  :else [x]))
          linters))


(defn linter-seq->set [linter-seq]
  (set (replace-linter-keywords linter-seq all-linters default-linters)))


(defn opts->linters [opts linter-name->info default-linters]
  (let [linters-orig (linter-seq->set (:linters opts))
        excluded-linters (linter-seq->set (:exclude-linters opts))
        add-linters (linter-seq->set (:add-linters opts))
        linters-requested (-> (set/difference linters-orig excluded-linters)
                              (set/union add-linters))
        known-linters (set (keys linter-name->info))
        unknown-linters (set/difference (set/union linters-requested
                                                   excluded-linters)
                                        known-linters)
        linters (set/intersection linters-requested known-linters)]
    (when (util/debug? :options opts)
      (let [debug-cb (util/make-msg-cb :debug opts)]
        (debug-cb "Calculation of final list of linters:")
        (debug-cb (format "    :linters"))
        (debug-cb (format "      before keyword substitution: %s" (vec (:linters opts))))
        (debug-cb (format "      after  keyword substitution: %s" (vec (sort linters-orig))))
        (debug-cb (format "    :exclude-linters"))
        (debug-cb (format "      before keyword substitution: %s" (vec (:exclude-linters opts))))
        (debug-cb (format "      after  keyword substitution: %s" (vec (sort excluded-linters))))
        (debug-cb (format "    :add-linters"))
        (debug-cb (format "      before keyword substitution: %s" (vec (:add-linters opts))))
        (debug-cb (format "      after  keyword substitution: %s" (vec (sort add-linters))))
        (debug-cb (format "    final effective linter set: linters - exclude + add:"))
        (debug-cb (format "      %s" (vec (sort linters))))))
    (if (and (seq unknown-linters)
             (not (:disable-linter-name-checks opts)))
      {:err :unknown-linter,
       :err-data {:unknown-linters unknown-linters
                  :known-linters known-linters}}
      ;; else
      {:err nil, :linters linters})))


(defmethod error-msg :unknown-linter [err-info]
  (let [{:keys [unknown-linters known-linters]} (:err-data err-info)]
    (with-out-str
      (println (format "The following requested or excluded linters are unknown: %s"
                       (seq unknown-linters)))
      (println (format "Known linters are: %s"
                       (seq (sort known-linters)))))))


(defmethod error-msg :exception-thrown [err-info]
  (let [{:keys [unanalyzed-namespaces last-namespace]} (:err-data err-info)]
    ;; Don't report that we stopped analyzing early if we stop on the
    ;; last namespace (it is especially bad form to print the long
    ;; message if only one namespace was being linted).
    (if (seq unanalyzed-namespaces)
      (format "
Stopped analyzing namespaces after %s
due to exception thrown.  %d namespaces left unanalyzed.

If you wish to force continuation of linting after an exception in one
namespace, make the option map key :continue-on-exception have the
value true.

WARNING: This can cause exceptions to be thrown while analyzing later
namespaces that would not otherwise occur.  For example, if a function
is defined in the namespace where the first exception occurs, after
the exception, it will never be evaluated.  If the function is then
used in namespaces analyzed later, it will be undefined, causing
error.
"
            last-namespace
            (count unanalyzed-namespaces))

      "
Exception thrown while analyzing last namespace.
"
      )))

(defn- dirs-scanned [dirs]
  (when dirs
    (println "Directories scanned for source files:")
    (print " ")
    (->> dirs
         (map :uri-or-file-name)
         (str/join " ")
         (println))
    (flush)))

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
  [opts]
  (let [warning-count (atom 0)
        exception-count (atom 0)
        cb (:callback opts)
        {:keys [linters] :as m1} (opts->linters opts linter-name->info
                                                default-linters)
        opts (assoc opts :enabled-linters linters)
        {:keys [namespaces dirs no-ns-form-found-files
                non-clojure-files] :as m2}
        (opts->namespaces opts warning-count)]
    (dirs-scanned dirs)
    (when (some #{:no-ns-form-found} (:enabled-linters opts))
      (->> no-ns-form-found-files
           (map (partial make-lint-warning :no-ns-form-found "No ns form was found in file" opts))
           (map (fn [m] (assoc m :opt opts)))
           (report-warnings cb warning-count)))
    (when (some #{:non-clojure-file} (:enabled-linters opts))
      (->> non-clojure-files
           (map (partial make-lint-warning :non-clojure-file "Non-Clojure file" opts))
           (map (fn [m] (assoc m :opt opts)))
           (report-warnings cb warning-count)))
    (cond
     (:err m1) m1
     (:err m2) m2
     :else
     (let [error-cb (util/make-msg-cb :error opts)
           debug-cb (util/make-msg-cb :debug opts)
           note-cb (util/make-msg-cb :note opts)
           continue-on-exception? (:continue-on-exception opts)
           stopped-on-exc (atom false)
           print-time? (util/debug? :time opts)]
       (when (util/debug? :ns opts)
         (debug-cb (format "Namespaces to be linted:"))
         (doseq [n namespaces]
           (debug-cb (format "    %s" n))))
       ;; Create all namespaces to be analyzed.  This can help in some
       ;; (unusual) situations, such as when namespace A requires B,
       ;; so Eastwood analyzes B first, but eval'ing B assumes that
       ;; A's namespace has been created first because B contains
       ;; (alias 'x 'A)
       (doseq [n namespaces]
         (create-ns n))
       (when (seq (:enabled-linters opts))
         (loop [namespaces namespaces]
           (when-first [namespace namespaces]
             (let [e (try
                       (note-cb (str "== Linting " namespace " =="))
                       (let [{:keys [exception lint-results analysis-time]} (lint-ns namespace (:enabled-linters opts) opts)]
                         (when print-time?
                           (note-cb (format "Analysis took %.1f millisec" analysis-time)))
                         (when exception
                           (swap! exception-count inc))
                         (doseq [{:keys [lint-warning lint-error elapsed linter]} lint-results]
                           (when print-time?
                             (note-cb (format "Linter %s took %.1f millisec"
                                              (:name linter) elapsed)))
                           (swap! warning-count + (count lint-warning))
                           (swap! exception-count + (count lint-error))
                           (doseq [error lint-error]
                             (error-cb (:warn-data error)))
                           (doseq [warning lint-warning]
                             (cb (assoc warning :opt opts))))
                         (when exception
                           (error-cb (str/join "\n" (:msgs exception)))))
                       (catch RuntimeException e
                           (error-cb "Linting failed:")
                           (util/pst e nil error-cb)
                           e))]
               (if (or continue-on-exception?
                       (not (instance? Throwable e)))
                 (recur (next namespaces))
                 (reset! stopped-on-exc
                         {:exception e
                          :last-namespace namespace
                          :unanalyzed-namespaces (next namespaces)}))))))
       (merge
        {:err nil
         :warning-count @warning-count
         :exception-count @exception-count}
        (if @stopped-on-exc
          {:err :exception-thrown
           :err-data @stopped-on-exc}))))))

;; Test Eastwood for a while with messages being written to file
;; "east-out.txt", to see if I catch everything that was going to
;; *out* with callback functions or return values.

;; Use the java.io.PrintWriter shown below to write messages to the
;; same place as Eastwood does in version 0.1.4.


(def default-builtin-config-files
  ["clojure.clj"
   "clojure-contrib.clj"
   "third-party-libs.clj"])


(defn last-options-map-adjustments [opts]
  (let [opts (update-in opts [:debug] set)
        opts (merge {:cwd (.getCanonicalFile (io/file "."))
                     :linters default-linters
                     :namespaces [:source-paths :test-paths]
                     :builtin-config-files default-builtin-config-files}
                    opts)
        ;; special case 'merge': If _neither_ of :source-paths or
        ;; :test-paths were specified in the options map, then set
        ;; _one_ of them to a list of all the directories on the
        ;; classpath.  If either is present, leave them both as is.
        ;; Both of these should always have a value if invoked from
        ;; Leiningen command line, so this is only for when invoked
        ;; directly, e.g. from the REPL, intended as a convenience.
        opts (if (or (contains? opts :source-paths)
                     (contains? opts :test-paths))
               opts
               (assoc opts :source-paths
                      (classpath/classpath-directories)))
        ;; The following value is equivalent to (merge {:callback ...}
        ;; opts), but it does not calculate the value unless needed.
        opts (if (contains? opts :callback)
               opts
               (assoc opts :callback (reporting/make-default-cb opts)))

        ;; Changes below override anything in the caller-provided
        ;; options map.
        opts (assoc opts :warning-enable-config
                    (util/init-warning-enable-config opts))]
    opts))


(defn eastwood [opts]
  ;; Use caller-provided :cwd and :callback values if provided
  (let [opts (last-options-map-adjustments opts)
        _ (when (util/debug? :options opts)
            (println "\nOptions map after filling in defaults:")
            (pp/pprint (into (sorted-map) opts)))
        _ (when (util/debug? :var-info opts)
            (util/print-var-info-summary @typos/var-info-map-delayed opts))
        error-cb (util/make-msg-cb :error opts)
        note-cb (util/make-msg-cb :note opts)
        debug-cb (util/make-msg-cb :debug opts)
        _ (do
            (note-cb (format "== Eastwood %s Clojure %s JVM %s"
                             (eastwood-version)
                             (clojure-version)
                             (get (System/getProperties) "java.version")))
            (when (util/debug? :compare-forms opts)
              (debug-cb "Writing files forms-read.txt and forms-emitted.txt")))
        {:keys [err warning-count exception-count] :as ret}
        (eastwood-core opts)]
    (when err
      (error-cb (error-msg ret)))
    (when (number? warning-count)
      (note-cb (format "== Warnings: %d (not including reflection warnings)  Exceptions thrown: %d"
                       warning-count exception-count)))
    (if (or err (and (number? warning-count)
                     (or (> warning-count 0) (> exception-count 0))))
      {:some-warnings true}
      {:some-warnings false})))


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
  [opts]
  (let [lint-warnings (atom [])
        cb (fn cb [info]
             (case (:kind info)
               :lint-warning (swap! lint-warnings conj (:warn-data info))
               :default))  ; do nothing with other kinds of callbacks
        opts (if (contains? opts :callback)
               opts
               (assoc opts :callback cb))

        opts (last-options-map-adjustments opts)
        _ (when (util/debug? :options opts)
            (println "\nOptions map after filling in defaults:")
            (pp/pprint (into (sorted-map) opts)))

        {:keys [err err-data] :as ret} (eastwood-core opts)]
    {:warnings @lint-warnings
     :err err
     :err-data err-data
     :versions
     {:eastwood-version-map *eastwood-version*
      :eastwood-version-string (eastwood-version)
      :clojure-version-map *clojure-version*
      :clojure-version-string (clojure-version)
      :jvm-version-string (get (System/getProperties) "java.version")}}))


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
