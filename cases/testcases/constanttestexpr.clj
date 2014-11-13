(ns testcases.constanttestexpr)

;; Expressions that evaluate to a constant value as a test

;; not truthy:
;; false
;; nil

;; truthy:
;; any other expression that is a compile-time constant

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

(if-let [x [false]] "w" "v")  ; tbd: needs local binding resolve handling
(when-let [x #{5 7}] (println "Hello"))  ; tbd: needs to resolve local binding val
(when-first [x [1 2]] (println "Goodbye"))  ; tbd

(and (nil? nil) 7 (inc 2))  ; tbd: same as if-let above
(or false 2)                ; tbd: same as if-let above

(if-some [x {:a 1}] "w" "v")  ; tbd: needs local binding resolve + (nil? const) handling
(when-some [x "w"] nil)  ; tbd: same as if-some above


;; TBD? condp
;; TBD? while
