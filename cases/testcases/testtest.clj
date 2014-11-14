(ns testcases.testtest
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

  (contains? #{:foo :bar} :foo)

  (is (= (+ 1 1)) (+ 0 2))  ; always passes because (= (+ 1 1)) is always true
  (is (> (+ 1 1)) (+ 0 1))  ; always passes because (> (+ 1 1)) is always true
  (is (= (min-key first [2 3]) [2 3]))

  (is (=  true (= 2)))
  (is (=  true (== 2)))
  (is (= false (not= 2)))
  (is (=  true (< 2)))
  (is (=  true (<= 2)))
  (is (=  true (> 2)))
  (is (=  true (>= 2)))
  (is (=     2 (min 2)))
  (is (=     2 (max 2)))
  (is (= [2 3] (min-key first [2 3])))
  (is (= [2 3] (max-key first [2 3])))
  (is (=     0 (+)))
  (is (=     2 (+ 2)))
  (is (=     0 (+')))
  (is (=     2 (+' 2)))
  (is (=     1 (*)))
  (is (=     2 (* 2)))
  (is (=     1 (*')))
  (is (=     2 (*' 2)))
  (is (=    {} (dissoc {})))
  (is (=   #{} (disj #{})))
  (is (=   nil (merge)))
  (is (=    {} (merge {})))
  (is (=   nil (merge-with +)))
  (is (=    {} (merge-with + {})))
  (when (and (>= (:major *clojure-version*) 1)
             (>= (:minor *clojure-version*) 6))
    (is (=    () (interleave))))  ; new arity introduced in Clojure 1.6
  (is (=    "" (pr-str)))
  (is (=    "" (print-str)))
  (is (=    "" (with-out-str)))
  (is (=   nil (pr)))
  (is (=   nil (print)))
  (is (= identity (comp)))
  (is (=     + (partial +)))
  (is (=    () (lazy-cat)))
  (is (=     2 (-> 2)))
  (is (=   nil (cond)))
  (is (=     2 (case 3 2)))
  (is (=     2 (condp = 3 2)))
  (is (=   nil (when 2)))
  (is (=   nil (when-not 2)))
  (is (=   nil (when-let [x 2])))
  (is (=   nil (doseq [x [1 2 3]])))
  (is (=   nil (dotimes [i 10])))
  (is (=  true (and)))
  (is (=     2 (and 2)))
  (is (=   nil (or)))
  (is (=     2 (or 2)))
  (is (=     2 (doto 2)))
  (is (=   nil (declare)))

  ;; suspicious-test linter should specially recognize that the 2nd
  ;; arg to 'is' are strings, because they are forms that begin with
  ;; str, format, or a few other similar symbols
  (is (= 6 (+ 2 4)) (str "2+4" "is" "6"))
  (is (= 6 (+ 2 4)) (format "%s is %s" 2 6))
  (is (= 6 (+ 2 4)) (print-str 5))

  (is (= "foo") (str "fo" "o"))
  (is (== 6) (+ 2 4))
  )

(deftest when-in-quoted-expr
  ;; Should not give :suspicious-expression warnings for expressions
  ;; inside of (quote ...)
  (is (= 'when (first (fnext '(first (when (foo bar))))))))

(deftest macros-inside-is
  (is (-> 1 (= 1))) ; should be no warning for this
  (is (-> 1 (=)))   ; do warn for this, but should be for 1-arg = call, not 0-arg
  )

(comment
  ;; Best not to warn for things inside of (comment ...)
  (= 5)
  (is "2+4 is 6 in the wrong place" (= (count [:flood :flood :floob :flood :gates :agtes]) (+ 2 4)))  ; backwards args, but test passes because string is logical true
  )
