(defproject eastwood "0.0.1"
  :description "A Clojure lint tool"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 
                 [org.clojure/data.json "0.1.1"] ;; OK - reflection warnings
                 [org.clojure/core.match "0.2.0-alpha8"] ;;ArityException
                 [org.clojure/data.finger-tree "0.0.1"] ;;NPE
                 [org.clojure/tools.logging "0.2.3"] ;; OK
                 [org.clojure/java.jdbc "0.1.1"] ;; OK, using deprecated replicate + naked use.
                 [org.clojure/data.csv "0.1.0"] ;; OK
                 
                 ])