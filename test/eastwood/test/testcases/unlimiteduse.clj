

(ns eastwood.test.testcases.unlimiteduse
  (:refer-clojure :exclude [read-string])
  (:use clojure.test       ; warn
        [clojure.reflect]   ; warn
        [clojure inspector]    ; warn
        [clojure [set]]    ; warn
        [clojure.java.io :as io]   ; warn because no :only or :refer
        [clojure.java.browse :only [browse-url]]  ; no warning because :only
        [clojure.walk :as w :refer [postwalk-demo]]) ; no warning because :refer
  (:use (clojure [xml :only [emit]]   ; no warn because :only
                 [edn :only [read-string]])) ; ditto
  (:use (clojure [pprint :as pp]   ; warn because no :only or :refer
                 [uuid :as u]))  ; warn because no :only or :refer
  )
