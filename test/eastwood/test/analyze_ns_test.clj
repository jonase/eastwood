(ns eastwood.test.analyze-ns-test
  (:require
   [clojure.test :refer :all]
   [eastwood.analyze-ns :as analyze]))

(deftest test3
  (let [result (analyze/analyze-ns 'testcases.wrong-require :opt {:debug #{}})]
    (is (instance? java.io.FileNotFoundException (:exception result)))))

(defn- int-reader [input]
  (Integer/parseInt input))

(deftest custom-data-readers
  (testing "it should fail if there are no data readers registered"
    (is (thrown-with-msg? Exception #"No reader function for tag my-int"
                          (analyze/analyze-ns 'testcases.data-readers))))

  (testing "it should not fail when there is a data reader"
    ; the parser reader uses the same data-readers from clojure.core (which in
    ; practice are loaded by clojure.core/load-data-readers, but here in tests
    ; has a manual binding.
    (binding [clojure.core/*data-readers* {'my-int int-reader}]
      (let [result (analyze/analyze-ns 'testcases.data-readers)]
        (is (nil? (:exception result)))))))
