(ns testcases.core-async-example
  (:require
   [clojure.core.async :refer [go]]))

(defprotocol P
  (x [p]))

;; taken from the core.async test suite
(defrecord R [z]
  P
  (x [this]
    (go
      (loop []
        (if (zero? (rand-int 3))
          [z (.z this)]
          (recur))))))
