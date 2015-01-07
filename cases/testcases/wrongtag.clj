(ns testcases.wrongtag
  (:import (java.util LinkedList) (java.util Arrays))
  (:require [clojure.test :refer :all]))

;; These two cases should cause a warning, because Clojure 1.6.0
;; treats the tag as if it is the function clojure.core/long.
(def ^long lv1 -2)
(def ^{:tag long} lv2 -2)

;; This is a correct way to tag it.  Should have no warning
(def ^{:tag 'long} lv3 -2)

;; These kinds of tags do not have to be restricted to long or double
;; the way that primitive arguments and return types of functions do.
;; They are only used for avoiding reflection in Java interop calls,
;; not to control the type of object that is used to store the value.

;; Warnings for these, similar to above cases with warnings
(def ^int iv1 -2)
(def ^{:tag int} iv2 -2)
;; No warnings
(def ^{:tag 'int} iv3 -2)
(def ^{:tag 'bytes} bvv1 (byte-array (map byte [1 2 3])))  ; map byte needed to avoid bug in Clojure 1.5.1

;; Similarly for tags on Vars whose values are functions.  Again, such
;; tags do not cause the return type of the function created to be a
;; primitive.  They are simply used to avoid reflection wherever the
;; return value of the function is used in a Java interop call.

;; Warnings for these
(def ^long lf1 (fn [x y] (+ x y)))
(defn ^long lf2 [x y] (+ x y))
(def ^{:tag long} lf3 (fn [x y] (+ x y)))
(defn ^{:tag long} lf4 [x y] (+ x y))
(def ^int if1 (fn [x y] (+ x y)))
(defn ^int if2 [x y] (+ x y))
(def ^{:tag int} if3 (fn [x y] (+ x y)))
(defn ^{:tag int} if4 [x y] (+ x y))

;; No warnings for these
(def ^{:tag 'long} lf5 (fn [x y] (+ x y)))
(defn ^{:tag 'long} lf6 [x y] (+ x y))
(def ^{:tag 'int} if5 (fn [x y] (+ x y)))
(defn ^{:tag 'int} if6 [x y] (+ x y))


;; When the return type tag is on the arg vector, if it is a primitive
;; it must be long or double or the Clojure compiler will give an
;; error.

;; No warnings
(def plf1 (fn ^long [x y] (+ x y)))
(defn plf2 ^long [x y] (+ x y))
(def pdf1 (fn ^double [x y] (+ x y)))
(defn pdf2 ^double [x y] (+ x y))

;; When the return type tag is on the arg vector and it is a Java
;; class, Clojure can handle any class name just fine when defining
;; the function, and wherever calling it, except in one case described
;; in CLJ-1232:

;; If the class is not imported by default into Clojure (it imports
;; those in java.lang by default), but it is imported where the
;; function is defined, and the tag is on the arg vector, and it is
;; not fully qualified, and the function is called from a different
;; namespace where the class is not imported, then Clojure 1.6.0
;; compiler will throw an exception where the call is, indicating that
;; the class is unknown.

;; Thus Eastwood will warn about function definitions like this, in an
;; effort to help the developer more quickly discover the solution.
;; The thing that works for now in all cases is to fully qualify the
;; class name where the function is defined.  This does not require
;; the caller of the function to import the class.

;; Eastwood avoids issuing a warning if the function is also declared
;; private, since there are cases of this in existing libraries, and
;; it is very unlikely that the error will be hit for such a function
;; (or perhaps even impossible).

;; Warn for these cases

;; wrong way to specify the type
(def avlf1 (fn ^{:tag 'LinkedList} [coll] (java.util.LinkedList. coll)))
(defn avlf2 ^{:tag 'LinkedList} [coll] (java.util.LinkedList. coll))
;; right way to specify the type, but not fully qualified, and public
(def avlf3 (fn ^LinkedList [coll] (java.util.LinkedList. coll)))
(defn avlf4 ^LinkedList [coll] (java.util.LinkedList. coll))
;; The following will not warn with Clojure <= 1.7.0-alpha1, but will
;; with Clojure >= 1.7.0-alpha2, because of the fix for ticket
;; CLJ-887.  Before that fix, metadata was lost on argument vectors
;; that use destructuring.
(defn avlf4b ^LinkedList [& {:keys [coll]}] (java.util.LinkedList. coll))

;; No warnings for these cases

;; fully qualified type, public
(def avlf5 (fn ^java.util.LinkedList [coll] (java.util.LinkedList. coll)))
(defn avlf6 ^java.util.LinkedList [coll] (java.util.LinkedList. coll))
(defn ^{:private false} avlf7 ^java.util.LinkedList [coll] (java.util.LinkedList. coll))
;; not fully qualified type, but private
(def ^:private avlf9 (fn ^LinkedList [coll] (java.util.LinkedList. coll)))
(defn ^:private avlf10 ^LinkedList [coll] (java.util.LinkedList. coll))
(def ^{:private true} avlf11 (fn ^LinkedList [coll] (java.util.LinkedList. coll)))
(defn ^{:private true} avlf12 ^LinkedList [coll] (java.util.LinkedList. coll))
(defn- avlf13 ^LinkedList [coll] (java.util.LinkedList. coll))

;; No warnings for these cases, either, strangely enough.  Clojure
;; never seems to throw an exception for non-fully qualified Java
;; class names as type tags on Vars naming functions, only in the
;; cases mentioned above when such a class name is used as a tag on
;; the argument vector.

(def ^LinkedList avlf15 (fn [coll] (java.util.LinkedList. coll)))
(defn ^LinkedList avlf16 [coll] (java.util.LinkedList. coll))

;; This is probably a reduced test case from some project I was
;; examining a problem in a while back, but I don't recall now which.
;; The type tag ^Set on b should give a :wrong-tag warning, unless
;; java.util.Set is imported in the namespace, or it is fully
;; qualified where it is used.

(defn foo [x]
  (let [a 5
        ^Set b (set [1 2 3])
        c (count b)]
    (+ x c)))

;; Copied and adapted from Seesaw namespace seesaw.widgets.log-window

;; Formerly caused tools.analyzer to throw an exception, now Eastwood
;; handles the wrong-tag callback to avoid the exception.  Filed
;; ticket TANAL-31 for this issue.

(defn- log-window-proxy [state]
  (proxy [javax.swing.JTextArea clojure.lang.IDeref] []
    ; Implement IDeref
    (deref [] state)

    ; this is how you disable auto-scrolling :(
    (scrollRectToVisible [rect]
                         (if @(:auto-scroll? state)
                           (proxy-super scrollRectToVisible rect)))))

(defprotocol LogWindow
  (log   [this message])
  (clear [this]))

(extend-type (class (log-window-proxy nil))
  LogWindow
  (log [this message]
    (println "message" message))
  (clear [this]
    nil))

;; This gives a compiler error "Only long and double primitives are supported"
;;(defn compiler-error-1 [^int x]
;;  (Math/abs x))

;; The functions below do not give compiler errors, *and* the type
;; hints on their arg vectors do help avoid reflection.  Eastwood
;; 0.2.1 gives incorrect warnings about ^objects type tags on arg
;; vectors.

(defn copy-object-array
  (^objects [^objects arr]
    (Arrays/copyOf arr (int (alength arr)))))

(defn copy-object-array-2
  ([^objects arr]
    (Arrays/copyOf arr (int (alength arr)))))

(defn copy-int-array
  (^ints [^ints arr]
    (Arrays/copyOf arr (int (alength arr)))))

(defn copy-int-array-2
  ([^ints arr]
    (Arrays/copyOf arr (int (alength arr)))))

;; no reflection warning
(aget (copy-object-array (object-array [1 2 3])) 1)

;; This gives reflection warning
(aget (copy-object-array-2 (object-array [1 2 3])) 1)

;; no reflection warning
(aget (copy-int-array (int-array [1 2 3])) 1)

;; This gives reflection warning
(aget (copy-int-array-2 (int-array [1 2 3])) 1)


;; Copied and modified from some core.matrix code, which caused
;; exceptions in the :wrong-tag linter of Eastwood 0.2.1.

(defn copy-double-array
  "Returns a copy of a double array"
  (^doubles [^doubles arr]
    (Arrays/copyOf arr (int (alength arr)))))


(defprotocol PDoubleArrayOutput
  (to-double-array [m])
  (as-double-array [m]))


(extend-protocol PDoubleArrayOutput
  (Class/forName "[D")
    (to-double-array [m] (copy-double-array m))
    (as-double-array [m] m))
