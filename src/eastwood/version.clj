(ns eastwood.version
  (:require [clojure.java.io :refer [reader resource]]
            [clojure.string :refer [join]])
  (:import java.io.PushbackReader))
(let [version-file (resource "EASTWOOD_VERSION")]
  (when version-file
    (with-open [rdr (reader version-file)]
      (binding [*read-eval* false]
        (def version (read (PushbackReader. rdr)))
        (def major (:major version))
        (def minor (:minor version))
        (def patch (:patch version))
        (def pre-release (:pre-release version))
        (def build (:build version))
        (def sha (:sha version))
        (def string (str (join "." (filter identity [major minor patch]))
                         (when pre-release (str "-" pre-release))
                         (when build (str "+" build))))))))

(def ^:dynamic *eastwood-version*
  {:major major, :minor minor, :incremental patch, :qualifier pre-release})

(defn eastwood-version [] string)

(defn versions []
  {:eastwood-version-map *eastwood-version*
   :eastwood-version-string (eastwood-version)
   :clojure-version-map *clojure-version*
   :clojure-version-string (clojure-version)
   :jvm-version-string (get (System/getProperties) "java.version")})

(defn version-string []
  (let [versions (versions)]
    (format "== Eastwood %s Clojure %s JVM %s =="
            (:eastwood-version-string versions)
            (:clojure-version-string versions)
            (:jvm-version-string versions))))
