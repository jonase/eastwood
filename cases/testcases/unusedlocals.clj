(ns testcases.unusedlocals)



;; defrecords with an empty field list produce in their macroexpansion
;; several unused locals.  This code here helps test that Eastwood
;; suppresses those warnings correctly.

(defrecord zero-field-rec [])

(defrecord one-field-rec [a])



;; At least an early version of the unused-locals linter warned about
;; undesirable-warn1 being unused in fn foo2 below.

;; This occurs because of the way loop macroexpands when it has
;; destructuring binding.  The macroexpansion of the loop in foo2's
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
;; anywhere.  Why is it even bound to a value in the outer let?
;; Probably because it should be available for use in a later bound
;; symbol's initial value expression, if there is one.

;; Thus the warning is technically correct, but misleading when
;; looking at the pre-macroexpanded form.  It would be nice to find a
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
