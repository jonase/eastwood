(defproject jonase/eastwood "0.3.10"
  :description "A Clojure lint tool"
  :url "https://github.com/jonase/eastwood"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars {*warn-on-reflection* true
                ;;*unchecked-math* :warn-on-boxed
                }
  :source-paths ["src" "copied-deps"]
  :dependencies [
                 ;[org.clojure/clojure "1.5.1"]
                 ;[org.clojure/clojure "1.6.0"]
                 ;[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojure "1.8.0"]
                 ;[org.clojure/clojure "1.9.0-master-SNAPSHOT"]
                 [org.clojars.brenton/google-diff-match-patch "0.1"]
                 [org.ow2.asm/asm-all "5.2"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.macro "0.1.5"]
                                  [jafingerhut/dolly "0.1.0"]
                                  [leiningen-core "2.7.1"]]
                   ;; I want the namespaces defined in files in
                   ;; "cases" and its subdirectories to be accessible
                   ;; during 'lein test' in the classpath, but they
                   ;; themselves should not be eval'd by 'lein test'.
                   ;; I also want those namespaces available for
                   ;; one-off tests at the command line like "lein
                   ;; eastwood '{:namespaces [testcases.testtest]}'",
                   ;; and for that, the :test profile does not work,
                   ;; so put them in :dev
                   :resource-paths [ "cases" ]}
             :test {:dependencies [
                                   ;; Needed for issue-173-test
                                   [commons-io "2.4"]
                                   ]
                    :resource-paths ["test-resources"]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}}
  :aliases {"test-all" ["with-profile"
                        "dev,test,1.6:dev,test,1.7:dev,test,1.8:dev,test,1.9"
                        "test"]}
  :eastwood {:source-paths ["src"]
             :test-paths ["test"]
             :only-modified true
             :debug #{}}
  :plugins [[net.assum/lein-ver "1.2.0"]]
  :lein-ver {:version-file "resources/EASTWOOD_VERSION"}
  ;; Note: comment out the following line if you want to do 'lein
  ;; test' and get a Clojure version later than 1.5.1, even if you
  ;; have an explicit org.clojure/clojure in the :dependencies
  ;; Also: Having the following line uncommented causes a problem when
  ;; doing 'lein test', I think because of a different version of
  ;; core.cache or core.memoize on which the latest tools.analyzer.jvm
  ;; 0.1.0-SNAPSHOT depends, versus what Leiningen itself probably
  ;; depends on.
;;  :eval-in-leiningen true
  ;; Eastwood may work with earlier Leiningen versions, but this is
  ;; close to the earliest version that it was most tested with.
  :min-lein-version "2.3.0"
  :resource-paths ["resource" "resources"]
  :eval-in-leiningen true)
