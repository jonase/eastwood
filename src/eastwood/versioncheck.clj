(ns eastwood.versioncheck
  (:require
   [eastwood.exit :refer [exit-fn]]))

(def main
  (delay
    (do
      (require 'eastwood.lint)
      (resolve (symbol "eastwood.lint/eastwood-from-cmdline")))))

(defn run-eastwood [opts]
  (let [{:keys [major minor]} *clojure-version*]
    (when-not (>= (compare [major minor] [1 7]) 0)
      (println "Eastwood requires Clojure version >= 1.7.0. This project uses version"
               (clojure-version))
      ((exit-fn) 1)))
  (@main opts))
