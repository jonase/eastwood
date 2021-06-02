(ns testcases.constant-test.when-some.green)

(when-some [v (when (< (rand) 0.5)
                2)]
  v)
