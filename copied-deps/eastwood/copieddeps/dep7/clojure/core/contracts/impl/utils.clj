(ns eastwood.copieddeps.dep7.clojure.core.contracts.impl.utils
  (:require [eastwood.copieddeps.dep8.clojure.core.unify :as unify]))

(defn keys-apply [f ks m]
  (let [only (select-keys m ks)]
    (zipmap (keys only) (map f (vals only)))))


(defn manip-map [f ks m]
  (conj m (keys-apply f ks m)))


(defmacro assert-w-message
  [check message]
  `(when-not ~check
     (throw (new AssertionError (str "Assertion failure: " ~message "\n"
                                     (pr-str '~check))))))

(defn check-args!
  [name slots inv-description invariants]
  (assert-w-message (and inv-description (string? inv-description))
                    (str "Expecting an invariant description for " name))
  (assert-w-message (and invariants (or (map? invariants) (vector? invariants)))
                    (str "Expecting invariants of the form "
                         "[pre-conditions => post-conditions] or "
                         "{:pre [pre-conditions]}"
                         "for record type " name)))
