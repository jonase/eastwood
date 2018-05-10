(ns leiningen.eastwood
  (:require [clojure.pprint :as pp]
            [eastwood.copieddeps.dep6.leinjacker.eval :as leval]
            [eastwood.copieddeps.dep6.leinjacker.deps :refer [add-if-missing]]))

(def eastwood-version-string "0.2.7-SNAPSHOT")

;; 'lein help' prints only the first line of the string returned by
;; help.  'lein help eastwood' prints all of it, plus the arg vectors
;; taken by the eastwood function below.

(defn help []
  (with-out-str
    (println "Lint your Clojure code.")
    (println (format "== Eastwood %s Clojure %s JVM %s"
                     eastwood-version-string
                     (clojure-version)
                     (get (System/getProperties) "java.version")))
    (println "
Usage: To lint all Clojure files in your :source-paths and :test-paths:

    lein eastwood

WARNING: If loading your code, e.g. via 'require' or 'use' (especially
test files) causes side effects like opening connections to servers,
modifying databases, etc., linting your test files will have those
side effects, too.  To confine linting to Clojure code in
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
wherever it appears.  (binding [*print-meta* true] (pprint obj))
prints metadata on symbols, but not on collection, at least with
Clojure 1.6.0 and probably earlier versions.  Clojure ticket CLJ-1445
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


(defn eastwood
  ([project] (eastwood project "{}"))
  ([project opts]
     (cond
      (= opts "help") (println (help))

      (= opts "lein-project")
      (do
        (pprint-meta (into (sorted-map) project))
        (println "\nValue of :eastwood key in project map:")
        (pprint-meta (into (sorted-map) (:eastwood project))))

      :else
      (let [leiningen-paths (select-keys project [:source-paths
                                                  :test-paths])
            leiningen-opts (:eastwood project)
            cmdline-opts (read-string opts)
            opts (merge leiningen-paths leiningen-opts cmdline-opts)]
        (when (some #{:options} (:debug opts))
          (println "\nLeiningen paths:")
          (pprint-meta (into (sorted-map) leiningen-paths))
          (println "\nLeiningen options map:")
          (pprint-meta (into (sorted-map) leiningen-opts))
          (println "\nCommand line options map:")
          (pprint-meta (into (sorted-map) cmdline-opts))
          (println "\nMerged options map:")
          (pprint-meta (into (sorted-map) opts))
          (println "\nLeininge project map:")
          (pprint-meta (into (sorted-map) project))
          (println)
          (flush))
        (leval/eval-in-project
         (add-if-missing project ['jonase/eastwood eastwood-version-string])
         `(eastwood.versioncheck/run-eastwood '~opts)
         '(require 'eastwood.versioncheck))))))
