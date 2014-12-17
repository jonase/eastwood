;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Just for Eastwood testing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :wrong-arity
  :function-symbol 'testcases.f01/fn-with-arglists-meta
  :arglists-for-linting
  '([x y z])
  :reason "Only for Eastwood testing"})
