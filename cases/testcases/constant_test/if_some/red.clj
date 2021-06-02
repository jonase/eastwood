(ns testcases.constant-test.if-some.red)

(if-some [v nil]
  v
  :whoops)
