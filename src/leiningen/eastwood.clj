(ns leiningen.eastwood)

;; Stolen from lein-swank:
;; https://github.com/technomancy/swank-clojure/blob/master/lein-swank/src/leiningen/swank.clj#L45
(defn eval-in-project
  "Support eval-in-project in both Leiningen 1.x and 2.x."
  [project form init]
  (let [[eip two?] (or (try (require 'leiningen.core.eval)
                            [(resolve 'leiningen.core.eval/eval-in-project)
                             true]
                            (catch java.io.FileNotFoundException _))
                       (try (require 'leiningen.compile)
                            [(resolve 'leiningen.compile/eval-in-project)]
                            (catch java.io.FileNotFoundException _)))]
    (if two?
      (eip project form init)
      (eip project form nil nil init))))

(defn prepare-project [project]
  (let [project (update-in project
                           [:dependencies]
                           conj
                           '[jonase/eastwood "0.0.2"])

        project (if (contains? project :source-path)
                  (assoc project :source-paths [(:source-path project)])
                  project)]
    project))

(defn eastwood
  ([project] (eastwood project "{}"))
  ([project opts]
     (let [project (prepare-project project)
           opts (read-string opts)
           opts (if (contains? opts :source-paths)
                  opts
                  (assoc opts :source-paths (:source-paths project)))
           global-opts (:eastwood project)
           opts (merge global-opts opts)]
       (eval-in-project project
                        `(eastwood.core/run-eastwood '~opts)
                        '(require 'eastwood.core)))))

