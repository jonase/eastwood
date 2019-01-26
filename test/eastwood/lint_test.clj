(ns eastwood.lint-test
  (:use [clojure.test ])
  (:require [eastwood.lint :refer :all]
            [eastwood.copieddeps.dep11.clojure.java.classpath :as classpath]
            [eastwood.util :as util]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.dir :as dir]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.track :as track]
            [eastwood.reporting-callbacks :as reporting])
  (:import java.io.File))


(deftest expand-ns-keywords-test
  (testing ""
    (is (= ["foo" "bar" "baz"] (expand-ns-keywords {:source-paths ["foo"]} [:source-paths "bar" "baz"])))))

(deftest last-options-map-adjustments-test
  (let [reporter (reporting/silent-reporter default-opts)]
    (testing "default-options are added"
      (is (= default-opts
             (dissoc (last-options-map-adjustments nil reporter)
                     :warning-enable-config))))
    (testing "passed options are respected"
      (is (= (assoc default-opts
                    :namespaces #{"foo"})
             (dissoc (last-options-map-adjustments {:namespaces ["foo"]} reporter)
                     :warning-enable-config))))
    (testing "all the things are sets"
      (is (empty? (->>
                   (select-keys (last-options-map-adjustments {:namespaces []} reporter) [:debug :namespaces :source-paths :test-paths :exclude-namespaces])
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
    (with-redefs [classpath/classpath-directories (fn [] [(File. "lol" )])]
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
