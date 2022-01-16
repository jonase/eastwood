(ns eastwood.clojure-core-test
  "This ns exercises the linting of clojure.core namespaces.

  They deserve a separte :test-path because explicit re-evaluation has some risk of affecting the rest of the test suite."
  (:require
   [clojure.test :refer [deftest is testing]]
   [eastwood.lint :as sut]))

(when-not (System/getProperty "eastwood.internal.plugin-profile-active")
  (let [p "eastwood.internal.running-test-suite"
        v (System/getProperty p)]
    (assert (= "true" v)
            (format "The test suite should be running with the %s system property set, for extra test robustness"
                    p))))

(defn clojure-core-namespaces []
  (->> (all-ns)
       (map str)
       (filter (fn [^String s]
                 (-> s (.startsWith "clojure."))))
       (remove (fn [^String s]
                 (or (-> s (.startsWith "clojure.core.async"))
                     (-> s (.startsWith "clojure.tools"))
                     (-> s (.startsWith "clojure.spec"))
                     (-> s (.contains "core.cache"))
                     (-> s (.contains "java.data"))
                     (-> s (.contains ".data."))
                     (-> s (.contains "numeric-tower"))
                     (-> s (.contains "jdbc"))
                     (-> s (.contains "core.memoize"))
                     (-> s (.contains "rrb")))))
       (map symbol)
       (sort)
       (into #{})))

(deftest clojure-core
  (testing "clojure.core libraries can be successfully analyzed without exceptions thrown or significant found faults"
    (is (= {:some-warnings false
            :some-errors false}
           (sut/eastwood (assoc sut/default-opts
                                :ignored-faults {:misplaced-docstrings {'clojure.set [{:line 13}]}
                                                 :deprecations {'clojure.test.junit [{:line 118}]}
                                                 :constant-test {'clojure.main [{:line 653}]}}
                                :namespaces (->> '[clojure.core
                                                   clojure.core.protocols
                                                   clojure.core.reducers
                                                   clojure.core.server
                                                   clojure.core.specs.alpha
                                                   clojure.data
                                                   clojure.datafy
                                                   clojure.edn
                                                   clojure.inspector
                                                   clojure.instant
                                                   clojure.java.browse
                                                   clojure.java.classpath
                                                   clojure.java.io
                                                   clojure.java.javadoc
                                                   clojure.math
                                                   clojure.java.shell
                                                   clojure.main
                                                   clojure.pprint
                                                   clojure.repl
                                                   clojure.set
                                                   clojure.stacktrace
                                                   clojure.string
                                                   clojure.template
                                                   clojure.test
                                                   clojure.test.junit
                                                   clojure.test.tap
                                                   clojure.uuid
                                                   clojure.walk
                                                   clojure.xml
                                                   clojure.zip]
                                                 (keep (fn [x]
                                                         (try
                                                           ;; some of these may be unreachable depending on the clojure version or JDK version:
                                                           (doto x require)
                                                           (catch Exception _
                                                             nil))))
                                                 ;; possibly add some unforeseen namespaces (in face of new clojure releases):
                                                 (into (clojure-core-namespaces))
                                                 ;; would cause a protocol reloading issue:
                                                 (remove #{'clojure.reflect}))
                                :exclude-linters [:local-shadows-var
                                                  :redefd-vars
                                                  :unused-ret-vals
                                                  :reflection
                                                  :non-dynamic-earmuffs
                                                  :implicit-dependencies
                                                  :unused-ret-vals-in-try]))))))
