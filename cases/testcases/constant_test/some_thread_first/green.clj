(ns testcases.constant-test.some-thread-first.green)

(some-> (symbol "clojure.tools.namespace.repl" "refresh-dirs")
        resolve
        deref)
