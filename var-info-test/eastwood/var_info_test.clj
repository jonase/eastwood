(ns eastwood.var-info-test
  (:require
   [clojure.test :refer [deftest is]]
   [eastwood.util]
   [eastwood.linters.typos]
   [clojure.string :as string]))

(when-not (System/getProperty "eastwood.internal.plugin-profile-active")
  (let [p "eastwood.internal.running-test-suite"
        v (System/getProperty p)]
    (assert (= "true" v)
            (format "The test suite should be running with the %s system property set, for extra test robustness"
                    p))))

(deftest var-info
  (let [v (with-out-str
            (eastwood.util/print-var-info-summary @eastwood.linters.typos/var-info-map-delayed))]
    (or (is (not (string/includes? v eastwood.util/loaded-but-not-in-file-marker))
            "The var-info.edn file does not have missing entries")
        (println v))))
