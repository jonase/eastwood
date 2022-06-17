(ns testcases.constant-test.spec-every.green
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :refer [are deftest is join-fixtures testing use-fixtures]]
   [clojure.spec.alpha :as spec]))

;; https://github.com/jonase/eastwood/issues/435
(spec/def ::some-spec (spec/map-of
                       (spec/spec string?
                                  :gen (fn [] (gen/fmap
                                               #(apply str %)
                                               (gen/vector
                                                gen/char-alpha
                                                5 20))))
                       (spec/coll-of
                        (spec/tuple ::source ::sub-type)
                        :into #{}
                        :min-count 2
                        :gen-max 7)
                       :min-count 2
                       :gen-max   8))
