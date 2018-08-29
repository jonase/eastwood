(ns eastwood.copieddeps.dep6.leinjacker.eval-in-project
  "Provides an eval-in-project function that should work for both Leiningen 1
  and Leiningen 2."
  (:require [eastwood.copieddeps.dep6.leinjacker.eval :as real-eip]
            [eastwood.copieddeps.dep6.leinjacker.utils :as utils]))

(defn eval-in-project
  "This function is deprecated.  Use, eastwood.copieddeps.dep6.leinjacker.eval/eval-in-project instead."
  ([project form init]
   (println "This function is deprecated.  Use, eastwood.copieddeps.dep6.leinjacker.eval/eval-in-project instead.")
   (real-eip/eval-in-project project form init))
  ([project form]
   (println "This function is deprecated.  Use, eastwood.copieddeps.dep6.leinjacker.eval/eval-in-project instead.")
   (real-eip/eval-in-project project form)))
