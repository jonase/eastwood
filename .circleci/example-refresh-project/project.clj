(defproject example-project "n/a"
  :source-paths ["src" "green" "red"]
  :dependencies [[jonase/eastwood "RELEASE"]
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.namespace "1.1.1"]]
  :plugins [[jonase/eastwood "RELEASE"]]
  ;; no green or red:
  ;; - green will be inferred via refresh-dirs
  ;; - red will be ignored because it's not set in refresh-dirs,
  ;; so its reflection warning won't show up in Eastwood output.
  :eastwood {:source-paths ["src"]})
