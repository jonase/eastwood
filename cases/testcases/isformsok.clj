(ns testcases.isformsok
  (:require [clojure.test :as t]))

(defmacro is [& args]
  `(println ~@args))

(t/deftest testing-clojure-test
  ;; This should not warn, because it is testcases.isformsok/is, not
  ;; clojure.test/is
  (is "2+4 is 6 in the wrong place" (= 6 (+ 2 4)))  ; backwards args, but test passes because string is logical true
  )

(comment
  ;; Best not to warn for things inside of (comment ...)
  (is "2+4 is 6 in the wrong place" (= (count [:flood :flood :floob :flood :gates :agtes]) (+ 2 4)))  ; backwards args, but test passes because string is logical true
  )
