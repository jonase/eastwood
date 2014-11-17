(ns eastwood.versioncheck)

(def main
  (delay
   (do
     (require 'eastwood.lint)
     (resolve (symbol "eastwood.lint/eastwood-from-cmdline")))))

(defn run-eastwood [opts]
  (let [{:keys [major minor]} *clojure-version*]
    (when-not (>= (compare [major minor] [1 5]) 0)
      (println "Eastwood requires Clojure version >= 1.5.0.  This project uses version"
               (clojure-version))
      (System/exit 1)))
  (@main opts))
