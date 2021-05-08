(ns eastwood.linters.typos-test
  (:require
   [clojure.test :refer [are deftest is join-fixtures testing use-fixtures]]
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
