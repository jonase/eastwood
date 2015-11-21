(ns testcases.constanttestexpr
  (:require [clojure.pprint :as pprint]))
;; Expressions that evaluate to a constant value as a test
(defmacro compile-if [test then] (if (eval test) then))
;; not truthy:
;; false
;; nil

;; truthy:
;; any other expression that is a compile-time constant
(def shrouded-false (not (seq {:a (/ 84 2)})))  ; false, but defined in a way that the :constant-test linter cannot determine that the test will always go the same way
;; Warn about these
(if false 1 2)
(if [nil] 1 2)
(if #{} 1 2)
(if {:a 1} 1 2)
(if '() 1 2)
(if '("string") 1 2)
(if [(inc 41)] 1 2)
(if #{(dec 43)} 1 2)
(if {:a (/ 84 2)} 1 2)
(if (seq {:a 1}) 1 2)   ; done: needed (pure-fn const) handling

(if-not 'x "y" "z")  ; done: needed (not logical-true-value) handling
(if-not false 1 2)
(if-not [nil] 1 2)
(if-not #{} 1 2)
(if-not {:a 1} 1 2)
(if-not '() 1 2)
(if-not '("string") 1 2)
(if-not [(inc 41)] 1 2)
(if-not #{(dec 43)} 1 2)
(if-not {:a (/ 84 2)} 1 2)
(if-not (seq {:a 1}) 1 2)

(when nil 'tom :cat)  ; same as if
(when-not [nil] 1)    ; same as if

;; No warning for this one, unless I make the linter fancier.  Why?
;; While the linter can tell seq is a pure fn, it is not able to
;; determine that the map has constant keys and values because of the
;; expression (/ 84 2).  Thus it treats the value of the map as a
;; variable, and does not know the behavior of seq.
(if (seq {:a (/ 84 2)}) 1 2)

;; No warning for :else in this one, or in general for any constant in
;; the 2nd-to-last arg of cond
(cond (>= (count [1 2 3]) 2) 8
      :else 9)
(cond :x 8
      :else 9)
;; I'd be surprised if someone wrote code like this, but may as well
;; not warn about it.
(cond :else 9)
;; Sometimes people use true or :default instead of :else.  Don't warn
;; about those, either.
(cond true 9)
(cond :default 9)

;; While it is true that doing (assert false "msg") is a constant
;; test, it is used often enough as a way to throw an exception with a
;; message in various Clojure projects that I don't think we should
;; warn about it.
(if false
  (assert false "This won't be reached, but shouldn't warn about it whether it can be reached or not."))

(if-let [x [false]] "w" "v")
(when-let [x (sorted-set 5 7)] (println "Hello"))
(when-first [x [1 2]] (println "Goodbye"))

(and (nil? nil) 7 (inc 2))
(or false 2)

(compile-if (resolve 'clojure.core/if-some) (if-some [x {:a 1}] "w" "v"))  ; tbd: needs (nil? const) handling as in-line
(compile-if (resolve 'clojure.core/when-some) (when-some [x "w"] nil))       ; tbd: same as if-some

(if shrouded-false
  (assert nil "string"))
(if shrouded-false
  (assert nil))
;; Both of the following should give a warning because they are
;; neither assert false nor assert nil, which are the only special
;; cases of constant assert expressions we should avoid warning about.
(assert true "string")
(assert [false])

;; Make sure *not* to get bindings from loop statements when looking
;; for let bindings.  There should be no warnings for the function
;; below.

(defn input-ranges [s]
  (loop [accumulator [], start nil, end nil, s (sort s)]
    (if (empty? s)
      (if end
        (conj accumulator [start end])
        accumulator)
      (let [x (first s)]
        (cond
          (and end (= (inc end) x))
          (recur accumulator start x (rest s))
          
          end
          (recur (conj accumulator [start end]) x x (rest s))
          
          :else
          (recur accumulator x x (rest s)))))))

;; A precondition similar to the one below should trigger a warning, I
;; think.

(defn wrongly-written-precondition [i]
  {:pre (<= 0 i 32)}
  (dec i))

;; A function like this in data.json gave :constant-test warnings
;; before.  I don't see yet why that might happen.  Answer:
;; pprint/formatter-out is a macro, not a function.  It exapnds to
;; include an expression of the form (if (string? "string-arg")
;; then-expr else-expr).  That is what causes the warning.

(defn- pprint-array [s]
  ((pprint/formatter-out "~<[~;~@{~w~^, ~:_~}~;]~:>") s))

;; The linter is able to detect this as a constant test expression,
;; since it is a pure function call on a pure function call on
;; constants.
(if (map? (list [:p "a"] [:p "b"])) 1 2)

;; It can detect this, too.
(let [x (map? (list [:p "a"] [:p "b"]))]
  (if x 1 2))

;; TBD: Why is it not able to issue a warning for a line like this in
;; test/hiccup/test/core.clj ?
;; (is (= (html (list [:p "a"] [:p "b"])) "<p>a</p><p>b</p>"))
;; It does detect this one right next to it
;; (is (= (html [:body (list "foo" "bar")]) "<body>foobar</body>"))

;; TBD? condp
;; TBD? while
