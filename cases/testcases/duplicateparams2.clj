(ns testcases.duplicateparams2)


;; foo8 gives an error when attempting to compile it with Clojure
;; 1.8.0 or earlier. It is OK with Clojure 1.9.0.

(defn foo8 [{:my.ns/keys [a b] :other.ns/syms [a c]}]
  [a b c])

;; user=> (foo8 {:my.ns/a 1 :my.ns/b 2 'other.ns/a 4 'other.ns/c 3})
;; [4 2 3]
