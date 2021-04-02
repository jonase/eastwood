(ns eastwood.lint-test
  (:require
   [clojure.test :refer :all]
   [eastwood.copieddeps.dep11.clojure.java.classpath :as classpath]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.dir :as dir]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.track :as track]
   [eastwood.lint :as sut :refer :all]
   [eastwood.reporting-callbacks :as reporting]
   [eastwood.util :as util])
  (:import
   (java.io File)))

(deftest expand-ns-keywords-test
  (testing ""
    (is (= ["foo" "bar" "baz"] (expand-ns-keywords {:source-paths ["foo"]} [:source-paths "bar" "baz"])))))

(deftest last-options-map-adjustments-test
  (let [reporter (reporting/silent-reporter default-opts)]
    (testing "default-options are added"
      (is (= default-opts
             (dissoc (last-options-map-adjustments nil reporter)
                     :warning-enable-config))))
    (testing "passed options are respected. So are the type and sort order of :namespaces."
      (is (= (assoc default-opts
                    :namespaces ["foo" "bar"])
             (dissoc (last-options-map-adjustments {:namespaces ["foo" "bar"]} reporter)
                     :warning-enable-config)))
      (is (= (assoc default-opts
                    :namespaces #{"foo"})
             (dissoc (last-options-map-adjustments {:namespaces #{"foo"}} reporter)
                     :warning-enable-config)))
      (is (= (assoc default-opts
                    :namespaces '("foo" "bar"))
             (dissoc (last-options-map-adjustments {:namespaces '("foo" "bar")} reporter)
                     :warning-enable-config))))
    (testing "all the things are sets (except :namespaces, which keep their original class)"
      (is (empty? (->>
                   (select-keys (last-options-map-adjustments {:namespaces []} reporter) [:debug :source-paths :test-paths :exclude-namespaces])
                   (vals)
                   (remove set?)))))))

(deftest setup-lint-paths-test
  (testing "non-empty source/test paths is respected"
    (is (= {:source-paths #{"lol"}
            :test-paths #{}}
           (setup-lint-paths #{"lol"} nil)))
    (is (= {:source-paths #{}
            :test-paths #{"lol"}}
           (setup-lint-paths nil #{"lol"})))
    (is (= {:source-paths #{"lol"}
            :test-paths #{"bar"}}
           (setup-lint-paths #{"lol"} #{"bar"}))))
  (testing "empty source/test paths yields classpath-directories"
    (with-redefs [classpath/classpath-directories (fn [] [(File. "lol")])]
      (is (= {:source-paths #{(File. "lol")}
              :test-paths #{}}
             (setup-lint-paths nil nil))))))

(def eastwood-src-namespaces (::track/load (dir/scan-dirs (track/tracker) #{"src"})))
(def eastwood-test-namespaces (::track/load (dir/scan-dirs (track/tracker) #{"test"})))
(def eastwood-all-namespaces (concat eastwood-src-namespaces eastwood-test-namespaces))

(deftest nss-in-dirs-test
  (let [dirs #{"src"}]
    (testing "basic functionality"
      (is (= {:dirs (set (map util/canonical-filename dirs))
              :namespaces eastwood-src-namespaces}
             (select-keys (nss-in-dirs dirs 0) [:dirs :namespaces]))))))

(deftest effective-namespaces-test
  (let [source-paths #{"src"}
        test-paths #{"test"}]
    (testing ""
      (is (= {:dirs (concat (map util/canonical-filename source-paths)
                            (map util/canonical-filename test-paths))
              :namespaces eastwood-all-namespaces}
             (select-keys (effective-namespaces #{}
                                                #{:source-paths :test-paths}
                                                {:source-paths source-paths
                                                 :test-paths test-paths} 0)
                          [:dirs :namespaces]))))))

(deftest exceptions-test
  (let [valid-namespaces   (take 1 eastwood-src-namespaces)
        invalid-namespaces '[invalid.syntax]]
    (are [namespaces rethrow-exceptions? ok?] (testing [namespaces rethrow-exceptions?]
                                                (try
                                                  (eastwood.lint/eastwood (assoc eastwood.lint/default-opts :namespaces namespaces :rethrow-exceptions? rethrow-exceptions?))
                                                  ok?
                                                  (catch Exception _
                                                    (not ok?))))
      []                 false true
      []                 true  true
      invalid-namespaces false true
      invalid-namespaces true  false
      valid-namespaces   true  true
      valid-namespaces   false true)))

(deftest large-defprotocol-test
  (testing "A large defprotocol doesn't cause a 'Method code too large' exception"
    (is (= {:some-warnings false}
           (eastwood.lint/eastwood (assoc eastwood.lint/default-opts :namespaces #{'testcases.large-defprotocol}))))))

(deftest ignore-fault?-test
  (are [input expected] (= expected
                           (sut/ignore-fault? input
                                              {:warn-data {:namespace-sym 'some-ns
                                                           :line 1
                                                           :column 2
                                                           :linter :some-linter}}))
    nil                                                                        false
    {}                                                                         false
    {:some-linter {'some-ns true}}                                             true
    {:some-linter {'some-ns [true]}}                                           true
    {:different-linter {'some-ns true}}                                        false
    {:different-linter {'some-ns [true]}}                                      false
    {:some-linter {'different-ns true}}                                        false
    {:some-linter {'different-ns [true]}}                                      false
    {:some-linter {'some-ns {:line 1 :column 2}}}                              true
    {:some-linter {'some-ns [{:line 1 :column 2}]}}                            true
    {:some-linter {'some-ns {:line 999 :column 2}}}                            false
    {:some-linter {'some-ns [{:line 999 :column 2}]}}                          false
    {:some-linter {'some-ns {:line 1 :column 999}}}                            false
    {:some-linter {'some-ns [{:line 1 :column 999}]}}                          false
    {:some-linter {'some-ns [{:line 1 :column 2} {:line 1 :column 999}]}}      true
    {:some-linter {'some-ns [{:line 1 :column 999} {:line 1 :column 2}]}}      true
    {:some-linter {'different-ns {:line 1 :column 2}}}                         false
    {:some-linter {'different-ns [{:line 1 :column 2}]}}                       false
    {:some-linter {'different-ns {:line 999 :column 2}}}                       false
    {:some-linter {'different-ns [{:line 999 :column 2}]}}                     false
    {:some-linter {'different-ns {:line 1 :column 999}}}                       false
    {:some-linter {'different-ns [{:line 1 :column 999}]}}                     false
    {:some-linter {'different-ns [{:line 1 :column 2} {:line 1 :column 999}]}} false
    ;; Exercises line-only matching:
    {:some-linter {'some-ns {:line 1}}}                                        true
    {:some-linter {'some-ns [{:line 1}]}}                                      true
    {:some-linter {'some-ns {:line 999}}}                                      false
    {:some-linter {'some-ns [{:line 999}]}}                                    false
    {:some-linter {'some-ns [{:line 1} {:line 1}]}}                            true
    {:some-linter {'some-ns [{:line 1} {:line 2}]}}                            true
    {:some-linter {'some-ns [{:line 2} {:line 1} {:line 2}]}}                  true
    {:some-linter {'different-ns {:line 1}}}                                   false
    {:some-linter {'different-ns [{:line 1}]}}                                 false
    {:some-linter {'different-ns {:line 999}}}                                 false
    {:some-linter {'different-ns [{:line 999}]}}                               false
    {:some-linter {'different-ns [{:line 1} {:line 1}]}}                       false))

(deftest ignored-faults-test
  (testing "A ignored-faults can remove warnings.
The ignored-faults must match ns (exactly) and file/column (exactly, but only if provided)"
    (are [input expected] (= expected
                             (-> eastwood.lint/default-opts
                                 (assoc :namespaces #{'testcases.ignored-faults-example}
                                        :ignored-faults input)
                                 (eastwood.lint/eastwood)))
      {}                                                                                {:some-warnings true}
      {:implicit-dependencies {'testcases.ignored-faults-example true}}                 {:some-warnings false}
      {:implicit-dependencies {'testcases.ignored-faults-example [{:line 4 :column 1}]}}  {:some-warnings false}
      {:implicit-dependencies {'testcases.ignored-faults-example [{:line 4 :column 99}]}} {:some-warnings true}
      {:implicit-dependencies {'testcases.ignored-faults-example [{:line 99 :column 1}]}} {:some-warnings true})))

(deftest const-handling
  (testing "Processing a namespace where `^:const` is used results in no exceptions being thrown"
    (is (= {:some-warnings false}
           (eastwood.lint/eastwood (assoc eastwood.lint/default-opts :namespaces #{'testcases.const}))))))
