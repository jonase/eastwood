(defproject example-project "n/a"
  :source-paths ["src" "red"]
  :dependencies [[jonase/eastwood "RELEASE"]
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.namespace "1.1.1"]]
  :plugins [[jonase/eastwood "RELEASE"]]
  ;; no red:
  ;; red will be ignored because it's not set passed as Eastwood config/arguments,
  ;; so its reflection warning won't show up in Eastwood output.
  :eastwood {:source-paths ["src"]})
