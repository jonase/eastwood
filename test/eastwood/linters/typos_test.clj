(ns eastwood.linters.typos-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [eastwood.linters.typos :as sut]))

(deftest seq-call-from-destructuring?
  (testing "Detects a very specific call, for preventing false positives/negatives"
    (are [desc input expected] (testing input
                                 (is (= expected
                                        (sut/seq-call-from-destructuring? input))
                                     desc)
                                 true)
      "Only the targeted shape returns `true`"
      '(if (clojure.core/seq? map__413572) (clojure.lang.PersistentHashMap/create (clojure.core/seq map__413572)) map__413572)
      true

      "Placing an unexpected `seq?`"
      '(if (clojure.core/seq? map__413572) (clojure.lang.PersistentHashMap/create (clojure.core/seq? map__413572)) map__413572)
      false

      "Placing an unexpected `sequential?`"
      '(if (clojure.core/sequential? map__413572) (clojure.lang.PersistentHashMap/create (clojure.core/seq map__413572)) map__413572)
      false

      "Using a naming other than `map__`"
      '(if (clojure.core/seq? mOp__413572) (clojure.lang.PersistentHashMap/create (clojure.core/seq map__413572)) map__413572)
      false

      "Using a naming other than `map__`"
      '(if (clojure.core/seq? map__413572) (clojure.lang.PersistentHashMap/create (clojure.core/seq map__413572)) m0p__413572)
      false)))

(deftest inlined-identical-test
  (let [any-sym (gensym)]
    (are [input expected] (testing input
                            (is (= expected
                                   (sut/inlined-identical-test {:op :static-call
                                                                :form input})))
                            true)
      nil                                               nil
      42                                                nil
      '(. clojure.lang.Util (identical x nil))          'x
      `(. clojure.lang.Util (~'identical ~any-sym nil)) any-sym
      '(. clojure.lang.Util (identical 42 nil))         nil
      '(. clojure.lang.Other (identical x nil))         nil
      '(. clojure.lang.Util (identical x 42))           nil)))
