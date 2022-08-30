(ns dolly
  (:require
   [dolly.clone :as c]))

(def dry-run {:dry-run? true :print? true})
(def for-real {:dry-run? false :print? true})

(def e-root (System/getProperty "user.dir"))
(def src-path (str e-root "/copied-deps"))
(def staging-path (str e-root "/staging"))

(def taj-src-path (str e-root "/copy-deps-scripts/repos/tools.analyzer.jvm/src/main/clojure"))
(def ta-src-path (str e-root "/copy-deps-scripts/repos/tools.analyzer/src/main/clojure"))
(def cm-src-path (str e-root "/copy-deps-scripts/repos/core.memoize/src/main/clojure"))
(def ccache-src-path (str e-root "/copy-deps-scripts/repos/core.cache/src/main/clojure"))
(def dp-src-path (str e-root "/copy-deps-scripts/repos/data.priority-map/src/main/clojure"))
(def tr-src-path (str e-root "/copy-deps-scripts/repos/tools.reader/src/main/clojure"))
(def tn-src-path (str e-root "/copy-deps-scripts/repos/tools.namespace/src/main/clojure"))
(def jc-src-path (str e-root "/copy-deps-scripts/repos/java.classpath/src/main/clojure"))

(defn -main [& _]
  ;; Change for-real to dry-run to see what will happen without
  ;; changing anything.  Look it over to see if it is reasonable.
  (c/copy-namespaces-unmodified taj-src-path staging-path 'clojure.tools.analyzer for-real)

  ;; The copying above should make no modifications, so running a
  ;; diff command like the following from the dolly project root
  ;; directory in a command shell should show no differences.

  ;; diff -cr copy-deps-scripts/repos/tools.analyzer.jvm/src/main/clojure staging

  ;; Now move the files from the staging area into dolly's code and
  ;; rename the namespaces.

  (c/move-namespaces-and-rename staging-path src-path 'clojure.tools.analyzer 'eastwood.copieddeps.dep2.clojure.tools.analyzer [src-path] for-real)

  ;; tools.analyzer
  (c/copy-namespaces-unmodified ta-src-path staging-path 'clojure.tools.analyzer for-real)
  (c/move-namespaces-and-rename staging-path src-path 'clojure.tools.analyzer 'eastwood.copieddeps.dep1.clojure.tools.analyzer [src-path] for-real)

  ;; core.memoize
  (c/copy-namespaces-unmodified cm-src-path staging-path 'clojure.core.memoize for-real)
  (c/move-namespaces-and-rename staging-path src-path 'clojure.core.memoize 'eastwood.copieddeps.dep3.clojure.core.memoize [src-path] for-real)

  ;; core.cache
  (c/copy-namespaces-unmodified ccache-src-path staging-path 'clojure.core.cache for-real)
  (c/move-namespaces-and-rename staging-path src-path 'clojure.core.cache 'eastwood.copieddeps.dep4.clojure.core.cache [src-path] for-real)

  ;; data.priority-map
  (c/copy-namespaces-unmodified dp-src-path staging-path 'clojure.data.priority-map for-real)
  (c/move-namespaces-and-rename staging-path src-path 'clojure.data.priority-map 'eastwood.copieddeps.dep5.clojure.data.priority-map [src-path] for-real)

  ;; tools.reader
  (c/copy-namespaces-unmodified tr-src-path staging-path 'clojure.tools.reader for-real)
  (c/move-namespaces-and-rename staging-path src-path 'clojure.tools.reader 'eastwood.copieddeps.dep10.clojure.tools.reader [src-path] for-real)

  ;; tools.namespace
  (c/copy-namespaces-unmodified tn-src-path staging-path 'clojure.tools.namespace for-real)
  (c/move-namespaces-and-rename staging-path src-path 'clojure.tools.namespace 'eastwood.copieddeps.dep9.clojure.tools.namespace [src-path] for-real)

  ;; java.classpath
  (c/copy-namespaces-unmodified jc-src-path staging-path 'clojure.java.classpath for-real)
  (c/move-namespaces-and-rename staging-path src-path 'clojure.java.classpath 'eastwood.copieddeps.dep11.clojure.java.classpath [src-path] for-real))
