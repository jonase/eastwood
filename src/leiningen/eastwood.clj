(ns leiningen.eastwood
  (:require [clojure.pprint :as pp]
            [eastwood.copieddeps.dep6.leinjacker.eval :as leval]
            [eastwood.copieddeps.dep6.leinjacker.deps :refer [add-if-missing]]))

(def eastwood-version-string "0.2.3-SNAPSHOT")

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


(defn eastwood
  ([project] (eastwood project "{}"))
  ([project opts]
     (cond
      (= opts "help") (println (help))

      (= opts "lein-project")
      (do
        (pp/pprint (into (sorted-map) project))
        (println "\nValue of :eastwood key in project map:")
        (pp/pprint (into (sorted-map) (:eastwood project))))

      :else
      (let [leiningen-paths (select-keys project [:source-paths
                                                  :test-paths])
            leiningen-opts (:eastwood project)
            cmdline-opts (read-string opts)
            opts (merge leiningen-paths leiningen-opts cmdline-opts)]
        (when (some #{:options} (:debug opts))
          (println "\nLeiningen paths:")
          (pp/pprint (into (sorted-map) leiningen-paths))
          (println "\nLeiningen options map:")
          (pp/pprint (into (sorted-map) leiningen-opts))
          (println "\nCommand line options map:")
          (pp/pprint (into (sorted-map) cmdline-opts))
          (println "\nMerged options map:")
          (pp/pprint (into (sorted-map) opts))
          (println))
        (leval/eval-in-project
         (add-if-missing project ['jonase/eastwood eastwood-version-string])
         `(eastwood.versioncheck/run-eastwood '~opts)
         '(require 'eastwood.versioncheck))))))
