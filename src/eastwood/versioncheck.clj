(ns eastwood.versioncheck)

(def main
  (delay
   (do
     (require 'eastwood.core)
     (resolve (symbol "eastwood.core/run-eastwood")))))

(defn run-eastwood [opts]
  (let [{:keys [major minor]} *clojure-version*]
    (when-not (>= (compare [major minor] [1 4]) 0)
      (println "Eastwood requires Clojure version >= 1.4.0.  This project uses version"
               (clojure-version))
      (System/exit 1)))
  (@main opts))
