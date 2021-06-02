(ns testcases.constant-test.if-some.green)

(if-some [v (when (< (rand) 0.5)
              2)]
  v
  :whoops)
