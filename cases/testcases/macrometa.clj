(ns testcases.macrometa
  (:require [clojure.java.io :as io])
  (:import (java.io Writer StringWriter)
           (java.awt Point)))

(set! *warn-on-reflection* true)

;; Any of these with a name ending in -lintwarn should cause an
;; Eastwood :unused-meta-on-macro warning due to metadata being lost.
;; They are not intended to be useful bits of code -- just things that
;; the Clojure compiler will accept without error. Some will cause
;; reflection warnings if *warn-on-reflection* is true, but that is
;; exactly why someone might try to add type hints, which if not done
;; correctly will be ignored.

;; Java interop - constructor calls (ClassName. args)
;; All metadata removed during macroexpansion to form (new ClassName
;; args), even :tag

(def ex01-lintwarn (.close ^{:foo 7} (StringWriter.)))
(def ex02-lintwarn (.close ^Writer (StringWriter.)))
(def ex03-nolintwarn (.close (StringWriter.)))
(def ex04-nolintwarn (let [^Writer w (StringWriter.)] (.close w)))

(def ex01b-nolintwarn (.close ^{:foo 7} (new StringWriter)))
(def ex02b-nolintwarn (.close ^Writer (new StringWriter)))
(def ex03b-nolintwarn (.close (new StringWriter)))
(def ex04b-nolintwarn (let [^Writer w (new StringWriter)] (.close w)))

;; Java interop - instance method calls (.instanceMethod obj args)
;; Only :tag metadata preserved during macroexpansion to form (. obj
;; (instanceMethod args)). Any other metadata removed.

(def ex05-nolintwarn (.close (StringWriter.)))
(def ex06-lintwarn ^{:foo 7} (.close (StringWriter.)))
(def ex07-nolintwarn ^Writer (.close (StringWriter.)))
(def ex08-lintwarn ^{:foo 7 :tag Writer} (.close (StringWriter.)))

;; Java interop - class method calls (ClassName/staticMethod args)
;; Only :tag metadata preserved during macroexpansion to form
;; (. ClassName (staticMethod args)). Any other metadata removed.

(def ex09-nolintwarn (Math/abs 5))
(def ex10-lintwarn ^{:foo 7} (Math/abs 5))
(def ex11-nolintwarn ^Writer (Math/abs 5))
(def ex12-lintwarn ^{:foo 7 :tag Writer} (Math/abs 5))

;; Java interop - those beginning with ".". All metadata preserved
;; since no macroexpansion required.

(def ex13-nolintwarn (. Math abs 5))
(def ex14-nolintwarn ^{:foo 7} (. Math abs 5))
(def ex15-nolintwarn ^Writer (. Math abs 5))
(def ex16-nolintwarn ^{:foo 7 :tag Writer} (. Math abs 5))

;; Function calls - All metadata preserved since no macroexpansion
;; required.

(defn my-writer [& args]
  (apply io/writer args))

(def ex17-nolintwarn (my-writer (StringWriter.)))
(def ex18-nolintwarn ^{:foo 7} (my-writer (StringWriter.)))
(def ex19-nolintwarn ^Writer (my-writer (StringWriter.)))
(def ex20-nolintwarn ^{:foo 7 :tag Writer} (my-writer (StringWriter.)))

;; Invocations of macros defined with defmacro -- all metadata removed
;; during macroexpansion, unless the macro is specifically written to
;; preserve it. I am not certain, but I believe doing so would
;; require using the &form or &env hidden args to macros.

(defmacro my-writer-macro [x]
  `(my-writer ~x))

(def ex21-nolintwarn (my-writer-macro (StringWriter.)))
(def ex22-lintwarn ^{:foo 7} (my-writer-macro (StringWriter.)))
(def ex23-lintwarn ^Writer (my-writer-macro (StringWriter.)))
(def ex24-lintwarn ^{:foo 7 :tag Writer} (my-writer-macro (StringWriter.)))
(def ex25-nolintwarn (let [^Writer w (my-writer-macro (StringWriter.))] 
                       (.close w)))

(def ex26-nolintwarn (fn [x] (inc x)))
(def ex27-nolintwarn ^{:foo 7} (fn [x] (inc x)))
(def ex28-nolintwarn ^Writer (fn [x] (inc x)))
(def ex29-nolintwarn ^{:foo 7 :tag Writer} (fn [x] (inc x)))

;; Java interop - class static field accesses (ClassName/staticField)
;; Only :tag metadata preserved during macroexpansion to form
;; (. ClassName staticField). Any other metadata removed.

(def ex30-nolintwarn (Long/MAX_VALUE))
(def ex31-lintwarn ^{:foo 7} (Long/MAX_VALUE))
(def ex32-nolintwarn ^Writer (Long/MAX_VALUE))
(def ex33-lintwarn ^{:foo 7 :tag Writer} (Long/MAX_VALUE))

;; Java interop - instance field accesses (.instanceField instance)
;; Only :tag metadata preserved during macroexpansion to form
;; (. instance instanceField). Any other metadata removed.

(def pt1 (Point. 1 2))
(def ex34-nolintwarn (.x pt1))
(def ex35-lintwarn ^{:foo 7} (.x pt1))
(def ex36-nolintwarn ^Writer (.x pt1))
(def ex37-lintwarn ^{:foo 7 :tag Writer} (.x pt1))




;; Intended to be a summary of behavior where Clojure macroexpansion
;; can cause metadata to be lost.

;; user=> *clojure-version*
;; {:major 1, :minor 6, :incremental 0, :qualifier nil}
;; user=> (import '[java.io Writer FileWriter])
;; java.io.FileWriter
;; user=> (require '[clojure.java.io :as io])
;; nil

;; user=> (defn m [form] ((juxt meta identity) (macroexpand form)))
;; #'user/m

;; ;; All metadata eliminated from forms (ClassName. args)
;; user=> (m '(FileWriter. "a.txt"))
;; [nil (new FileWriter "a.txt")]
;; user=> (m '^{:foo 7 :tag Writer} (FileWriter. "a.txt"))
;; [nil (new FileWriter "a.txt")]

;; ;; All metadata except :tag eliminated from forms (.instanceMethod args)
;; user=> (m '(.close (FileWriter. "a.txt")))
;; [nil (. (FileWriter. "a.txt") close)]
;; user=> (m '^{:foo 7 :tag Writer} (.close (FileWriter. "a.txt")))
;; [{:tag Writer} (. (FileWriter. "a.txt") close)]

;; ;; All metadata except :tag eliminated from forms (Class/staticMethod args)
;; user=> (m '(Math/abs 5))
;; [nil (. Math abs 5)]
;; user=> (m '^{:foo 7 :tag Long} (Math/abs 5))
;; [{:tag Long} (. Math abs 5)]

;; ;; All metadata except :tag eliminated from forms (Class/staticField)
;; user=> (m '(Long/MAX_VALUE))
;; [nil (. Long MAX_VALUE)]
;; user=> (m '^{:foo 7 :tag Long} (Long/MAX_VALUE))
;; [{:tag Long} (. Long MAX_VALUE)]

;; ;; All metadata preserved for forms (. ClassName instanceMethod args),
;; ;; because no macroexpansion is occurring.
;; user=> (m '(. Math abs 5))
;; [{:line 1, :column 5} (. Math abs 5)]
;; user=> (m '^{:foo 7 :tag Long} (. Math abs 5))
;; [{:tag Long, :foo 7, :line 1, :column 5} (. Math abs 5)]

;; ;; All metadata preserved for forms (new ClassName), because no
;; ;; macroexpansion is occurring.
;; user=> (m '(new StringWriter))
;; [{:line 1, :column 5} (new StringWriter)]
;; user=> (m '^{:foo 7} (new StringWriter))
;; [{:foo 7, :line 1, :column 5} (new StringWriter)]
;; user=> (m '^Writer (new StringWriter))
;; [{:tag Writer, :line 1, :column 5} (new StringWriter)]
;; user=> (m '^{:foo 7 :tag Writer} (new StringWriter))
;; [{:tag Writer, :foo 7, :line 1, :column 5} (new StringWriter)]

;; ;; All metadata preserved for function calls. No macroexpansion is
;; ;; occurring.
;; user=> (defn my-writer [& args] (apply io/writer args))
;; #'user/my-writer
;; user=> (m '(my-writer "a.txt"))
;; [{:line 1, :column 5} (my-writer "a.txt")]
;; user=> (m '^Writer (my-writer "a.txt"))
;; [{:tag Writer, :line 1, :column 5} (my-writer "a.txt")]
;; user=> (m '^{:foo 7 :tag Writer} (my-writer "a.txt"))
;; [{:tag Writer, :foo 7, :line 1, :column 5} (my-writer "a.txt")]

;; ;; All metadata _including_ :tag eliminated for invocations of macros
;; ;; defined with defmacro.
;; user=> (defmacro my-writer-macro [x] `(my-writer ~x))
;; #'user/my-writer-macro
;; user=> (m '(my-writer-macro "a.txt"))
;; [nil (user/my-writer "a.txt")]
;; user=> (m '^Writer (my-writer-macro "a.txt"))
;; [nil (user/my-writer "a.txt")]
;; user=> (m '^{:foo 7 :tag Writer} (my-writer-macro "a.txt"))
;; [nil (user/my-writer "a.txt")]


;; ;; A let binding with a type hint is one way to avoid the reflection,
;; ;; although a little more verbose.
;; 
;; user=> (set! *warn-on-reflection* true)
;; true
;; user=> (.close (my-writer-macro "a.txt"))
;; Reflection warning, /private/var/folders/v5/7hpqbpk51td3v351377gl6yw0000gn/T/form-init3862515282802286345.clj:1:1 - reference to field close can't be resolved.
;; nil
;; user=> (.close ^Writer (my-writer-macro "a.txt"))
;; Reflection warning, /private/var/folders/v5/7hpqbpk51td3v351377gl6yw0000gn/T/form-init3862515282802286345.clj:1:1 - reference to field close can't be resolved.
;; nil
;; user=> (let [^Writer w (my-writer-macro "a.txt")]
;;   #_=>   (.close w))
;; nil

;; clojure.core/fn is one macro specially written specifically to
;; preserve metadata, by getting metadata on its &form argument.

;; user=> (m '(fn [x] (inc x)))
;; [{:line 1, :column 5} (fn* ([x] (inc x)))]
;; user=> (m '^{:foo 7 :tag Integer} (fn [x] (inc x)))
;; [{:tag Integer, :foo 7, :line 1, :column 5} (fn* ([x] (inc x)))]
