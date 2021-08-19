(ns leiningen.eastwood
  (:require
   [clojure.pprint :as pp]
   [eastwood.version :as version]
   [leiningen.core.eval :as eval]
   [leiningen.core.main :as main]))

;; 'lein help' prints only the first line of the string returned by
;; help. 'lein help eastwood' prints all of it, plus the arg vectors
;; taken by the eastwood function below.

(defn help []
  (with-out-str
    (println "Lint your Clojure code.")
    (println (version/version-string))
    (println "
Usage: To lint all Clojure files in your :source-paths and :test-paths:

    lein eastwood

WARNING: If loading your code, e.g. via 'require' or 'use' (especially
test files) causes side effects like opening connections to servers,
modifying databases, etc., linting your test files will have those
side effects, too. To confine linting to Clojure code in
your :source-paths, use this command:

    lein eastwood '{:namespaces [:source-paths]}'

For other options, see the full documentation on-line here:

    https://github.com/jonase/eastwood")))

;; Everything from here down to, and including, pprint-meta is a copy
;; of some functions from namespace eastwood.util, specifically to
;; allow more visibility of metadata in the Leiningen project map when
;; running with a command line like "lein eastwood lein-project"

(defn has-keys? [m key-seq]
  (every? #(contains? m %) key-seq))

(defn sorted-map-with-non-keyword-keys? [x]
  (and (map? x)
       (sorted? x)
       (some (fn [o] (not (keyword? o))) (keys x))))

(defn protocol?
  "Make a good guess as to whether p is an object created via defprotocol."
  [p]
  (and (map? p)
       ;; Don't even try to do has-keys? on a sorted map with
       ;; non-keyword keys, since it will most likely cause compare to
       ;; throw an exception.
       (not (sorted-map-with-non-keyword-keys? p))
       (has-keys? p [:on :on-interface :sigs :var :method-map
                     :method-builders])))

(defn pprint-meta
  "A version of pprint that prints all metadata on the object,
wherever it appears. (binding [*print-meta* true] (pprint obj))
prints metadata on symbols, but not on collection, at least with
Clojure 1.6.0 and probably earlier versions. Clojure ticket CLJ-1445
may improve upon this in the future.

http://dev.clojure.org/jira/browse/CLJ-1445"
  [obj]
  (binding [*print-meta* false]
    (let [orig-dispatch pp/*print-pprint-dispatch*]
      (pp/with-pprint-dispatch
        (fn pm [o]
          (let [o (if (protocol? o)
                    (assoc o
                           :var :true-value-replaced-to-avoid-pprint-infinite-loop
                           :method-builders :true-value-replaced-to-avoid-pprint-infinite-loop)
                    o)]
            (when (meta o)
              (print "^")
              (pm (meta o))
              (.write ^java.io.Writer *out* " ")
              (pp/pprint-newline :fill))
            (orig-dispatch o)))
        (pp/pprint obj)))))

(defn- missing-eastwood-dependency? [project]
  (not (some->> project
                :dependencies
                (some (comp (partial = 'jonase/eastwood) first)))))

(defn- eastwood-plugin [project]
  (->> project
       :plugins
       (filter (comp (partial = 'jonase/eastwood) first))
       first))

;; The rationale for this function is as follows:
;; In order for this code to be run, eastwood has to be present in some
;; :plugin - vector, either in a `project.clj` or in `~/.lein/profiles.clj`.
;; The Eastwood readme does not mandate that you also put eastwood in the
;; `:dependencies` vector if you put it in a `~/.lein/profiles.clj` `:plugin`
;; vector. If this is the case, we need to manually add it here:
;; So being here, we know at least that we have eastwood defined as a plugin.

(defn- maybe-add-eastwood [project]
  (cond-> project
    (and (eastwood-plugin project) ;; if we're dog-fooding, no plugin is present
                                   ;; in project.clj, and we don't need to fix the vector
         (missing-eastwood-dependency? project))
    (update :dependencies (comp vec conj) (eastwood-plugin project))))

(defn eastwood
  ([project] (eastwood project "{}"))
  ([project opts]
   (cond
     (= opts "help") (main/info (help))
     (= opts "lein-project")
     (do
       (main/info (with-out-str (pprint-meta (into (sorted-map) project))))
       (main/info "\nValue of :eastwood key in project map:")
       (main/info (with-out-str (pprint-meta (into (sorted-map) (:eastwood project))))))

     :else
     (let [leiningen-paths (select-keys project [:source-paths
                                                 :test-paths])
           leiningen-opts (:eastwood project)
           cmdline-opts (read-string opts)
           opts (merge leiningen-paths leiningen-opts cmdline-opts)]
       (main/debug "\nLeiningen paths:")
       (main/debug (with-out-str (pprint-meta (into (sorted-map) leiningen-paths))))
       (main/debug "\nLeiningen options map:")
       (main/debug (with-out-str (pprint-meta (into (sorted-map) leiningen-opts))))
       (main/debug "\nCommand line options map:")
       (main/debug (with-out-str (pprint-meta (into (sorted-map) cmdline-opts))))
       (main/debug "\nMerged options map:")
       (main/debug (with-out-str (pprint-meta (into (sorted-map) opts))))
       (main/debug "\nLeiningen project map:")
       (main/debug (with-out-str (pprint-meta (into (sorted-map) project))))
       (main/debug)
       (eval/eval-in-project
        (maybe-add-eastwood project)
        `(eastwood.versioncheck/run-eastwood '~opts)
        '(require 'eastwood.versioncheck))))))
