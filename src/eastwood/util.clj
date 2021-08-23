(ns eastwood.util
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.repl :as repl]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
   [eastwood.copieddeps.dep10.clojure.tools.reader :as reader]
   [eastwood.copieddeps.dep10.clojure.tools.reader.reader-types :as reader-types])
  (:import
   (clojure.lang LineNumberingPushbackReader)
   (java.io File StringReader)))

(defmacro timeit
  "Evaluates expr and returns a vector containing the expression's
  return value followed by the time it took to evaluate in millisec."
  {:style/indent 0}
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr
         elapsed-msec# (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
     [ret# elapsed-msec#]))

(defn min-clojure-version [min-version-vector]
  (>= (compare ((juxt :major :minor) *clojure-version*)
               min-version-vector)
      0))

(defn clojure-1-6-or-later []
  (min-clojure-version [1 6]))

(defn clojure-1-7-or-later []
  (min-clojure-version [1 7]))

(defn clojure-1-8-or-later []
  (min-clojure-version [1 8]))

(defn clojure-1-9-or-later []
  (min-clojure-version [1 9]))

(defn clojure-1-10-or-later []
  (min-clojure-version [1 10]))

;; Before Clojure 1.8.0, an :op :def node resulting from a defn form would
;; have its :init value equal to an AST node with :op :fn

;; Clojure 1.8.0 changed things so that often (always?) the :op :def
;; node has an :init value being an AST node with :op :with-meta, and
;; _that_ AST node has an :expr key whose value is the AST node
;; with :op :fn.

;; Handle both of those cases here.

;; Commit that leads to different behavior with Clojure 1.8.0:
;; https://github.com/clojure/clojure/commit/309c03055b06525c275b278542c881019424760e

(defn get-fn-in-def [expr]
  (let [init (:init expr)]
    (case (:op init)
      :fn init
      :with-meta (let [init-expr (:expr init)]
                   (if (= :fn (:op init-expr))
                     init-expr
                     nil))
      nil)))

;; first char is upper-case

(defn to-uri ^java.net.URI [x]
  (cond (instance? java.net.URI x) x
        (instance? java.io.File x) (.toURI ^java.io.File x)
        (instance? java.net.URL x) (.toURI ^java.net.URL x)
        (string? x) (.toURI (File. ^String x))
        :else (assert false)))

(defn remove-prefix
  "If string s starts with the string prefix, return s with that
  prefix removed. Otherwise, return s."
  [^String s ^String prefix]
  (if (.startsWith s prefix)
    (subs s (count prefix))
    s))

(defn file-warn-info [f cwd-file]
  (let [uri (to-uri f)
        ;; file-or-nil will be nil if uri is a URI like the following,
        ;; which cannot be converted to a File:
        ;; #<URI jar:file:/Users/jafinger/.m2/repository/org/clojure/clojure/1.6.0/clojure-1.6.0.jar!/clojure/test/junit.clj>
        file-or-nil (try
                      (File. uri)
                      (catch IllegalArgumentException _
                        nil))
        uri-or-rel-file-str
        (if (nil? file-or-nil)
          uri
          (let [file-str (str file-or-nil)
                cwd-str (if cwd-file (str cwd-file File/separator) "")]
            (remove-prefix file-str cwd-str)))]
    {:uri uri
     :uri-or-file-name uri-or-rel-file-str}))

(defn canonical-filename
  "Returns the canonical file name for the given file name. A
  canonical file name is platform dependent, but is both absolute and
  unique. See the Java docs for getCanonicalPath for some more details,
  and the examples below.

    http://docs.oracle.com/javase/7/docs/api/java/io/File.html#getCanonicalPath%28%29

  Examples:

  Context: A Linux or Mac OS X system, where the current working
  directory is /Users/jafinger/clj/dolly

  user=> (canonical-filename \"README.md\")
  \"/Users/jafinger/clj/dolly/README.md\"

  user=> (canonical-filename \"../../Documents/\")
  \"/Users/jafinger/Documents\"

  user=> (canonical-filename \"../.././clj/../Documents/././\")
  \"/Users/jafinger/Documents\"

  Context: A Windows 7 system, where the current working directory is
  C:\\Users\\jafinger\\clj\\dolly

  user=> (canonical-filename \"README.md\")
  \"C:\\Users\\jafinger\\clj\\dolly\\README.md\"

  user=> (canonical-filename \"..\\..\\Documents\\\")
  \"C:\\Users\\jafinger\\Documents\"

  user=> (canonical-filename \"..\\..\\.\\clj\\..\\Documents\\.\\.\\\")
  \"C:\\Users\\jafinger\\Documents\""
  [fname]
  (let [^java.io.File f (if (instance? java.io.File fname)
                          fname
                          (java.io.File. ^String fname))]
    (.getCanonicalPath f)))

;; Copied from clojure.repl/pst then modified to 'print' using a
;; callback function, and to use depth nil to print all stack frames.

(defn pst
  "'Prints' a stack trace of the exception,  to the depth requested (the
  entire stack trace if depth is nil). Does not print ex-data."
  [^Throwable e depth]
  (println (str (-> e class .getSimpleName) " "
                (.getMessage e)))
  (let [st (.getStackTrace e)
        cause (.getCause e)]
    (doseq [el (remove #(#{"clojure.lang.RestFn" "clojure.lang.AFn"}
                         (.getClassName ^StackTraceElement %))
                       st)]
      (println (str \tab (repl/stack-element-str el))))
    (when cause
      (println "Caused by:")
      (pst cause (when depth
                   (min depth
                        (+ 2 (- (count (.getStackTrace cause))
                                (count st)))))))))

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
                        (if (indices b)
                          1
                          (default-comparator a b))))))))

(defn ast-to-ordered
  "Take an ast and return an identical one, except every map is
replaced with an ordering-map. The sorting of keys in a specified
order can make it significantly easier when printing them out, to see
more interesting keys earlier."
  [ast]
  (let [empty (ordering-map [:op
                             :children
                             :top-level
                             :eastwood/path
                             :tag
                             :form
                             :var
                             :raw-forms
                             :eastwood/ancestors
                             :env
                             ;; Some keywords I have seen in :children
                             ;; vectors, given in the same relative
                             ;; order as I saw them.
                             :statements
                             :ret     ;; after :statements
                             :test
                             :then    ;; after :test
                             :else    ;; after :else
                             :tests   ;; after :test
                             :thens   ;; after :tests
                             :default ;; after :thens
                             :fields
                             :class
                             :local    ;; after :class
                             :methods  ;; after :local :fields
                             :protocol-fn
                             :keyword
                             :target   ;; after :keyword :protocol-fn
                             :val      ;; after :target
                             :fn
                             :instance
                             :args     ;; after :fn :instance :class :target
                             :this
                             :params   ;; after :this
                             :bindings
                             :body     ;; after :params :bindings :local
                             :catches  ;; after :body
                             :finally  ;; after :catches
                             :meta
                             :expr     ;; after :meta
                             :init     ;; after :meta
                             :keys
                             :vals     ;; after :keys
                             ])]
    (ast/postwalk ast (fn [ast]
                        (if (every? keyword? (keys ast))
                          (into empty ast)
                          ;; Do not make anything into a sorted map if
                          ;; it has keys that are not keywords, since
                          ;; that would risk having key lookups throw
                          ;; exceptions due to incomparable keys.
                          ast)))))

(defn all-children-vectors [asts]
  (->> asts
       (mapcat ast/nodes)
       (keep :children)
       frequencies))

(defn has-keys? [m key-seq]
  (every? #(contains? m %) key-seq))

(defn nil-safe-rseq [s]
  (if (nil? s)
    nil
    (rseq s)))

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

(defn sorted-map-with-non-keyword-keys? [x]
  (and (map? x)
       (sorted? x)
       (some (fn [o] (not (keyword? o))) (keys x))))

(defn protocol?
  "Make a good guess as to whether p is an object created via defprotocol."
  [p]
  (and (map? p)
       ;; Don't even try to do has-keys? on a sorted map with
       ;; non-keyword keys, since it will most likely cause compare to
       ;; throw an exception.
       (not (sorted-map-with-non-keyword-keys? p))
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

(defn longest-common-prefix [s1 s2]
  (loop [ret (transient [])
         s1 (seq s1)
         s2 (seq s2)]
    (if (and (seq s1) (seq s2) (= (first s1) (first s2)))
      (recur (conj! ret (first s1)) (next s1) (next s2))
      (persistent! ret))))

(defn separate-suffix
  "Given a string s and a sequence of strings, suffixes, return a
  vector of 2 strings [x y] where y is the first element of suffixes
  that is a suffix of s, and (str x y)=s. If no string in suffixes is
  a suffix of s, return nil."
  [^String s suffixes]
  (when-let [suffix (some #(when (.endsWith s %)
                             %)
                          suffixes)]
    [(subs s 0 (- (count s) (count suffix))) suffix]))

(defn nth-last
  "Return the nth-last element of a vector v, where n=1 means the last
element, n=2 is the second-to-last, etc. Returns nil if there are
fewer than n elements in the vector."
  [v n]
  (let [c (count v)]
    (when (>= c n)
      (v (- c n)))))

(defn var-to-fqsym [^clojure.lang.Var v]
  (when v
    (symbol (str (.ns v))
            (str (.sym v)))))

(defn op= [op]
  (fn [ast]
    (= (:op ast) op)))

(defn contains-loc-info?
  [x]
  (and (map? x)
       (has-keys? x [:file :line :column])))

(defn walk
  "Traverses form, an arbitrary data structure. inner and outer are
  functions. Applies inner to each element of form, building up a
  data structure of the same type, then applies outer to the result.
  Recognizes all Clojure data structures. Consumes seqs as with doall.

  walk was copied from Clojure 1.6.0, and then modified to preserve
  metadata on all sub-values of the input form that have them. Note
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
  "Performs a depth-first, post-order traversal of form. Calls f on
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
;; Cedric Greevey later in the discussion thread. As the test cases
;; below the function show, it appears to work correctly even if
;; metadata maps have metadata themselves.

(defn pprint-meta
  "A version of pprint that prints all metadata on the object,
wherever it appears. (binding [*print-meta* true] (pprint obj))
prints metadata on symbols, but not on collection, at least with
Clojure 1.6.0 and probably earlier versions. Clojure ticket CLJ-1445
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
;; or values inside of other metadata maps. Such metadata does occur
;; in output of tools.reader, e.g. when metadata is in the source
;; code, symbols inside the metadata map keys/vals have metadata
;; maps with :line :column etc. info.

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
all metadata included leads to an infinite loop. Why?  The value of a
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
try to generate code from it. It is fine for pprint'ing.

There are cases where this function misses things. It seems like it
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

(defn clean-ast [ast & kws]
  (let [kws-to-trim [:eastwood/ancestors]
        kws-to-trim (if (contains? (set kws) :with-env)
                      kws-to-trim
                      (conj kws-to-trim :env))]
    (-> ast
        (trim-ast :remove-only kws-to-trim)
        ast-to-ordered)))

(defn pprint-ast-node [ast & kws]
  (pprint-meta-elided (apply clean-ast ast kws)))

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

(defn fqsym-of-raw-form [raw-form]
  ;; A few raw forms are symbols like clojure.lang.Compiler/COMPILE
  ;; (I'm probably misremembering the precise name, but it was
  ;; definitely a symbol, not a list). Return nil for those.
  (when (seq? raw-form)
    (-> raw-form
        meta
        :eastwood.copieddeps.dep1.clojure.tools.analyzer/resolved-op
        var-to-fqsym)))

(defn ast-expands-macro [ast macro-fqsym-set]
  (some macro-fqsym-set
        (map fqsym-of-raw-form
             (:raw-forms ast))))

(defn invoke-expr? [ast-node]
  (and (= :invoke (:op ast-node))
       (contains? ast-node :fn)
       (contains? (:fn ast-node) :var)))

(defn static-call? [ast-node]
  (and (= :static-call (:op ast-node))
       (contains? ast-node :class)
       (contains? ast-node :method)))

(defn instance-call? [ast-node]
  (and (= :instance-call (:op ast-node))
       (contains? ast-node :class)
       (contains? ast-node :method)))

(defn string->forms
  "Treat a string as a sequence of 0 or more Clojure forms, and read
  all of them. No line or column number metadata will be attached to
  the returned forms, but sometimes this is what you want.

  Hack alert: In order to be able to correctly interpret keywords of
  the form ::name or ::ns-or-ns-alias/name, you should pass in a
  namespace to bind to the *ns* var during this function's reading of
  s. The assumption is that this namespace and those it requires are
  already loaded before this function is called, and any aliases used
  by keywords are already set up. It also assumes that the entire
  string is in that same namespace. Fortunately, this is pretty
  common for most Clojure code as written today."
  [s ns include-line-col-metadata?]
  (binding [reader/*data-readers* *data-readers*
            *ns* (or ns *ns*)]
    (let [rdr (if include-line-col-metadata?
                (LineNumberingPushbackReader. (StringReader. s))
                (reader-types/string-push-back-reader s))
          eof (reify)]
      (loop [forms []]
        (let [x (reader/read {:eof eof :read-cond :allow :features #{:clj}} rdr)]
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

(defn trim-thrown-form
  "Given a `(thrown? Exception ...)`, removes the `Exception` symbol, for making `=` comparisons simpler."
  [x]
  (walk/postwalk (fn [form]
                   (let [thrown-form? (and (sequential? form)
                                           (-> form first #{'thrown?}))]
                     (cond->> form
                       thrown-form? (drop 2)
                       thrown-form? (cons 'thrown?))))
                 x))

(defn remove-unrelated-is-clauses [do-form candidate-is-clause]
  (->> do-form
       rest
       (filter #{candidate-is-clause})
       (distinct) ;; for `clojure.test/are`
       (cons 'do)))

(defn in-thrown?
  "Is `statement-form` part of a `thrown?` (per clojure.test semantics) invoked in `ancestor-form`?"
  [statement-form ancestor-form]
  (let [t? (list 'thrown? statement-form)
        candidates #{(list 'is t?)
                     (list 'clojure.test/is t?)}
        do? (and (sequential? ancestor-form)
                 (-> ancestor-form first #{'do}))]
    (if-not do?
      false ;; performance optimization - avoid `walk`ing irrelevant forms
      (->> candidates
           (reduce (fn [v candidate]
                     (if (= (list 'do candidate)
                            (-> ancestor-form
                                trim-thrown-form
                                (remove-unrelated-is-clauses candidate)))
                       (reduced true)
                       v))
                   false)))))

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
                                   (mapv (fn [m]
                                           (let [statement-form (-> m :raw-forms first)]
                                             (cond-> m
                                               true
                                               (assoc :eastwood/unused-ret-val-expr-in-try-body true)

                                               (and
                                                (sequential? statement-form)
                                                (->> m :eastwood/ancestors (map :form) (some (partial in-thrown? statement-form))))
                                               (assoc :eastwood/in-thrown?-call true))))
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
value true. Use the fn ret-expr-in-try-body? to check whether a node
was so marked.

Use fn expr-in-try-body? to check whether an expression was either one
of these kind."
  [ast]
  (ast/postwalk ast mark-exprs-in-try-body-post))

(defn statement-in-try-body? [ast]
  (contains? ast :eastwood/unused-ret-val-expr-in-try-body))

(defn ret-expr-in-try-body? [ast]
  (contains? ast :eastwood/used-ret-val-expr-in-try-body))

(defn in-thrown?-call?
  "Is `ast` within a clojure.test (thrown?) call?"
  [ast]
  (contains? ast :eastwood/in-thrown?-call))

(defn expr-in-try-body? [ast]
  (or (statement-in-try-body? ast)
      (ret-expr-in-try-body? ast)))

(defn deftype-for-fieldless-defrecord [ast]
  (and (= :deftype (-> ast :op))
       (let [flds (map :form (-> ast :fields))]
         (or (= flds '(__meta __extmap))
             ;; Clojure 1.9.0 performance improvement change for
             ;; CLJ-1224 adds "hidden" fields __hash and __hasheq for
             ;; records. Before then, it was only __meta and __extmap
             (= flds '(__meta __extmap __hash __hasheq))))))

(defn inside-fieldless-defrecord [ast]
  (some deftype-for-fieldless-defrecord
        (nil-safe-rseq (-> ast :eastwood/ancestors))))

(defn ns-form-asts [asts]
  (->> (mapcat ast/nodes asts)
       (filter #(= 'clojure.core/ns
                   (-> % :raw-forms first fqsym-of-raw-form)))))

(defn get-in-ast [ast kvec-op-pairs]
  (let [sentinel (Object.)]
    (loop [ast ast
           pairs kvec-op-pairs]
      (if-let [[ks expected-op] (first pairs)]
        (let [ast2 (get-in ast ks sentinel)]
          (if (identical? ast2 sentinel)
            {:stop-reason :next-ks-not-found, :unused-ks pairs, :ast ast}
            (if (and (map? ast2)
                     (= expected-op (:op ast2)))
              (recur ast2 (rest pairs))
              {:stop-reason :next-step-not-ast, :unused-ks pairs, :ast ast})))
        {:stop-reason nil, :unused-ks nil, :ast ast}))))

(defn get-val-in-map-ast
  ([map-ast k]
   (get-val-in-map-ast map-ast k nil))
  ([map-ast k not-found]
   {:pre [(map? map-ast)
          (= :map (:op map-ast))
          (vector? (:keys map-ast))
          (vector? (:vals map-ast))]}
   (if-let [idx (some #(when (= k (:form (second %))) (first %))
                      (map-indexed vector (:keys map-ast)))]
     ((:vals map-ast) idx)
     not-found)))

(defn debug? [debug-options opt]
  (when-let [d (:debug opt)]
    (or (contains? d :all)
        (and (coll? debug-options)
             (some (set debug-options) d))
        (contains? (set d) debug-options))))

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
  "Like with-out-str, but returns a map m. (:val m) is the return
  value of the last expression in the body. (:out m) is the string
  normally returned by with-out-str. (:err m) is the string that would
  be returned by with-out-str if it bound *err* instead of *out* to a
  StringWriter."
  {:style/indent 0}
  [& body]
  `(let [s# (new java.io.StringWriter)
         s2# (new java.io.StringWriter)
         x# (binding [*out* s#
                      *err* s2#]
              ~@body)]
     {:val x# :out (str s#) :err (str s2#)}))

;; TBD: There are some cases I have seen of calling this for every ast
;; warned about in the :constant-test linter, where the 'leaf' AST
;; seemed to be shown, but none of its ancestor ASTs. This was the
;; case for the last form in constanttestexpr.clj, for example. It
;; would be good to make that case work, too.

(defn print-enclosing-macros
  [ast]
  (println "----------")
  (doseq [[i a] (map-indexed
                 vector
                 (concat (-> ast :eastwood/ancestors) [ast]))]
    (println (format "level %d:" i))
    (doseq [[j f] (map-indexed vector (:raw-forms a))]
      (println (format "  raw-form %2d first=%s"
                       j (try
                           (first f)
                           (catch Exception _
                             (str f " (actual :raw-forms element, not its first)"))))))
    (println (format "  form=%s" (:form a)))
    (println (format "  op=%s" (:op a)))))

(def empty-enclosing-macro-map
  (ordering-map [:depth
                 :index
                 :final
                 :op
                 :macro
                 :eastwood/path
                 :form
                 :first-only
                 :ast]))

(defn enclosing-macros
  [ast]
  (apply concat
         (for [[i a] (map-indexed vector
                                  (cons ast
                                        (nil-safe-rseq (-> ast :eastwood/ancestors))))]
           (for [[j f] (map-indexed vector
                                    (reverse (:raw-forms a)))]
             (into empty-enclosing-macro-map
                   {:depth i, :index j, :op (:op a), :ast a,
                    :eastwood/path (:eastwood/path a),
                    :form f, :resolved-form-meta (meta (:form a)),
                    :macro (fqsym-of-raw-form f)})))))

(defn debug-warning
  ([w ast opt extra-flags]
   (debug-warning w ast opt extra-flags nil))
  ([w ast opt extra-flags f]
   (let [d (cond
             (not (:debug-warning opt)) false
             (true? (:debug-warning opt)) #{}
             :else (set (:debug-warning opt)))]
     (when d
       ((make-msg-cb :debug opt)
        (with-out-str
          (println "This warning:")
          (pp/pprint (dissoc w (:linter w)))
          (when (extra-flags :enclosing-macros)
            (println "was generated from code with the following enclosing macro expansions:")
            (pp/pprint (->> (enclosing-macros ast)
                            (map #(dissoc % :ast :index)))))
          (when f (f))
          (when (d :ast)
            (println "The code has this AST:")
            (if ast
              (pprint-ast-node ast)
              (println "(none specified to debug-warning fn)")))))))))

;; This atom is modified from the config files

(def warning-enable-config-atom (atom []))

(defn disable-warning [m]
  (swap! warning-enable-config-atom conj m))

(def linter-executor-atom
  "A `#'map`-like function that will run each of the linters.

  Can be customized in order to achieve linter parallelism, or extra logging, etc."
  (atom pmap))

(defn set-linter-executor! [executor]
  (reset! linter-executor-atom executor))

(defn process-configs [warning-enable-config]
  (->> warning-enable-config
       (reduce (fn [configs {:keys [linter] :as m}]
                 ;; NOTE: if adding items here, make sure to update the
                 ;; `Linter name | debug | suppress` table in README.
                 (let [[path op obj] (case linter
                                       (:constant-test
                                        :redefd-vars
                                        :suspicious-test
                                        :unused-fn-args
                                        :unused-meta-on-macro
                                        :unused-ret-vals
                                        :unused-ret-vals-in-try
                                        :wrong-arity
                                        :wrong-tag)
                                       [[linter]
                                        conj
                                        (dissoc m :linter)]

                                       :suspicious-expression
                                       [[linter (:for-macro m)]
                                        conj
                                        (dissoc m :linter :for-macro)]

                                       :deprecations
                                       [[linter :symbol-matches]
                                        into
                                        (:symbol-matches m)])]
                   (update-in configs path op obj)))
               {})))

(defn builtin-config-to-resource [name]
  (io/resource (str "eastwood/config/" name)))

(defn init-warning-enable-config [builtin-config-files config-files opt]
  (let [all-config-files (concat (map builtin-config-to-resource builtin-config-files)
                                 config-files)]
    (reset! warning-enable-config-atom [])
    (reset! linter-executor-atom pmap)
    (doseq [config-file all-config-files]
      (when (debug? :config opt)
        (println (format "Loading config file: %s" config-file)))
      (try
        (binding [*ns* (the-ns 'eastwood.util)]
          (load-reader (io/reader config-file)))
        (catch Exception e
          (if (= "true" (System/getProperty "eastwood.internal.running-test-suite"))
            (throw e)
            (do
              (println (format "Exception while attempting to load config file: %s" config-file))
              (pst e nil))))))
    (process-configs @warning-enable-config-atom)))

(defn meets-suppress-condition [linter-id
                                enclosing-macros
                                qualifier
                                condition
                                {:keys [ignore-faults-from-foreign-macroexpansions?]
                                 project-namespaces :eastwood/project-namespaces
                                 linter-info :eastwood/linter-info}]
  {:pre [(set? project-namespaces)
         (instance? Boolean ignore-faults-from-foreign-macroexpansions?)
         (vector? linter-info)]}
  (let [configured-qualifier? (find condition :qualifier)]
    (when (or (= qualifier :eastwood/unset) ;; `nil` can be a qualifier value, so :eastwood/unset conveys an absent value
              (not configured-qualifier?)
              (and configured-qualifier?
                   (= qualifier (:qualifier condition))))
      (let [macro-set (-> condition :if-inside-macroexpansion-of (doto assert))
            depth (:within-depth condition)
            limited-enclosing-macros (cond->> enclosing-macros
                                       (number? depth) (take depth))]
        (or (->> limited-enclosing-macros
                 (some (fn [{:keys [macro]}]
                         (when (macro-set macro)
                           {:matching-condition condition
                            :matching-macro macro}))))
            (when (and ignore-faults-from-foreign-macroexpansions? ;; global option
                       ;; per-linter option:
                       (some->> linter-info
                                (filter (comp #{linter-id} :name))
                                first
                                :ignore-faults-from-foreign-macroexpansions?))
              (->> enclosing-macros
                   (some (fn [{:keys [macro]}]
                           (let [macro-ns (some-> macro namespace)
                                 macro-sym (some-> macro-ns symbol)]
                             (when (and macro ;; can be nil under certain conditions
                                        (not (project-namespaces macro-sym))
                                        ;; macroexpansions generally bottom out at clojure.* stuff:
                                        (not (-> macro-ns (.startsWith "clojure."))))
                               {:matching-condition :ignored-fault-from-foreign-macroexpansions
                                :matching-macro macro})))))))))))

(defn allow-warning-based-on-enclosing-macros [w linter suppress-desc suppress-conditions opt]
  (let [ast (-> w
                linter
                :ast
                (doto (assert "The given warning should provide an `:ast` under its linter key. Else this mechanism cannot work properly.")))
        ;; Don't bother calculating enclosing-macros if there are
        ;; no suppress-conditions to check, to save time.
        encl-macros (when (some :if-inside-macroexpansion-of suppress-conditions)
                      (enclosing-macros ast))
        match (->> suppress-conditions
                   (filter :if-inside-macroexpansion-of)
                   (some #(meets-suppress-condition linter
                                                    encl-macros
                                                    (:qualifier w :eastwood/unset)
                                                    %
                                                    opt)))]
    (when (and match (:debug-suppression opt))
      ((make-msg-cb :debug opt)
       (with-out-str
         (let [c (:matching-condition match)
               depth (:within-depth c)]
           (println (format "Suppressed %s warning%s" linter suppress-desc))
           (println (format "because it is within%s an expansion of macro"
                            (if (number? depth)
                              (format " %d steps of" depth)
                              "")))
           (println (format "'%s'" (:matching-macro match)))
           (println "Reason suppression rule was created:" (:reason c))
           (pp/pprint (map #(dissoc % :ast :index)
                           (if depth
                             (take depth encl-macros)
                             encl-macros)))))))
    ;; allow the warning if there was no match
    (not match)))

(defn allow-warning [w opt]
  (let [linter (:linter w)]
    (case linter
      :suspicious-expression
      (case (-> w linter :kind)
        :macro-invocation
        (let [macro-symbol (-> w linter :macro-symbol)
              suppress-conditions (get-in opt [:warning-enable-config
                                               linter macro-symbol])]
          (allow-warning-based-on-enclosing-macros w
                                                   linter
                                                   (format " for invocation of macro '%s'" macro-symbol)
                                                   suppress-conditions
                                                   opt)))

      (:constant-test
       :suspicious-test
       :unused-fn-args
       :unused-meta-on-macro
       :unused-ret-vals
       :unused-ret-vals-in-try
       :wrong-arity
       :wrong-tag)
      (let [suppress-conditions (get-in opt [:warning-enable-config linter])]
        (allow-warning-based-on-enclosing-macros w
                                                 linter
                                                 ""
                                                 suppress-conditions
                                                 opt)))))

;; Linters that use the info in resource file var-info.edn as of
;; Eastwood 0.2.2:

;; suspicious-test linter:

;; functions with :predicate true are suspicious if they are at top
;; level of deftest or testing, without being wrapped inside (is ...)
;; or similar form.

;; functions with :pure-fn true at top level are similarly suspicious.

;; constant-test linter:

;; functions with :pure-fn true that have constant arguments return
;; constant values, and are thus suspicious as tests in conditional
;; expressions like if or cond.

;; unused-ret-vals and unused-ret-vals-in-try linters:

;; Use several properties
;; like :side-effect :lazy-fn :pure-fn :pure-fn-if-fn-args-pure
;; :warn-if-ret-val-unused to determine whether an expression has an
;; unused return value.

;; For each namespace with at least one symbol as a key in
;; var-info.edn, print the following:
;; * how many of them are in var-info.edn

;; If the namespace is already loaded, also print:
;; * how many are in var-info.edn but not in that namespace
;; * how many are in that namespace but not in var-info.edn.

;; For each namespace returned by (all-ns), list the ones
;; that have no symbols mentioned in var-info.edn at all.

(def loaded-but-not-in-file-marker "        Loaded but not in file:")

(defn print-var-info-summary [var-info-map]
  (let [file-vars-by-ns (->> (keys var-info-map)
                             (group-by #(namespace %))
                             (map-vals #(set (map name %))))
        ;; Try to require the namespaces mentioned in the file, but
        ;; mask any exceptions that occur when trying.
        ;; TBD: figure out extra namespaces from each .jar that may not be reflected in var-info.edn
        ;; ...use simply (all-ns) after performing a first batch of `require`s
        _ (doseq [ns-name (keys file-vars-by-ns)]
            (try
              (println "require " ns-name "...")
              (require (symbol ns-name))
              (catch Throwable _)))
        loaded-vars-by-ns (->> (all-ns)
                               (map (fn [ns]
                                      [(str ns)
                                       (->> (ns-publics ns)
                                            keys
                                            (map str)
                                            set)]))
                               (into {}))
        ns-names (into (sorted-set) (concat (keys file-vars-by-ns)
                                            (keys loaded-vars-by-ns)))
        ;; returns a handy string that can easily be copied into var-info.edn:
        template (fn [ns-name var-name]
                   (let [fqs (symbol (str ns-name)
                                     (str var-name))
                         var-ref (when (-> ns-name str symbol find-ns)
                                   (find-var fqs))
                         macro? (if (-> var-ref meta :macro)
                                  true
                                  nil)
                         ctor? (or (->> var-name str (re-find #"^->[A-Z]"))
                                   (and (->> var-name str (re-find #"^map->[A-Z]"))
                                        (let [fqs (symbol (str ns-name)
                                                          (-> var-name str (string/replace #"^map" "")))]
                                          (try
                                            (find-var fqs)
                                            (catch Exception _
                                              nil))))
                                   (->> var-name str (re-find #"^make-"))
                                   (->> var-name str (re-find #"-factory$")))
                         predicate? (-> var-name str (.endsWith "?"))
                         uninteresting? (or (some-> var-ref deref protocol?)
                                            (and var-ref
                                                 (or (nil? @var-ref)
                                                     (instance? clojure.lang.Var$Unbound @var-ref)
                                                     (coll? @var-ref)
                                                     (not (ifn? @var-ref)))))]
                     (cond
                       uninteresting?
                       (format "%s {:var-kind nil, :macro nil}"
                               fqs)

                       ctor?
                       (format "%s {:var-kind nil, :macro %s, :predicate %s, :side-effect false, :pure-fn true, :warn-if-ret-val-unused true, :lazy false, :pure-if-fn-args-pure true, :io-fn false, :evals-exprs false}"
                               fqs
                               (pr-str macro?)
                               predicate?)

                       :else
                       ;; this format includes all keys, so as to invite maintainers to think of all relevant info.
                       (format "%s {:var-kind nil, :macro %s, :predicate %s, :side-effect , :pure-fn , :warn-if-ret-val-unused , :lazy , :pure-if-fn-args-pure , :io-fn , :evals-exprs }"
                               fqs
                               (pr-str macro?)
                               predicate?))))]
    (println "Clojure version " (clojure-version))
    (println)
    (println "Summary of contents of var-info.edn file:")
    (println)
    (println "Columns:")
    (println "A  # of symbols in the file")
    (println "B  namespace is loaded?")
    (println "C  # of symbols in loaded namespace, but not in the file")
    (println "D  # of symbols in the file, but not in the loaded namespace")
    (println)
    (println (format "   A B    C    D Namespace name"))
    (doseq [ns-name ns-names]
      (let [loaded-vars (get loaded-vars-by-ns ns-name)
            file-vars (get file-vars-by-ns ns-name #{})
            loaded-but-not-file (set/difference (or loaded-vars #{}) file-vars)
            file-but-not-loaded (set/difference file-vars (or loaded-vars #{}))]
        (println
         (format "%4d %s %4s %4s %s"
                 (count file-vars)
                 (if loaded-vars "Y" "N")
                 (if loaded-vars
                   (count loaded-but-not-file)
                   "-")
                 (if loaded-vars
                   (count file-but-not-loaded)
                   "-")
                 ns-name))
        (when (> (count file-vars) 0)
          (when (> (count loaded-but-not-file) 0)
            (println (format loaded-but-not-in-file-marker))
            (doseq [name (sort loaded-but-not-file)]
              (println (template ns-name name))))
          (when (> (count file-but-not-loaded) 0)
            (println (format "            In file but not loaded:"))
            (doseq [name (sort file-but-not-loaded)]
              (println (format "            %s" name)))))))))

(defn expand-exclude-linters [exclude-linters]
  (->> exclude-linters
       (mapcat (fn [x]
                 (if (keyword? x)
                   [x]
                   (let [[y z :as a] x]
                     (if (keyword? z)
                       [a]
                       (->> z
                            (mapv (fn [k]
                                    [y k]))))))))
       (set)))

(defn excludes-kind?
  "Is the linter kind denoted by `path` excluded from runtime configuration?"
  [[linter _kind :as path] exclude-linters--internal]
  {:pre [(vector? path)
         (every? keyword? path)
         (set? exclude-linters--internal)]}
  (->> [linter ;; the whole linter
        ;; only the specific linter `:kind`:
        path]
       (some exclude-linters--internal)
       boolean))

(defn dir-outside-root-dir? [^File f]
  {:pre [(-> f .isDirectory)]}
  (let [f (-> f .getCanonicalPath File.)
        root-dir (File. (System/getProperty "user.dir"))
        parent-dirs (->> f
                         (iterate (fn [^File f]
                                    (some-> f .getCanonicalPath File. .getParent File.)))
                         (take-while some?)
                         (set))]
    (not (parent-dirs root-dir))))

(defn ast->ns [ast]
  {:post [%]}
  (-> ast :env :ns find-ns))
