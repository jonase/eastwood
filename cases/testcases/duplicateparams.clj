(ns testcases.duplicateparams)



;; Top level function definition with repeated params

(defn foo [a _ b a _]
  [a b])

;; user=> (foo 1 2 3 4 5)
;; [4 3]


;; fn with repeated params

(defn foo2 [x]
  (let [bar (fn foo2b [y _ z y _]
              [x y z])]
    bar))

;; user=> ((foo2 1) 2 3 4 5 6)
;; [1 5 4]


;; top level defn with sequential destructuring

(defn foo3 [[x y z y _a w _a]]
  [w x y z])

;; user=> (foo3 [1 2 3 4 5 6 7])
;; [6 1 4 3]


;; repeated parameter name is not inside of the destructuring

(defn foo3b [[x y z _a] y _a]
  [x y z])

;; I was expecting y to be bound to 5, but for some reason it gets 2.
;; Probably because the destructuring bindings happen sequentially in
;; time after the top level parameter bindings.

;; user=> (foo3b [1 2 3 4] 5 6)
;; [1 2 3]


;; top level defn with nested sequential destructuring

(defn foo4 [[[x y z] [y _a w _a]]]
  [w x y z])

;; user=> (foo4 [[1 2 3] [4 5 6 7]])
;; [6 1 4 3]


;; sequential destructuring with :as <name>, where <name> is a repeat
;; of an earlier parameter

(defn foo5 [[x y :as x]]
  [x y])

;; user=> (foo5 [1 2])
;; [[1 2] 2]

(defn foo5b [[x y :as z] [w :as z]]
  [w x y z])

;; user=> (foo5b [1 2 3] [4 5])
;; [4 1 2 [4 5]]


;; sequential :as in anonymous fn

(defn foo6 [x]
  (let [bar (fn [[y z :as z]]
              [x y z])]
    bar))


;; associative destructuring with all of :syms :keys :strs included in
;; the same map, some with common names to be bound to (b, c, d), some
;; unique (a, e, f).

(defn foo7 [{:syms [a b c] :keys [b d e] :strs [c d f]}]
  [a b c d e f])

;; user=> (foo7 {"c" "c-string" "d" "d-string" "f" "f-string" :b :b-kw :d :d-kw :e :e-kw 'a 'a-sym 'b 'b-sym 'e 'e-sym})
;; [a-sym :b-kw "c-string" "d-string" :e-kw "f-string"]


;; Example of repeated symbol 'a' causing confusing behavior from
;; @thheller on clojure-dev Slack channel during July 2017, recorded
;; on Eastwood issue #225.

(defn foo9 [{:keys [a]} a]
  [a])

;; user=> (foo9 {:a 2} 1)
;; [2]


;; Same as foo9, but with different variations of optional parameters
;; to defn macro.

(defn foo9b
  "doc string"
  [{:keys [a]} a]
  [a])


(defn foo9c
  "doc string"
  [{:keys [a]} a]
  {:pre [(pos? a)]}  ;; pre/postconditions
  [a])


(defn foo9d
  "doc string"
  {:a 1 :b 2}  ;; metadata map for var foo9d
  [{:keys [a]} a]   ;; arg vector
  {:pre [(pos? a)]}  ;; pre/postconditions
  [a])   ;; body


(defn foo9e
  "doc string"
  {:a 1 :b 2}  ;; metadata map for var foo9d
  ([{:keys [a]} a]   ;; arg vector
   {:pre [(pos? a)]}  ;; pre/postconditions
   [a])   ;; body #1
  ([b c d]
   [b c d]))


;; vector after :keys in associative destructuring can contain:

;; Vector    local    to val
;; element   symbol   associated
;; example   bound    with this key   Notes
;; --------  -------- -------------   --------------------

;;    x        x        :x            symbol without namespace

;;  c.d/e      e        :c.d/e        symbol with namespace

;;   :x        x        :x            keyword without namespace

;;  :i.j/k     k        :i.j/k        keyword with namespace

;; In all cases above, the Clojure expression (symbol nil (name elem))
;; could take the value in the column 'Vector element example', and
;; return the corresponding value in the column 'local symbol bound'.

;; There should be no warnings for this function. It simply
;; demonstates most of the kinds of things that can legally be inside
;; the vector after a :keys key in associative destructuring. (It
;; leaves out symbols that have no namespace, but there are plenty of
;; examples of that elsewhere in this file.)

(defn foo10 [{:keys [a/b c.d/e :f :g/h :i.j/k] :as l}]
  [b e f h k l])

;; Results from calling foo10 in REPL with Clojure 1.9.0:

;; user=> (foo10 {})
;; [nil nil nil nil nil {}]
;; user=> (foo10 {:b 2})
;; [nil nil nil nil nil {:b 2}]
;; user=> (foo10 {:a/b 2})
;; [2 nil nil nil nil #:a{:b 2}]
;; user=> (foo10 {:f 6})
;; [nil nil 6 nil nil {:f 6}]
;; user=> (foo10 {:e/f 6})
;; [nil nil nil nil nil #:e{:f 6}]
;; user=> (foo10 {:c.d/ef 6})
;; [nil nil nil nil nil #:c.d{:ef 6}]
;; user=> (foo10 {:c.d/e 6})
;; [nil 6 nil nil nil #:c.d{:e 6}]
;; user=> (foo10 {:c.d/e 5})
;; [nil 5 nil nil nil #:c.d{:e 5}]
;; user=> (foo10 {:g/h 5})
;; [nil nil nil 5 nil #:g{:h 5}]
;; user=> (foo10 {:g/h 8})
;; [nil nil nil 8 nil #:g{:h 8}]
;; user=> (foo10 {'g/h 8})
;; [nil nil nil nil nil #:g{h 8}]
;; user=> (foo10 {:i.j/k 11})
;; [nil nil nil nil 11 #:i.j{:k 11}]


;; Several duplicates here that should be warned about.

(defn foo11 [{:keys [nsa/b c.d/a :f :g/a :i.j/b] :as f}]
  [a b f])

;; user=> (foo11 {})
;; [nil nil nil]
;; user=> (foo11 {:nsa/b 1 :c.d/a 2 :f 3 :g/a 4 :i.j/b 5})
;; [4 5 3]


;; No duplicate local names in foo12, but it does have local name 'f'
;; as a key in the :or map, with no local name 'f'. If you change the
;; body of this function to refer to f, you get a compiler
;; error "Unable to resolve symbol: f in this context".

;; Eastwood's :unused-or-default warning can warn about a local name as
;; a key in an :or map that does not appear elsewhere. I believe that
;; such a local name that is a key in an :or map only applies to local
;; names bound in the same 'level' of map destructuring where the :or
;; appears. That is, if there are nested sub-maps or sub-vectors
;; destructuring within that map with the :or, that :or does not 'see'
;; or apply to them. It doesn't apply to siblings or parents in the
;; 'tree' of destructuring things, either.

(defn foo12 [{:keys [a.b/c d/e] :or {c 5 e 7 f 10} :as g}]
  [c e g])


(defn foo13 [{{{h :h} :a       ;; h here
               :keys [d e f]
               :or {d 4 e 5}} :bar
              ;; The h on the next line is at higher level in tree
              ;; than the one above, and thus has no affect on the
              ;; value bound to the local name h in the body of foo13.
              ;; Similarly for the f on the next line. Both keys in
              ;; the :or below are thus good to warn about in
              ;; Eastwood, as likely mistakes, since they do not
              ;; affect the compiled code at all.
              :or {f 6 h 8}
              :as g}]
  [d e f g h])

;; You can see the fully macroexpanded version of any Clojure
;; expression using code like that shown below:

;; (use 'clojure.pprint)
;; (require '[clojure.walk :as w])
;; (pprint (w/macroexpand-all '(defn foo13 [{{{h :h} :a :keys [d e f] :or {d 4 e 5}} :bar :or {f 6 h 8} :as g}] [d e f g h])))


;; macroexpanding this function demonstarates that the 'c' in the :or
;; map below does _not_ have any effect on the value of c bound by the
;; arg vector below via the ':as c' portion. This makes sense, since
;; c should be bound to the entire associative value passed as the
;; first parameter, regardless of its contents, and a default value
;; doesn't make sense. This is an example where a warning that the
;; 'c' in the :or {c 5} part of the arg vector should ideally trigger
;; a warning.

(defn foo14 [{:keys [a b] :as c :or {c 5}}]
  [a b c])


(defn wrong-pre-different-macroexpansion-13
  [datastore & [{:keys [tqname freezer redis-ttl-ms]
                 :or   {tqname :default}}]]
  {:pre [(instance? Number datastore)]}
  (assoc freezer :foo 7))


(defn foo15
  [base & {threshold :threshold :or {threshold 32}}]
  [base threshold])


;; TBD: Implement ability in Eastwood to look for these kinds of
;; warnings for destructuring expressions within let, loop, and other
;; macros that support destructuring.

;; Things in Clojure core that can use destructure:

;; let - Direct call to destructure.
;; loop - Direct call to destructure.
;; fn - Macro that calls fn maybe-destructured, which can return
;;    expression that includes let.
;; defn - macro expansion to fn.
;; defmacro - macro expansion to defn.
;; for - macro expansion can include let and/or loop
;; doseq - macro expansion can include let and/or loop
;; if-let - macro expansion includes let
;; when-let - macro expansion includes let
;; if-some - macro expansion includes let
;; when-some - macro expansion includes let

;; TBD: It would be nice if there was a way to detect all such uses,
;; even in user-defined macros that use clojure.core/destructure, but
;; that is a function, not a macro, so not clear how to do that in
;; Eastwood.

;; TBD: What happens with macros that have repeated parameter names?
;; Can Eastwood easily detect them?

;; TBD: Can macros use destructuring with their parameters?  How do
;; they look in ASTs?
