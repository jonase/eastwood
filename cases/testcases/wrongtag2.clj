(ns testcases.wrongtag2
  ;;(:import (java.util LinkedList))
  (:require [clojure.test :refer :all]))

;;(defn avlf4a ^LinkedList [coll] (java.util.LinkedList. coll))
;;(defn avlf4b ^LinkedList [& coll] (java.util.LinkedList. coll))
(defn avlf4c ^LinkedList [& {:keys [coll]}] (java.util.LinkedList. coll))
;;(defn avlf4d ^LinkedList [& {:keys [^LinkedList coll]}] (java.util.LinkedList. coll))


;; Copied and modified from some core.matrix code

(defprotocol PTypeInfo
  (element-type [m]))


(extend-protocol PTypeInfo
  (Class/forName "[D")
    (element-type [m] :double))

(extend-protocol PTypeInfo
  (Class/forName "[F")
    (element-type [m] :float))

(element-type (float-array [1.0 2.0]))


(defprotocol PGetElem
  (get-elem [m idx]))

;; Reflection warning:
;; Reflection warning, filename.clj:3:23 - call to static method aget on clojure.lang.RT can't be resolved (argument types: unknown, int).
;; Reflection: yes
;; Eastwood wrong-tag warning: yes
(extend-protocol PGetElem
  (Class/forName "[D")
    (get-elem [m idx] (aget m idx)))

;; Same reflection warning as previous, even with ^doubles hint:
;; Reflection: yes
;; Eastwood wrong-tag warning: yes
(extend-protocol PGetElem
  (Class/forName "[D")
    (get-elem [^doubles m idx] (aget m idx)))

;; Why?  I think because the type tag addition in macro extend-type,
;; used by macro extend-protocol, replaces the ^doubles type tag
;; during macro expansion.  See the REPL session below for
;; confirmation.  Namespace alias u was created with this require in
;; Eastwood:

;; (require '[eastwood.util :as u])

;; user=> (def x1 (macroexpand '(extend-protocol PGetElem
;;   #_=>   (Class/forName "[D")
;;   #_=>     (get-elem [^doubles m ^Integer idx] (aget m idx)))))
;; 
;; user=> (def x2 (macroexpand (second x1)))
;; 
;; user=> (u/pprint-meta x2)
;; (clojure.core/extend
;;  ^{:line 2, :column 3} (Class/forName "[D")
;;  PGetElem
;;  {:get-elem
;;   (fn
;;    ([^{:tag ^{:line 2, :column 3} (Class/forName "[D")} m
;;      ^{:tag Integer} idx]
;;     ^{:line 3, :column 41} (aget m idx)))})
;; nil

;; Same reflection warning as previous, even with ^doubles and ^Integer hint:
;; Reflection: yes
;; Eastwood wrong-tag warning: yes
(extend-protocol PGetElem
  (Class/forName "[D")
    (get-elem [^doubles m ^Integer idx] (aget m idx)))

;; This is the extend-type macro invocation that the previous
;; extend-protocol expands to.
;; Reflection: yes
;; Eastwood wrong-tag warning: yes

(extend-type (Class/forName "[D")
  PGetElem
    (get-elem [^doubles m ^Integer idx] (aget m idx)))

;; This is the extend function call that the previous extend-type
;; macro invocation expands to.  Note that there is no mention of a
;; type tag doubles anywhere, only (Class/forName "[D").
;; Reflection: yes
;; Eastwood wrong-tag warning: yes

(extend (Class/forName "[D")
 PGetElem
 {:get-elem
  (fn ([^{:tag (Class/forName "[D")} m ^Integer idx]
    (aget m idx)))})

;; No reflection warning, because ^doubles hint is inside of aget
;; call, and not overwritten as it would be if it is on the argument
;; m.

;; Reflection: no
;; Eastwood wrong-tag warning: yes
(extend-protocol PGetElem
  (Class/forName "[D")
    (get-elem [m idx] (aget ^doubles m idx)))


;; Here is a way to do it with no reflection, but it can only be done
;; by using extend directly.  Neither extend-type nor extend-protocol
;; can be used for this in Clojure 1.6.0 (and probably also cannot in
;; any other Clojure version, at least up through 1.7.0-alpha4).

;; no reflection warning
;; Reflection: no
;; Eastwood wrong-tag warning: no
(extend (Class/forName "[D")
 PGetElem
 {:get-elem
  (fn ([^doubles m idx]
    (aget m idx)))})

;; reflection warning
;; Reflection: yes
;; Eastwood wrong-tag warning: no
(extend (Class/forName "[D")
 PGetElem
 {:get-elem
  (fn ([m idx]
    (aget m idx)))})

;; no reflection warning
;; Reflection: no
;; Eastwood wrong-tag warning: no
(extend (Class/forName "[D")
 PGetElem
 {:get-elem
  (fn ([m idx]
    (aget ^doubles m idx)))})
