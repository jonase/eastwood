(defproject eastwood "0.0.1"
  :description "A Clojure lint tool"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [analyze "0.1.2"]

                 ;; OK - reflection warnings
                 #_[org.clojure/data.json "0.1.1"]
                 ;; OK
                 #_[org.clojure/core.match "0.2.0-alpha8"]
                 ;; OK. found stuff.
                 #_[org.clojure/core.logic "0.6.7"]
                 ;; OK
                 #_[org.clojure/data.finger-tree "0.0.1"]
                 ;; OK
                 #_[org.clojure/tools.logging "0.2.3"]
                 ;; OK, using deprecated replicate + naked use.
                 #_[org.clojure/java.jdbc "0.1.1"]
                 ;; OK
                 #_[org.clojure/data.csv "0.1.0"] 
                 ])