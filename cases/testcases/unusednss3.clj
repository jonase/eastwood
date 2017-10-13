(ns testcases.unusednss3
  (:require [clojure.core.protocols :as protocols]
            [clojure.core.reducers  :as reducers]
            [clojure.data           :as data]
            [clojure.java.io        :as io]
            [clojure.reflect        :as reflect]))

(extend String
  protocols/IKVReduce
  {:kv-reduce (fn [amap f init] nil)})

(extend-protocol reducers/CollFold
  String
  (coll-fold  [coll n combinef reducef] nil))

(extend-type String
  data/EqualityPartition
  (equality-partition [x] nil))

(deftype Foo [whatever]
  io/Coercions
  (as-file [x] nil)
  (as-url  [x] nil))

(deftype Bar [whatever]
  reflect/Reflector
  (do-reflect [reflector typeref] nil))
