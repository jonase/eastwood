(ns eastwood.lint-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [eastwood.copieddeps.dep11.clojure.java.classpath :as classpath]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.dir :as dir]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.track :as track]
   [eastwood.lint :as sut :refer [with-memoization-bindings]]
   [eastwood.reporting-callbacks :as reporting]
   [eastwood.util :as util])
  (:import
   (java.io File)))

(when-not (System/getProperty "eastwood.internal.plugin-profile-active")
  (let [p "eastwood.internal.running-test-suite"
        v (System/getProperty p)]
    (assert (= "true" v)
            (format "The test suite should be running with the %s system property set, for extra test robustness"
                    p))))

(deftest expand-ns-keywords-test
  (is (= ["foo" "bar" "baz"] (sut/expand-ns-keywords {:source-paths ["foo"]} [:source-paths "bar" "baz"]))))

(deftest last-options-map-adjustments-test
  (let [reporter (reporting/silent-reporter sut/default-opts)]
    (testing "default-options are added"
      (is (= sut/default-opts
             (dissoc (sut/last-options-map-adjustments nil reporter)
                     :warning-enable-config
                     :eastwood/exclude-linters))))
    (testing "passed options are respected. So are the type and sort order of :namespaces."
      (is (= (assoc sut/default-opts
                    :namespaces ["foo" "bar"])
             (dissoc (sut/last-options-map-adjustments {:namespaces ["foo" "bar"]} reporter)
                     :warning-enable-config
                     :eastwood/exclude-linters)))
      (is (= (assoc sut/default-opts
                    :namespaces #{"foo"})
             (dissoc (sut/last-options-map-adjustments {:namespaces #{"foo"}} reporter)
                     :warning-enable-config
                     :eastwood/exclude-linters)))
      (is (= (assoc sut/default-opts
                    :namespaces '("foo" "bar"))
             (dissoc (sut/last-options-map-adjustments {:namespaces '("foo" "bar")} reporter)
                     :warning-enable-config
                     :eastwood/exclude-linters))))
    (testing "all the things are sets (except :namespaces, which keep their original class)"
      (is (empty? (->>
                   (select-keys (sut/last-options-map-adjustments {:namespaces []} reporter)
                                [:debug :source-paths :test-paths :exclude-namespaces])
                   (vals)
                   (remove set?)))))))

(deftest setup-lint-paths-test
  (testing "non-empty source/test paths is respected"
    (is (= {:source-paths #{"src"}
            :test-paths #{}}
           (sut/setup-lint-paths #{} #{"src"} nil)))
    (is (= {:source-paths #{}
            :test-paths #{"test"}}
           (sut/setup-lint-paths #{} nil #{"test"})))
    (is (= {:source-paths #{"src"}
            :test-paths #{"test"}}
           (sut/setup-lint-paths #{} #{"src"} #{"test"}))))
  (testing "empty source/test paths yields classpath-directories"
    (with-redefs [classpath/classpath-directories (constantly [(File. "src")])]
      (is (= {:source-paths #{(File. "src")}
              :test-paths #{}}
             (sut/setup-lint-paths #{} nil nil))))))

(def eastwood-src-namespaces (::track/load (dir/scan-dirs (track/tracker) #{"src"})))
(def eastwood-test-namespaces (::track/load (dir/scan-dirs (track/tracker) #{"test"})))
(def eastwood-all-namespaces (concat eastwood-src-namespaces eastwood-test-namespaces))

(deftest nss-in-dirs-test
  (let [dirs #{"src"}]
    (testing "basic functionality"
      (is (= {:dirs (set (map util/canonical-filename dirs))
              :namespaces eastwood-src-namespaces}
             (select-keys (sut/nss-in-dirs dirs 0) [:dirs :namespaces]))))))

(deftest effective-namespaces-test
  (let [source-paths #{"src"}
        test-paths #{"test"}]
    (is (= {:dirs (concat (map util/canonical-filename source-paths)
                          (map util/canonical-filename test-paths))
            :namespaces (sort eastwood-all-namespaces)}
           (-> (with-memoization-bindings
                 (sut/effective-namespaces #{}
                                           #{:source-paths :test-paths}
                                           {:source-paths source-paths
                                            :test-paths test-paths}
                                           {:source-paths source-paths
                                            :test-paths test-paths} 0))
               (select-keys [:dirs :namespaces])
               (update :namespaces sort))))))

(deftest exceptions-test
  (let [valid-namespaces   (take 1 eastwood-src-namespaces)
        invalid-namespaces '[invalid.syntax]]

    (testing "Reader-level exceptions are reported as such"
      (is (= {:some-warnings true :some-errors true}
             (sut/eastwood (assoc sut/default-opts :namespaces invalid-namespaces)))))

    (testing "Reader-level exceptions are reported as such"
      (let [^String s (with-out-str
                        (sut/eastwood (assoc sut/default-opts :namespaces invalid-namespaces)))]
        (is (-> s
                (.contains "Warnings: 0. Exceptions thrown: 1")))))

    (testing "`:rethrow-exceptions?` option"
      (are [namespaces rethrow-exceptions? ok?] (testing [namespaces rethrow-exceptions?]
                                                  (is (try
                                                        (sut/eastwood (assoc sut/default-opts :namespaces namespaces :rethrow-exceptions? rethrow-exceptions?))
                                                        ok?
                                                        (catch Exception _
                                                          (not ok?))))
                                                  true)
        #_namespaces       #_rethrow-exceptions? #_ok?
        []                 false                 true
        []                 true                  true
        invalid-namespaces false                 true
        invalid-namespaces true                  false
        valid-namespaces   true                  true
        valid-namespaces   false                 true))))

(deftest empty-corpus-set
  (testing "If no ns will be linted, Eastwood will fail"
    (is (= {:some-warnings true
            :some-errors false}
           (sut/eastwood (assoc sut/default-opts :namespaces #{}))))))

(deftest large-defprotocol-test
  (testing "A large defprotocol doesn't cause a 'Method code too large' exception"
    (is (= {:some-warnings false
            :some-errors false}
           (sut/eastwood (assoc sut/default-opts :namespaces #{'testcases.large-defprotocol}))))))

(deftest ignore-fault?-test
  (are [input expected] (testing input
                          (is (= expected
                                 (sut/ignore-fault? input
                                                    {:warn-data {:namespace-sym 'some-ns
                                                                 :line 1
                                                                 :column 2
                                                                 :linter :some-linter}})))
                          true)
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
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces #{'testcases.ignored-faults-example}
                                              :ignored-faults input)
                                       (sut/eastwood))))
                            true)
      {}                                                                                  {:some-warnings true}
      {:implicit-dependencies {'testcases.ignored-faults-example true}}                   {:some-warnings false}
      {:implicit-dependencies {'testcases.ignored-faults-example [{:line 4 :column 1}]}}  {:some-warnings false}
      {:implicit-dependencies {'testcases.ignored-faults-example [{:line 4 :column 99}]}} {:some-warnings true}
      {:implicit-dependencies {'testcases.ignored-faults-example [{:line 99 :column 1}]}} {:some-warnings true})))

(deftest const-handling
  (testing "Processing a namespace where `^:const` is used results in no exceptions being thrown"
    (is (= {:some-warnings false
            :some-errors false}
           (sut/eastwood (assoc sut/default-opts :namespaces '#{testcases.const
                                                                testcases.const.unused-namespaces.consumer}))))))

(deftest test-metadata-handling
  (testing "Processing a vanilla defn where `^:test` is used results in no linter faults"
    (is (= {:some-warnings false
            :some-errors false}
           (sut/eastwood (assoc sut/default-opts :namespaces #{'testcases.test-metadata-example}))))))

(deftest wrong-tag-disabling-test
  (testing "The `:wrong-tag` linter can be selectively disabled via the `disable-warning` mechanism,
relative to a specific macroexpansion"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces #{'testcases.wrong-tag-example})
                                       (merge input)
                                       sut/eastwood)))
                            true)
      {}                                                          {:some-warnings true}
      {:builtin-config-files ["disable_wrong_tag.clj"]}           {:some-warnings false}
      {:builtin-config-files ["disable_wrong_tag_unrelated.clj"]} {:some-warnings true})))

(deftest unused-meta-on-macro-disabling-test
  (testing "The `:unused-meta-on-macro` linter can be selectively disabled via the `disable-warning` mechanism,
relative to a specific macroexpansion"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces #{'testcases.wrong-meta-on-macro-example}
                                              :ignore-faults-from-foreign-macroexpansions? false)
                                       (merge input)
                                       sut/eastwood)))
                            true)
      {}                                                                     {:some-warnings true}
      {:builtin-config-files ["disable_unused_meta_on_macro.clj"]}           {:some-warnings false}
      {:builtin-config-files ["disable_unused_meta_on_macro_unrelated.clj"]} {:some-warnings true})))

(deftest are-true-test
  (are [desc input expected] (testing input
                               (is (= (assoc expected :some-errors false)
                                      (-> sut/default-opts
                                          (assoc :namespaces input)
                                          (sut/eastwood)))
                                   desc)
                               true)
    "Supports the \"true at tail position\" pattern for `are`"
    #{'testcases.are-true.green}     {:some-warnings false}

    "does not ruin `(is true)` detection"
    #{'testcases.are-true.red-one}   {:some-warnings true}

    "does not ruin an edge case"
    #{'testcases.are-true.red-two}   {:some-warnings true}

    "only `true` is deemed an apt :qualifier value"
    #{'testcases.are-true.red-three} {:some-warnings true}))

(deftest clojure-test-test
  (testing "Some reported false positives against clojure.test"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input)
                                       (sut/eastwood))))
                            true)
      #{'testcases.clojure-test}         {:some-warnings false}
      #{'testcases.clojure-test.are.red} {:some-warnings true})))

(deftest let-test
  (testing "Some reported false positives against `let`"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input)
                                       (sut/eastwood))))
                            true)
      #{'testcases.let.green} {:some-warnings false}
      #{'testcases.let.red}   {:some-warnings true})))

(deftest while-true-test
  (testing "The `(while true)` idiom is supported"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input)
                                       (sut/eastwood))))
                            true)
      #{'testcases.while-true.green} {:some-warnings false}
      #{'testcases.while-true.red}   {:some-warnings true})))

(deftest is-false-test
  (testing "The `(is false)` idiom is supported"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input)
                                       (sut/eastwood))))
                            true)
      #{'testcases.is-false.green} {:some-warnings false}
      ;; no 'red' case for now, since there isn't one that would make sense today (given git.io/J35fr)
      )))

(deftest cond-test
  (testing "Some reported false positives against `cond`"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input)
                                       (sut/eastwood))))
                            true)
      #{'testcases.cond.green} {:some-warnings false}
      ;; some 'red' cases could be added at some point, there are no reported issues atm though.
      )))

(deftest bytes-class-test
  (testing "https://github.com/jonase/eastwood/issues/385"
    (is (= {:some-warnings false :some-errors false}
           (sut/eastwood (assoc sut/default-opts :namespaces #{'testcases.bytes-class.green}))))

    (is (= {:some-warnings false :some-errors false}
           (sut/eastwood (assoc sut/default-opts :namespaces #{'testcases.bytes-class.green2}))))))

(deftest bytes-array-test
  (testing "https://github.com/jonase/eastwood/issues/188"
    (is (= {:some-warnings false :some-errors false}
           (sut/eastwood (assoc sut/default-opts :namespaces #{'testcases.byte-array.green}))))))

(deftest unknown-reify-test
  (testing "https://github.com/jonase/eastwood/issues/205"
    (let [opts (assoc sut/default-opts :namespaces #{'testcases.unknown-reify})]

      (is (= {:some-warnings true :some-errors true}
             (sut/eastwood opts))
          "A non-compilable form is reported as an error")

      (let [^String v (with-out-str
                        (sut/eastwood opts))]
        (is (-> v
                (.contains "The following form was being processed during the exception:
(def foo (reify Unknown (foo [this])))"))
            "The culprit form is reported accurately.")))))

(deftest constant-test-test
  (testing "The :constant-test linter for if-some/when-some, per https://github.com/jonase/eastwood/issues/110"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input)
                                       (sut/eastwood))))
                            true)
      #{'testcases.constant-test.if-some.green}   {:some-warnings false}
      #{'testcases.constant-test.if-some.red}     {:some-warnings true}

      #{'testcases.constant-test.when-some.green} {:some-warnings false}
      #{'testcases.constant-test.when-some.red}   {:some-warnings true}))

  (testing "The :constant-test linter for some->/some->>, per https://github.com/jonase/eastwood/issues/397"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input)
                                       (sut/eastwood))))
                            true)
      ;; Unfortunately, no red cases are exercised because I don't think this can be fixed.
      ;; (some-> "") will check nilness of "" even when "" is a value known at compile time.
      ;; I believe that can be considered a (minor) issue in clojure.core, and working around it seems arduous.
      ;; Suggested an improvement upstream here:
      ;; https://ask.clojure.org/index.php/10712/minor-performance-improvement-initial-values-known-compile
      #{'testcases.constant-test.some-thread-first.green} {:some-warnings false}
      #{'testcases.constant-test.some-thread-last.green}  {:some-warnings false})))

(deftest unused-fn-args-test
  (testing "fn args within defmulti, see https://github.com/jonase/eastwood/issues/1"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input)
                                       (update :linters conj :unused-fn-args)
                                       (sut/eastwood))))
                            true)
      #{'testcases.unused-fn-args.multimethods.green} {:some-warnings false}
      #{'testcases.unused-fn-args.multimethods.red}   {:some-warnings true})))

(deftest implicit-dependencies-test
  (testing "Explicit `require` calls are accounted for. See https://github.com/jonase/eastwood/issues/22"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input)
                                       (sut/eastwood))))
                            true)
      #{'testcases.implicit-dependencies.explicit-require.green1} {:some-warnings false}
      #{'testcases.implicit-dependencies.explicit-require.green2} {:some-warnings false}
      #{'testcases.implicit-dependencies.explicit-require.green3} {:some-warnings false}
      #{'testcases.implicit-dependencies.explicit-require.green4} {:some-warnings false}
      #{'testcases.implicit-dependencies.explicit-require.green5} {:some-warnings false}
      #{'testcases.implicit-dependencies.explicit-require.red}    {:some-warnings true}

      #{'testcases.implicit-dependencies.cljc.green}              {:some-warnings false}
      #{'testcases.implicit-dependencies.cljc.red}                {:some-warnings true})))

(deftest deprecations-test
  (testing "type-overloaded Java arities, see https://github.com/jonase/eastwood/issues/329"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input)
                                       (sut/eastwood))))
                            true)
      #{'testcases.deprecations.overloading.green1} {:some-warnings false}
      #{'testcases.deprecations.overloading.green2} {:some-warnings false}
      #{'testcases.deprecations.overloading.red}    {:some-warnings true}))

  (testing "Disabling warnings via :symbol-matches"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces #{'testcases.deprecations.overloading.red}
                                              :builtin-config-files input)
                                       (sut/eastwood))))
                            true)
      []                               {:some-warnings true}
      ["disable_date_deprecation.clj"] {:some-warnings false}))

  (testing "Deprecations where producer and consumer defns belong to the same ns.
See https://github.com/jonase/eastwood/issues/402"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input)
                                       (sut/eastwood))))
                            true)
      #{'testcases.deprecations.own-ns.green} {:some-warnings false}
      #{'testcases.deprecations.own-ns.red}   {:some-warnings true})))

(deftest unused-ret-vals
  (testing "Method calls"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces input
                                              :ignore-faults-from-foreign-macroexpansions? false)
                                       (sut/eastwood))))
                            true)
      #{'testcases.unused-ret-vals.green1} {:some-warnings false}
      #{'testcases.unused-ret-vals.red1}   {:some-warnings true}
      #{'testcases.unused-ret-vals.red2}   {:some-warnings true}
      #{'testcases.unused-ret-vals.red3}   {:some-warnings true}
      #{'testcases.unused-ret-vals.red4}   {:some-warnings true})))

(deftest reflection
  (are [desc input expected] (testing input
                               ;; Remove the ns so that reflection state is reset on each run:
                               (remove-ns 'reflection-example.core)
                               (dosync (alter @#'clojure.core/*loaded-libs* disj 'reflection-example.core))

                               (is (= (assoc expected :some-errors false)
                                      (-> sut/default-opts
                                          (assoc :namespaces input
                                                 :linters [:reflection])
                                          sut/eastwood))
                                   desc)
                               true)
    "Merely `require`ing code that emits reflection warnings at compile-time won't cause Eastwood warnings (because it's third-party code)"
    #{'testcases.unhinted-reflective-call.unused-foreign-reflective-code}  {:some-warnings false}

    "Vanilla code that directly causes reflection warnings will cause Eastwood warnings"
    #{'testcases.unhinted-reflective-call.red}                             {:some-warnings true}

    "Calling a third-party defn that emits warnings will not cause Eastwood warnings (because the reflection happens outside our codebase)"
    #{'testcases.unhinted-reflective-call.foreign-defn-call}               {:some-warnings false}

    "Calling a third-party macro that emits warnings not cause Eastwood warnings (because the reflection happens inside our codebase)"
    #{'testcases.unhinted-reflective-call.foreign-macro-call}              {:some-warnings true}

    "Analysing code inside a .jar that emits warnings will cause Eastwood warnings (because we're analysing that code directly, so it shouldn't be omitted)"
    #{'reflection-example.core}                                            {:some-warnings true}

    "A macro call outside the refresh dirs results in an Eastwood warning"
    #{'testcases.unhinted-reflective-call.macro-call-outside-refresh-dirs} {:some-warnings true}

    "A macro call inside the refresh dirs results in an Eastwood warning"
    #{'testcases.unhinted-reflective-call.macro-call-inside-refresh-dirs}  {:some-warnings true}

    "A function call outside the refresh dirs does not result in an Eastwood warning"
    #{'testcases.unhinted-reflective-call.defn-call-outside-refresh-dirs}  {:some-warnings false}

    "A function call inside the refresh dirs results in an Eastwood warning"
    #{'testcases.unhinted-reflective-call.defn-call-inside-refresh-dirs}   {:some-warnings true})

  (testing "Integration with `:exclude-namespaces`"
    (are [desc namespaces exclude-namespaces expected] (testing [namespaces exclude-namespaces]

                                                         ;; Remove the ns so that reflection state is reset on each run:
                                                         (remove-ns 'testcases.unhinted-reflective-call.example-defn)
                                                         (dosync (alter @#'clojure.core/*loaded-libs* disj 'testcases.unhinted-reflective-call.example-defn))

                                                         (is (= (assoc expected :some-errors false)
                                                                (-> sut/default-opts
                                                                    (assoc :namespaces namespaces
                                                                           :exclude-namespaces exclude-namespaces
                                                                           :linters [:reflection])
                                                                    sut/eastwood))
                                                             desc)
                                                         true)
      #_desc
      #_namespaces                                                   #_exclude-namespaces                               #_expected
      "A given ns triggers reflection warnings"
      #{'testcases.unhinted-reflective-call.example-defn}            []                                                 {:some-warnings true}

      "Another ns triggers reflection warnings, because it depends on the former"
      #{'testcases.unhinted-reflective-call.depends-on-example-defn} []                                                 {:some-warnings true}

      "The dependent ns does not trigger warnings if the ns it depends on belongs to `:exclude-namespaces`"
      #{'testcases.unhinted-reflective-call.depends-on-example-defn} ['testcases.unhinted-reflective-call.example-defn] {:some-warnings false})))

(deftest ignore-faults-from-foreign-macroexpansions?
  (are [input expected] (testing input
                          (is (= (assoc expected :some-errors false)
                                 (-> sut/default-opts
                                     (assoc :namespaces #{'testcases.foreign-macroexpansions.red}
                                            :ignore-faults-from-foreign-macroexpansions? input)
                                     sut/eastwood)))
                          true)
    false {:some-warnings true}
    true  {:some-warnings false}))

(deftest non-dynamic-earmuffs
  (are [input expected] (testing input
                          (is (= (assoc expected :some-errors false)
                                 (-> sut/default-opts
                                     (assoc :namespaces input)
                                     (sut/eastwood))))
                          true)
    #{'testcases.dynamic-earmuffs.no-earmuffs.green} {:some-warnings false}
    #{'testcases.dynamic-earmuffs.no-earmuffs.red}   {:some-warnings true}
    #{'testcases.dynamic-earmuffs.no-dynamic.green}  {:some-warnings false}
    #{'testcases.dynamic-earmuffs.no-dynamic.red}    {:some-warnings true}))

(deftest boxed-math
  (are [input expected] (testing input
                          (is (= (assoc expected :some-errors false)
                                 (-> sut/default-opts
                                     (assoc :namespaces input
                                            :linters [:boxed-math])
                                     (sut/eastwood))))
                          true)
    #{'testcases.boxed-math.green} {:some-warnings false}
    #{'testcases.boxed-math.red}   {:some-warnings true}))

(deftest performance
  (are [input expected] (testing input
                          (is (= (assoc expected :some-errors false)
                                 (-> sut/default-opts
                                     (assoc :namespaces input
                                            ;; explicitly only run the :performance linter,
                                            ;; proving that it doesn't depend on the :reflection linter:
                                            :linters [:performance])
                                     (sut/eastwood))))
                          true)
    #{'testcases.performance.red.case}    {:some-warnings true}
    #{'testcases.performance.red.recur}   {:some-warnings true}
    #{'testcases.performance.red.hash}    {:some-warnings true}

    ;; the green cases demonstrate how each fault would be fixed:
    #{'testcases.performance.green.case}  {:some-warnings false}
    #{'testcases.performance.green.recur} {:some-warnings false}
    #{'testcases.performance.green.hash}  {:some-warnings false}))

(deftest subkind-silencing
  (testing "I can silence a specific linter :kind"
    (are [input expected] (testing input
                            (is (= (assoc expected :some-errors false)
                                   (-> sut/default-opts
                                       (assoc :namespaces #{'testcases.subkind-silencing.red}
                                              :exclude-linters input)
                                       sut/eastwood)))
                            true)
      #{}                                                {:some-warnings true}
      #{:suspicious-test}                                {:some-warnings false}
      #{[:suspicious-test :second-arg-is-not-string]}    {:some-warnings false}
      #{[:suspicious-test [::something-else]]}           {:some-warnings true}
      #{[:suspicious-test #{:second-arg-is-not-string}]} {:some-warnings false}
      #{[:suspicious-test #{:second-arg-is-not-string
                            ::something-else}]}          {:some-warnings false})))

(deftest refer-clojure-exclude
  (testing "https://github.com/jonase/eastwood/issues/185"
    (testing ":refer-clojure :exclude doesn't create false positives"
      (are [input expected] (testing input
                              (is (= (assoc expected :some-errors false)
                                     (-> sut/default-opts
                                         (assoc :namespaces input)
                                         (sut/eastwood))))
                              true)
        #{'testcases.refer-clojure-exclude.green} {:some-warnings false}
        #{'testcases.refer-clojure-exclude.red}   {:some-warnings true}))))
