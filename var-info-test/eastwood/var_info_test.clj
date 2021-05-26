(ns eastwood.var-info-test
  (:require
   [clojure.test :refer [deftest is]]
   [eastwood.util]
   [eastwood.linters.typos]
   [clojure.string :as string]))

(deftest var-info
  (let [v (with-out-str
            (eastwood.util/print-var-info-summary @eastwood.linters.typos/var-info-map-delayed))]
    (is (not (string/includes? v eastwood.util/loaded-but-not-in-file-marker))
        "The var-info.edn file does not have missing entries")))
