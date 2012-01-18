(defproject eastwood "0.0.1"
  :description "A Clojure lint tool"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [analyze "0.1.3-SNAPSHOT"]

                 ;; clojure.data.json OK
                 [org.clojure/data.json "0.1.1"]
                 
                 ;; clojure.core.match: lots of reflection warnings -- eastwood fault
                 [org.clojure/core.match "0.2.0-alpha8"]
                 
                 ;; clojure.core.logic: 2x misplaced docstrings, subst? never used + lots of reflection
                 [org.clojure/core.logic "0.6.7"]
                 
                 ;; clojure.data.finger-tree: lots of reflection
                 [org.clojure/data.finger-tree "0.0.1"]
                 
                 ;; clojure.tools.logging: OK
                 [org.clojure/tools.logging "0.2.3"]

                 ;; clojure.java.jdbc: using deprecated replicate + naked use.
                 ;; clojure.java.jdbc.internal: reflection getCause
                 [org.clojure/java.jdbc "0.1.1"]

                 ;; clojure.data.csv: OK
                 [org.clojure/data.csv "0.1.0"] 
                 ])