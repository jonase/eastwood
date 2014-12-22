(comment

;; expressions in comments can be used to test this function from a
;; REPL

(require '[eastwood.lint :as l])

)

;; See the function lint in the namespace eastwood.lint
;; It is a slightly enhanced version of what was here.

(comment

(use 'clojure.pprint)

;; Replace :source-paths and :test-paths values with whatever is in a
;; Leiningen project's defproject, if it has them.

(pprint (l/lint {:source-paths ["src"] :test-paths ["test"]}))

)
