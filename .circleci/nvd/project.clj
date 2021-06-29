(defproject nvd-checker "n/a"
  :description "Meant for analyzing other projects' classpaths in an isolated manner,
so that lein-nvd's dependencies don't affect the analyzed corpus (leading to false positives/negatives) OR
the target project's dependencies affect lein-nvd's implementation (leading to bugs)."
  :dependencies [ ;; if updating nvd-clojure, please make sure that the `~/.m2/repository/org/owasp/dependency-check-utils/6.2.2/data` key remains valid
                 ;; in .circleci/config.yml (as that transitive dependency might change):
                 [nvd-clojure "1.5.0"]
                 [org.clojure/clojure "1.10.3"]]
  :jvm-opts ["-Dclojure.main.report=stderr"]
  :pedantic? :abort)
