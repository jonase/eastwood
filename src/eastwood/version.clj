(ns eastwood.version
  (:require [clojure.java.io :refer [reader resource]]
            [clojure.string :refer [join]])
  (:import java.io.PushbackReader))
(let [version-file (resource "VERSION")]
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
