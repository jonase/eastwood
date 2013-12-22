(ns eastwood.test.testcases.testtest
  (:use [clojure.test]))


(deftest testing-clojure-test

  (is (= 4 (+ 2 2)))  ; normal is

  (is (= 6 (+ 2 4)) "2+4 is 6")   ; normal is with message to report on failure

  (is "2+4 is 6 in the wrong place" (= 6 (+ 2 4)))  ; backwards args, but test passes because string is logical true

  (is "2+4 is not 7 in the wrong place" (= 7 (+ 2 4)))  ; backwards args, but test passes because string is logical true

  ;; (is #"regex" (/ 1 0))  ; test fails because of exception

  (is #"regex" (= 3 (+ 1 1)))  ; test passes because #"regex" is the expression to test, and it is always logical true

  ;;(is) ;; Too few args causes error?  Yes.  Good, no reason for lint
         ;; tool to check for this case.

  ;;(is 1 "msg" 3) ;; Too many args causes error?  Yes.  Good.


  ;; Note: (is (thrown-with-msg? Class regex body ...)) seems to have
  ;; enough checking of its argument types built in that putting the
  ;; args in the wrong order seems certain to cause the test to fail,
  ;; or to throw an exception while trying to compile it.  That is
  ;; good news.  There shouldn't be too many bad ones out there in the
  ;; wild that haven't been caught already, then.

  ;; (is (thrown-with-msg? "foo" Exception (/ 1 0)))  ;; throws exception that "foo" is not a class name.

  ;; (is (thrown-with-msg? "Exception" Exception (/ 1 0)))  ;; exception: "Exception" is not a class name.

  ;; (is (thrown-with-msg? (/ 1 0) Exception "Exception"))  ;; exception: (/ 1 0) is not a class name.

  ;; (is (thrown-with-msg? Exception (/ 1 0) "Exception"))  ;; Fails because evaluating expression "Exception" does not throw an exception.

  ;; (is (thrown-with-msg? Exception "bad" (/ 1 0)))  ;; exception: re-find cannot change "bad" to a regex

  ;; (is (thrown-with-msg? Exception #"bad" (/ 1 0)))  ;; Fails because exception message does not match regex #"bad"

  ;; (is (thrown-with-msg? Exception #"bad" (+ 1 0)))  ;; Fails because no exception thrown by evaluating (+ 1 0)

  (is (thrown-with-msg? Exception #"Divide by zero" (/ 1 0) (+ 1 0)))  ;; Passes
  (is (thrown-with-msg? Exception #"Divide by zero" (+ 1 0) (/ 1 0)))  ;; Passes


  ;; For (is (thrown? ...)), it is very easy to give it args expected
  ;; by (is (thrown-with-msg? ...)) and believe it will do matching of
  ;; the exception message contents, but it silently passes the test
  ;; regardless of the contents of that message.  Warn about these.

  ;; (is (thrown? "foo" Exception (/ 1 0)))  ;; throws exception that "foo" is not a class name.

  ;; (is (thrown? "Exception" Exception (/ 1 0)))  ;; exception: "Exception" is not a class name.

  ;; (is (thrown? (/ 1 0) Exception "Exception"))  ;; exception: (/ 1 0) is not a class name.

  (is (thrown? Exception (/ 1 0) "Exception"))  ;; Passes because (/ 1 0) is evaluated and throws an exception.  Final string "Exception" is compiled but not evaluated at run time due to the exception.  No big deal here that a linter would need to warn about that I can see.

  (is (thrown? Exception "bad" (/ 1 0)))  ;; Passes because (/ 1 0) throws an exception.  Note that the presence of a constant string could be a sign that someone meant thrown-with-msg? and for the "bad" to be a regex instead.  Probably should warn, indicating that the string's value is unused, and no checking of the exception message is done.  Perhaps they wanted thrown-with-msg? instead of thrown? and a regex instead of a string?

  (is (thrown? Exception #"bad" (/ 1 0)))  ;; Passes for the same reason the previous one does.  This is even more likely to have been intended to be thrown-with-msg? instead of thrown?  Definitely warn about this.

  ;;(is (thrown? Exception #"bad" (+ 1 0)))  ;; Fails because no exception thrown by evaluating (+ 1 0).  Same lint rule that catches previous case should also warn about this one.

  (is (thrown? Exception #"Divide by zero" (/ 1 0) (+ 1 0)))  ;; Passes.  Definitely should warn that probably intended thrown-with-msg? instead of thrown?  Regex is a dead giveaway.

  (is (thrown? Exception #"Divide by zero" (+ 1 0) (/ 1 0)))  ;; Passes.  Nearly same as previous case.

  (= 2 (+ 1 1))  ;; Should cause warning, since it has no effect on
                 ;; tests passing or failing.

  (testing "foo"
    (= #{} (disj #{:a} :a)))  ;; Should cause warning for same reason
                              ;; as previous one.

  )
