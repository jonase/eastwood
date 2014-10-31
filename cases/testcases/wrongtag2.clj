(ns testcases.wrongtag2
  ;;(:import (java.util LinkedList))
  (:require [clojure.test :refer :all]))

;;(defn avlf4a ^LinkedList [coll] (java.util.LinkedList. coll))
;;(defn avlf4b ^LinkedList [& coll] (java.util.LinkedList. coll))
(defn avlf4c ^LinkedList [& {:keys [coll]}] (java.util.LinkedList. coll))
;;(defn avlf4d ^LinkedList [& {:keys [^LinkedList coll]}] (java.util.LinkedList. coll))
