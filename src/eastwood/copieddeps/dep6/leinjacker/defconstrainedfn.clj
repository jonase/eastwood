(ns eastwood.copieddeps.dep6.leinjacker.defconstrainedfn
  "Borrows defconstrainedfn from trammel until a core.contracts based trammel version is released."
    (:require eastwood.copieddeps.dep7.clojure.core.contracts.impl.transformers))


;; borrowed from trammel.core: https://github.com/fogus/trammel
(defmacro defconstrainedfn
  "Defines a function using the `contract` vector appearing after the arguments.

       (defconstrainedfn sqr
         [n] [number? (not= 0 n) => pos? number?]
         (* n n))

   Like the `contract` macro, multiple arity functions can be defined where each argument vector 
   is immediately followed by the relevent arity expectations.  This macro will also detect
   if a map is in that constraints position and use that instead under the assumption that
   Clojure's `:pre`/`:post` map is used instead.
  "
  [name & body]
  (let [mdata (if (string? (first body))
                {:doc (first body)}
                {})
        body  (if (:doc mdata)
                (next body)
                body)
        body  (if (vector? (first body))
                (list body)
                body)
        body  (for [[args cnstr & bd] body]
                (list* args
                       (if (vector? cnstr)
                         (second (#'eastwood.copieddeps.dep7.clojure.core.contracts.impl.transformers/build-constraints-description args cnstr (:doc mdata)))
                         cnstr)
                       bd))]
    `(defn ~name
       ~(str (:doc mdata))
       ~@body)))
