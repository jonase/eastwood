(ns eastwood.test.outside-test-paths.example
  "A ns that is not in :source-paths, :test-paths, or the t.n refresh-dirs")

(defmacro faulty
  "A macro which expansion would cause an Eastwood warning"
  [x]
  `(-> ~x))
