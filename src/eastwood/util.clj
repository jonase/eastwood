(ns eastwood.util
  (:import [java.io StringReader]
           [java.net URI]
           [clojure.lang LineNumberingPushbackReader])
  (:require [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.env :as env]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.utils :as utils]
            [eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm :as ana.jvm]
            [eastwood.copieddeps.dep10.clojure.tools.reader :as trdr]
            [clojure.pprint :as pp]
            [eastwood.copieddeps.dep10.clojure.tools.reader.reader-types :as rdr-types]))

(defn uri? [obj]
  (instance? java.net.URI obj))

;; ordering-map copied under Eclipse Public License v1.0 from useful
;; library available at: https://github.com/flatland/useful

(defn ordering-map
  "Create an empty map with a custom comparator that puts the given
keys first, in the order specified. Other keys will be placed after
the special keys, sorted by the default-comparator."
  ([key-order] (ordering-map key-order compare))
  ([key-order default-comparator]
     (let [indices (into {} (map-indexed (fn [i x] [x i]) key-order))]
       (sorted-map-by (fn [a b]
                        (if-let [a-idx (indices a)]
                          (if-let [b-idx (indices b)]
                            (compare a-idx b-idx)
                            -1)
                          (if-let [b-idx (indices b)]
                            1
                            (default-comparator a b))))))))

(defn ast-to-ordered
  "Take an ast and return an identical one, except every map is
replaced with an ordering-map.  The sorting of keys in a specified
order can make it significantly easier when printing them out, to see
more interesting keys earlier."
  [ast]
  (let [empty (ordering-map [:op
                             :children
                             :top-level
                             :tag
                             :form
                             :var
                             :raw-forms
                             :eastwood/partly-resolved-forms
                             :eastwood/ancestors
                             :env

                             ;; Some keywords I have seen in :children
                             ;; vectors, given in the same relative
                             ;; order as I saw them.
                             :catches
                             :finally
                             :statements
                             :ret
                             :test
                             :then
                             :else
                             :fn
                             :instance
                             :args
                             :params
                             :bindings
                             :body
                             :expr
                             :meta
                             :init
                             :local
                             :methods
                             :keys
                             :vals
                             ])]
    (ast/postwalk ast (fn [ast]
                        (into empty ast)))))


(defn has-keys? [m key-seq]
  (every? #(contains? m %) key-seq))


(defn keys-in-map
  "Return the subset of key-set that are keys of map m, or nil if no
element of key-set is a key of m."
  [key-set m]
  (let [keys-in-m (reduce (fn [result item]
                            (if (contains? m item)
                              (conj result item)
                              result))
                          (empty key-set) key-set)]
    (if (empty? keys-in-m)
      nil
      keys-in-m)))


(defn assert-keys [m key-seq]
  (assert (has-keys? m key-seq)))


(defn protocol?
  "Make a good guess as to whether p is an object created via defprotocol."
  [p]
  (and (map? p)
       (has-keys? p [:on :on-interface :sigs :var :method-map
                     :method-builders])))


(defn butlast+last
  "Returns same value as (juxt butlast last), but slightly more
efficient since it only traverses the input sequence s once, not
twice."
  [s]
  (loop [butlast (transient [])
         s s]
    (if-let [xs (next s)]
      (recur (conj! butlast (first s)) xs)
      [(seq (persistent! butlast)) (first s)])))

(defn map-keys [f m]
  (into (empty m)
        (for [[k v] m] [(f k) v])))

(defn map-vals [f m]
  (into (empty m)
        (for [[k v] m] [k (f v)])))

(defn filter-keys [f m]
  (into (empty m)
        (filter (fn [[k _]] (f k)) m)))

(defn filter-vals [f m]
  (into (empty m)
        (filter (fn [[_ v]] (f v)) m)))

(defn nth-last
  "Return the nth-last element of a vector v, where n=1 means the last
element, n=2 is the second-to-last, etc.  Returns nil if there are
fewer than n elements in the vector."
  [v n]
  (let [c (count v)]
    (if (>= c n)
      (v (- c n)))))

(defn var-to-fqsym [^clojure.lang.Var v]
  (if v (symbol (str (.ns v)) (str (.sym v)))))

(defn op= [op]
  (fn [ast]
    (= (:op ast) op)))

(defn add-loc-info
  "Return a map that is the same as warn-map, plus file location,
currently in keys :file :line :column, from a map 'loc' containing the
location."
  [loc warn-map]
  (merge warn-map
         (select-keys loc #{:file :line :column})))

(defn walk
  "Traverses form, an arbitrary data structure.  inner and outer are
  functions.  Applies inner to each element of form, building up a
  data structure of the same type, then applies outer to the result.
  Recognizes all Clojure data structures. Consumes seqs as with doall.

  walk was copied from Clojure 1.6.0, and then modified to preserve
  metadata on all sub-values of the input form that have them.  Note
  that this metadata is 'put back on' after the function inner has
  already been called, but before outer, so only outer has a chance to
  change it."
  [inner outer form]
  (let [m (meta form)
        f (cond
           (list? form) (apply list (map inner form))
           (instance? clojure.lang.IMapEntry form) (vec (map inner form))
           (seq? form) (doall (map inner form))
           (instance? clojure.lang.IRecord form)
             (reduce (fn [r x] (conj r (inner x))) form form)
           (coll? form) (into (empty form) (map inner form))
           :else form)]
    (if (and m (instance? clojure.lang.IObj f))
      (outer (with-meta f m))
      (outer f))))

;; postwalk and prewalk are unmodified from Clojure 1.6.0, except that
;; they use this modified version of walk.

(defn postwalk
  "Performs a depth-first, post-order traversal of form.  Calls f on
  each sub-form, uses f's return value in place of the original.
  Recognizes all Clojure data structures. Consumes seqs as with doall."
  {:added "1.1"}
  [f form]
  (walk (partial postwalk f) f form))

(defn prewalk
  "Like postwalk, but does pre-order traversal."
  [f form]
  (walk (partial prewalk f) identity (f form)))

;; From Chris Perkins and Cedric Greevey in the Clojure Google group,
;; Jan 20 2012:
;; https://groups.google.com/forum/#!topic/clojure/5LRmPXutah8

;; Note: The binding of *print-meta* to false is there because if you
;; invoke the body of this fn with *print-meta* to true, you will see
;; *two* copies of metadata on symbols.

;; This version of the function includes a suggested enhancement by
;; Cedric Greevey later in the discussion thread.  As the test cases
;; below the function show, it appears to work correctly even if
;; metadata maps have metadata themselves.

(defn pprint-meta
  "A version of pprint that prints all metadata on the object,
wherever it appears.  (binding [*print-meta* true] (pprint obj))
prints metadata on symbols, but not on collection, at least with
Clojure 1.6.0 and probably earlier versions.  Clojure ticket CLJ-1445
may improve upon this in the future.

http://dev.clojure.org/jira/browse/CLJ-1445"
  [obj]
  (binding [*print-meta* false]
    (let [orig-dispatch clojure.pprint/*print-pprint-dispatch*]
      (clojure.pprint/with-pprint-dispatch
        (fn pm [o]
          (let [o (if (protocol? o)
                    (assoc o
                      :var :true-value-replaced-to-avoid-pprint-infinite-loop
                      :method-builders :true-value-replaced-to-avoid-pprint-infinite-loop)
                    o)]
            (when (meta o)
              (print "^")
              (pm (meta o))
              (.write ^java.io.Writer *out* " ")
              (clojure.pprint/pprint-newline :fill))
            (orig-dispatch o)))
        (clojure.pprint/pprint obj)))))

(defn elide-meta [form elide-key-set]
  (let [eli (fn [form]
              (if (and (meta form) (instance? clojure.lang.IObj form))
                (let [m (meta form)
                      m (apply dissoc m elide-key-set)
                      ;; recursively elide metadata on the values in
                      ;; the metadata
                      m (map-vals #(elide-meta % elide-key-set) m)]
                  (with-meta form m))
                form))]
    (postwalk eli form)))

;; TBD: pprint-meta-elided does not elide metadata that is on the keys
;; or values inside of other metadata maps.  Such metadata does occur
;; in output of tools.reader, e.g. when metadta is in the source code,
;; symbols inside the metadata map keys/vals have metadata maps with
;; :line :column etc. info.

(defn pprint-meta-elided [form]
  (binding [*print-meta* true
            *print-length* 50]
    (pprint-meta
     (elide-meta form #{:column :file :end-line :end-column}))))

(defn trim-ast [ast action key-set]
  (ast/postwalk ast (fn [ast]
                      (case action
                        :keep-only (select-keys ast key-set)
                        :remove-only (apply dissoc ast key-set)))))

(defn elide-defprotocol-vars
  "Motivation: pprint'ing a value containing a defprotocol Var, with
all metadata included leads to an infinite loop.  Why?  The value of a
defprotocol's Var is a map.

(1) The value of the key :var in this map is the defprotocol Var
    itself, and pprint'ing this recurses infinitely.

(2) The value of the key :method-builders in this map has metadata
    containing the defprotocol Var, so if metadata is being printed, this
    also leads to an infinite loop.

These problems are most simply avoided by removing these two keys from
defprotocol maps.

Note: This can replace Vars in the AST with their values, so the
resulting AST is not as it was originally, and will likely fail if you
try to generate code from it.  It is fine for pprint'ing.

There are cases where this function misses things.  It seems like it
may be simpler to detect protocol occurrences while doing
pprint-meta instead."
  [ast]
  (ast/postwalk ast (fn [ast]
                      (map-vals (fn [val]
                                  (cond
                                   (protocol? val)
                                   (dissoc val :var :method-builders)

                                   (and (var? val) (protocol? @val))
                                   (dissoc @val :var :method-builders)

                                   :else val))
                                ast))))

(defn pprint-ast-node [ast & kws]
  (let [a (if (contains? (set kws) :with-env)
            ast
            (trim-ast ast :remove-only [:env]))]
    (-> a
        (trim-ast :remove-only [:eastwood/ancestors])
        ast-to-ordered
        pprint-meta-elided)))

(defn pprint-form [form]
  (pprint-meta-elided form))

(defn enhance-extend-args [extend-args]
  (let [[atype-ast & proto+mmaps-asts] extend-args
        atype-sym (:form atype-ast)]
    (vec
     (cons atype-ast
           (apply concat
                  (for [[proto-ast mmap-ast] (partition 2 proto+mmaps-asts)]
                    (let [proto-sym (:form proto-ast)
                          enh-mmap-vals
                          (vec
                           (map (fn [meth-keyword-ast meth-fn-ast]
                                  (let [meth-keyword (:form meth-keyword-ast)
                                        meth-sym (name meth-keyword)]
                                    (update-in meth-fn-ast
                                               [:env :name]
                                               (constantly
                                                (format
                                                 "protocol %s type %s method %s"
                                                 proto-sym
                                                 (cond (nil? atype-sym) "nil"
                                                       (class? atype-sym) (.getName ^Class atype-sym)
                                                       :else atype-sym)
                                                 meth-sym)))))
                                (:keys mmap-ast) (:vals mmap-ast)))]
                      [proto-ast (assoc mmap-ast :vals enh-mmap-vals)])))))))

(defn extend-invocation-ast? [ast]
  (and (= (:op ast) :invoke)
       (= 'clojure.core/extend (var-to-fqsym (get-in ast [:fn :var])))))

(defn enhance-extend-invocations-prewalk [ast]
  (if (extend-invocation-ast? ast)
    (update-in ast [:args] enhance-extend-args)
    ;; else no enhancement
    ast))

(defn enhance-extend-invocations [ast]
  (ast/prewalk ast enhance-extend-invocations-prewalk))

(defn regex? [x]
  (instance? java.util.regex.Pattern x))

(defn interface? [obj]
  (and (= Class (class obj))
       (.isInterface ^Class obj)))

(defn invoke-expr? [ast-node]
  (and (= :invoke (:op ast-node))
       (contains? ast-node :fn)
       (contains? (:fn ast-node) :var)))

(defn static-call? [ast-node]
  (and (= :static-call (:op ast-node))
       (contains? ast-node :class)
       (contains? ast-node :method)))

(defn string->forms
  "Treat a string as a sequence of 0 or more Clojure forms, and read
  all of them.  No line or column number metadata will be attached to
  the returned forms, but sometimes this is what you want.

  Hack alert: In order to be able to correctly interpret keywords of
  the form ::name or ::ns-or-ns-alias/name, you should pass in a
  namespace to bind to the *ns* var during this function's reading of
  s.  The assumption is that this namespace and those it requires are
  already loaded before this function is called, and any aliases used
  by keywords are already set up.  It also assumes that the entire
  string is in that same namespace.  Fortunately, this is pretty
  common for most Clojure code as written today."
  [s ns include-line-col-metadata?]
  (binding [*ns* (or ns *ns*)]
    (let [rdr (if include-line-col-metadata?
                (LineNumberingPushbackReader. (StringReader. s))
                (rdr-types/string-push-back-reader s))
          eof (reify)]
      (loop [forms []]
        (let [x (trdr/read rdr nil eof)]
          (if (identical? x eof)
            forms
            (recur (conj forms x))))))))

(defn subforms-with-first-in-set [form sym-set]
  (let [a (atom [])]
    (prewalk (fn [form]
               (when (and (sequential? form)
                          (not (vector? form))
                          (contains? sym-set (first form)))
                 (swap! a conj form))
               form)
             form)
    @a))

(defn replace-subforms-with-first-in-set [form sym-set replace-fn]
  (prewalk (fn [form]
             (if (and (sequential? form)
                      (not (vector? form))
                      (contains? sym-set (first form)))
               (replace-fn form)
               form))
           form))

(defn replace-comments-and-quotes-with-nil [form]
  (replace-subforms-with-first-in-set form #{'comment 'quote} (constantly nil)))

(defn- mark-statements-in-try-body-post [ast]
  (if (and (= :try (:op ast))
           (some #{:body} (:children ast))
           (let [body (:body ast)]
             (and (= :do (:op body))
                  (some #{:statements} (:children body))
                  (vector? (:statements body)))))
    (update-in ast [:body :statements]
               (fn [stmts]
                 (mapv #(assoc % :eastwood/unused-ret-val-expr-in-try-body true)
                       stmts)))
    ast))

(defn- mark-exprs-in-try-body-post [ast]
  (if-not (= :try (:op ast))
    ast
    (if-not (some #{:body} (:children ast))
        (do
          (println (format "Found try node but it had no :body key in its :children, only %s"
                           (seq (:children ast))))
          (pprint-ast-node ast)
          ast)
        (let [body (:body ast)]
          (if-not (= :do (:op body))
            (update-in ast [:body]
                       (fn [node]
                         (assoc node
                           :eastwood/used-ret-val-expr-in-try-body true)))
            (let [;; Mark statements - i.e. non-returning expressions in body
                  ast (if-not (and (some #{:statements} (:children body))
                                   (vector? (:statements body)))
                        (do
                          (println (format "Found :try node with :body child that is :do, but either do has no :statements in children (only %s), or it does, but it is not a vector (it has class %s)"
                                           (seq (:children body))
                                           (class (:statements body))))
                          (pprint-ast-node ast)
                          ast)
                        (update-in ast [:body :statements]
                                   (fn [stmts]
                                     (mapv #(assoc % :eastwood/unused-ret-val-expr-in-try-body true)
                                           stmts))))
                  ;; Mark the return expression
                  ast (if-not (some #{:ret} (:children body))
                        (do
                          (println (format "Found :try node with :body child that is :do, but do has no :ret in children (only %s)"
                                           (seq (:children body))))
                          (pprint-ast-node ast)
                          ast)
                        (update-in ast [:body :ret]
                                   (fn [node]
                                     (assoc node
                                       :eastwood/used-ret-val-expr-in-try-body true))))]
              ast))))))

(defn mark-exprs-in-try-body
  "Return an ast that is identical to the argument, except that
expressions 'directly' within try blocks will have one of two new
keywords with value true.

Statements, i.e. expressions that are not the last one in the body,
and thus their return value is discarded, will have the new
keyword :eastwood/unused-ret-val-expr-in-try-body with value true.
Use the fn statement-in-try-body? to check whether a node was so
marked.

Return values, i.e. expressions that are the last one in the body,
will have the new keyword :eastwood/used-ret-val-expr-in-try-body with
value true.  Use the fn ret-expr-in-try-body? to check whether a node
was so marked.

Use fn expr-in-try-body? to check whether an expression was either one
of these kind."
  [ast]
  (ast/postwalk ast mark-exprs-in-try-body-post))

(defn statement-in-try-body? [ast]
  (contains? ast :eastwood/unused-ret-val-expr-in-try-body))

(defn ret-expr-in-try-body? [ast]
  (contains? ast :eastwood/used-ret-val-expr-in-try-body))

(defn expr-in-try-body? [ast]
  (or (statement-in-try-body? ast)
      (ret-expr-in-try-body? ast)))

(defn debug? [debug-options opt]
  (assert (set? debug-options))
  (assert (map? opt))
  (let [d (:debug opt)]
    (or (contains? d :all)
        (some debug-options d))))


(defn make-msg-cb
  "Tiny helper function to create a simple way to call the Eastwood callback function with only a message string.

Given an option map opt that should have a key :callback whose value
is a callback function (which takes a certain kind of map as its only
argument), return a function that takes only a string, and then calls
the callback function with a proper kind of map, including that
message string."
  [kind opt]
  (fn [msg-str]
    ((:callback opt) {:kind kind, :msg msg-str, :opt opt})))


(defmacro with-out-str2
  "Like with-out-str, but returns a map m.  (:val m) is the return
value of the last expression in the body.  (:out m) is the string
normally returned by with-out-str.  (:err m) is the string that would
be returned by with-out-str if it bound *err* instead of *out* to a
StringWriter."
  [& body]
  `(let [s# (new java.io.StringWriter)
         s2# (new java.io.StringWriter)
         x# (binding [*out* s#
                      *err* s2#]
              ~@body)]
     {:val x# :out (str s#) :err (str s2#)}))
