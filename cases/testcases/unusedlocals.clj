(ns testcases.unusedlocals)



;; defrecords with an empty field list produce in their macroexpansion
;; several unused locals. This code here helps test that Eastwood
;; suppresses those warnings correctly.

(defrecord zero-field-rec [])

(defrecord one-field-rec [a])



;; At least an early version of the unused-locals linter warned about
;; undesirable-warn1 being unused in fn foo2 below.

;; This occurs because of the way loop macroexpands when it has
;; destructuring binding. The macroexpansion of the loop in foo2's
;; body is this:

;; user=> (pprint (macroexpand '(loop [[undesirable-warn1] (next x)] (when (next undesirable-warn1) (recur (next undesirable-warn1))))))
;; (let*
;;  [G__1178
;;   (next x)
;;   vec__1179
;;   G__1178
;;   undesirable-warn1
;;   (clojure.core/nth vec__1179 0 nil)]
;;  (loop*
;;   [G__1178 G__1178]
;;   (clojure.core/let
;;    [[undesirable-warn1] G__1178]
;;    (when (next undesirable-warn1) (recur (next undesirable-warn1))))))

;; Note that the inner let's undesirable-warn1 is what is used in the
;; loop body, while the outer let's undesirable-warn1 is not used
;; anywhere. Why is it even bound to a value in the outer let?
;; Probably because it should be available for use in a later bound
;; symbol's initial value expression, if there is one.

;; Thus the warning is technically correct, but misleading when
;; looking at the pre-macroexpanded form. It would be nice to find a
;; clean way to eliminate this warning without also suppressing any
;; good warnings.



(defn foo2 [x]
  (loop [[undesirable-warn1] (next x)]
    (when (next undesirable-warn1)
      (recur (next undesirable-warn1)))))

;; Similar to foo2, but no warning because z's initial value uses y,
;; and z is not part of a destructuring bind.
(defn foo3 [x]
  (loop [w x
         [y] (next x)
         z y]
    (when (next y)
      (recur (next w) (next y) (next z)))))


;; Similar to foo2, but should warn for some symbols because they are
;; not used in the loop body at all.

(defn foo4 [x]
  (loop [[unused-first-should-warn & rst] (seq x)]
    (when (seq rst)
      (recur (next rst)))))


(defn foo5 [x]
  (loop [[_unused-no-warn-because-of-_-at-beginning & rst] (seq x)]
    (when (seq rst)
      (recur (next rst)))))


(defn foo6 [x]
  (loop [unused-loop-symbol x, b (seq x)]
    (when (seq b)
      (recur b (next b)))))


;; Example with multiple unused locals so I can see whether some
;; changes I make will show the warnings in order the symbols appear,
;; rather than some other order.

;; Note: the macroexpansion of :keys destructuring forms reverses the
;; order of the keys given in the vector, e.g.

;;user=> (pprint (macroexpand '(let [{:keys [foo bar baz guh]} x] bar)))
;;(let*
;; [map__1177
;;  x
;;  map__1177
;;  (if
;;   (clojure.core/seq? map__1177)
;;   (clojure.lang.PersistentHashMap/create (clojure.core/seq map__1177))
;;   map__1177)
;;  guh
;;  (clojure.core/get map__1177 :guh)
;;  baz
;;  (clojure.core/get map__1177 :baz)
;;  bar
;;  (clojure.core/get map__1177 :bar)
;;  foo
;;  (clojure.core/get map__1177 :foo)]
;; bar)

(defn foo7 [x]
  (let [{:keys [foo bar baz guh]} x]
    (let [unused1 1, unused2 2, wee 3, unused3 4]
      [bar wee])))


;; unused-private-vars tests

;; I saw examples like these in tools.reader. They were used later in
;; the file, but tools.analyzer had :op :const ast nodes where they
;; were used, with a :form equal to 'upper-limit and 'lower-limit.
;; However, the asts where they were used seemed to have no reference
;; to the vars at all. I am not sure how much I can trust that there
;; are not corner cases if I tried to use the :form values of those
;; :const nodes to treat them as uses of these private const vars, and
;; not something else that might have the same symbol to refer to
;; them.

(def ^:private ^:const upper-limit (int \uD7ff))
(def ^:private ^:const lower-limit (int \uE000))

(defn foo8 [x]
  (and (> x upper-limit)
       (< x lower-limit)))

(def ^:private upper-limit2 (int \uD7ff))
(def ^:private lower-limit2 (int \uE000))

(def ^:private upper-limit3 (int \uD7ff))
(def ^:private lower-limit3 (int \uE000))

(defn foo9 [x]
  (and (> x upper-limit2)
       (< x lower-limit2)))

;; TBD: I found a case like foo10 and foo11 below in project automat,
;; where foo10 was warned about. Ideally we should not warn about
;; this, since it is used in foo11's macroexpansion, but I guess if
;; there is no actual expansion of foo11 in the namespace, how would
;; one know?

;; If foo11 were expanded, it would probably become obvious that there
;; was a use of foo10, but if not, it seems tricky.

;; Cases like this found:

;; automat var #'automat.core/input-range
;; compojure var #'compojure.core/if-context
;; hiccup var #'hiccup.def/update-arglists

;; Besides these undesired warnings, there are many more correct
;; warnings about unused private vars.

(defn- foo10 [x]
  (inc x))

(defmacro foo11 [y]
  `(#'testcases.unusedlocals/foo10 ~y))
