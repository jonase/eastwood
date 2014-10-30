;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:doc "A clojure reader in clojure"
      :author "Bronsa"}
  eastwood.copieddeps.dep10.clojure.tools.reader
  (:refer-clojure :exclude [read read-line read-string char
                            default-data-readers *default-data-reader-fn*
                            *read-eval* *data-readers*])
  (:use eastwood.copieddeps.dep10.clojure.tools.reader.reader-types
        [eastwood.copieddeps.dep10.clojure.tools.reader.impl utils commons])
  (:require [eastwood.copieddeps.dep10.clojure.tools.reader.default-data-readers :as data-readers])
  (:import (clojure.lang PersistentHashSet IMeta
                         RT Symbol Reflector Var IObj
                         PersistentVector IRecord Namespace)
           java.lang.reflect.Constructor
           java.util.regex.Pattern))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare read macros dispatch-macros
         ^:dynamic *read-eval*
         ^:dynamic *data-readers*
         ^:dynamic *default-data-reader-fn*
         default-data-readers)

(defn- macro-terminating? [ch]
  (case ch
    (\" \; \@ \^ \` \~ \( \) \[ \] \{ \} \\) true
    false))

(defn- ^String read-token
  [rdr initch]
  (if-not initch
    (reader-error rdr "EOF while reading")
    (loop [sb (StringBuilder.) ch initch]
      (if (or (whitespace? ch)
              (macro-terminating? ch)
              (nil? ch))
        (do (unread rdr ch)
            (str sb))
        (recur (.append sb ch) (read-char rdr))))))

(declare read-tagged)

(defn- read-dispatch
  [rdr _]
  (if-let [ch (read-char rdr)]
    (if-let [dm (dispatch-macros ch)]
      (dm rdr ch)
      (if-let [obj (read-tagged (doto rdr (unread ch)) ch)] ;; ctor reader is implemented as a taggged literal
        obj
        (reader-error rdr "No dispatch macro for " ch)))
    (reader-error rdr "EOF while reading character")))

(defn- read-unmatched-delimiter
  [rdr ch]
  (reader-error rdr "Unmatched delimiter " ch))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; readers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- read-unicode-char
  ([^String token offset length base]
     (let [l (+ offset length)]
       (when-not (== (count token) l)
         (throw (IllegalArgumentException. (str "Invalid unicode character: \\" token))))
       (loop [i offset uc 0]
         (if (== i l)
           (char uc)
           (let [d (Character/digit (int (nth token i)) (int base))]
             (if (== d -1)
               (throw (IllegalArgumentException. (str "Invalid digit: " (nth token i))))
               (recur (inc i) (long (+ d (* uc base))))))))))

  ([rdr initch base length exact?]
     (loop [i 1 uc (Character/digit (int initch) (int base))]
       (if (== uc -1)
         (throw (IllegalArgumentException. (str "Invalid digit: " initch)))
         (if-not (== i length)
           (let [ch (peek-char rdr)]
             (if (or (whitespace? ch)
                     (macros ch)
                     (nil? ch))
               (if exact?
                 (throw (IllegalArgumentException.
                         (str "Invalid character length: " i ", should be: " length)))
                 (char uc))
               (let [d (Character/digit (int ch) (int base))]
                 (read-char rdr)
                 (if (== d -1)
                   (throw (IllegalArgumentException. (str "Invalid digit: " ch)))
                   (recur (inc i) (long (+ d (* uc base))))))))
           (char uc))))))

(def ^:private ^:const upper-limit (int \uD7ff))
(def ^:private ^:const lower-limit (int \uE000))

(defn- read-char*
  [rdr backslash]
  (let [ch (read-char rdr)]
    (if-not (nil? ch)
      (let [token (if (or (macro-terminating? ch)
                          (whitespace? ch))
                    (str ch)
                    (read-token rdr ch))
            token-len (count token)]
        (cond

         (== 1 token-len)  (Character/valueOf (nth token 0))

         (= token "newline") \newline
         (= token "space") \space
         (= token "tab") \tab
         (= token "backspace") \backspace
         (= token "formfeed") \formfeed
         (= token "return") \return

         (.startsWith token "u")
         (let [c (read-unicode-char token 1 4 16)
               ic (int c)]
           (if (and (> ic upper-limit)
                    (< ic lower-limit))
             (reader-error rdr "Invalid character constant: \\u" (Integer/toString ic 16))
             c))

         (.startsWith token "x")
         (read-unicode-char token 1 2 16)

         (.startsWith token "o")
         (let [len (dec token-len)]
           (if (> len 3)
             (reader-error rdr "Invalid octal escape sequence length: " len)
             (let [uc (read-unicode-char token 1 len 8)]
               (if (> (int uc) 0377)
                 (reader-error rdr "Octal escape sequence must be in range [0, 377]")
                 uc))))

         :else (reader-error rdr "Unsupported character: \\" token)))
      (reader-error rdr "EOF while reading character"))))

(defn- ^PersistentVector read-delimited
  [delim rdr recursive?]
  (let [first-line (when (indexing-reader? rdr)
                     (get-line-number rdr))
        delim (char delim)]
    (loop [a (transient [])]
      (if-let [ch (read-past whitespace? rdr)]
        (if (identical? delim (char ch))
          (persistent! a)
          (if-let [macrofn (macros ch)]
            (let [mret (log-source-unread rdr
                         (macrofn rdr ch))]
              (recur (if-not (identical? mret rdr) (conj! a mret) a)))
            (let [o (read (doto rdr (unread ch)) true nil recursive?)]
              (recur (if-not (identical? o rdr) (conj! a o) a)))))
        (reader-error rdr "EOF while reading"
                      (when first-line
                        (str ", starting at line " first-line)))))))

(defn- read-list
  [rdr _]
  (let [[start-line start-column] (when (indexing-reader? rdr)
                                    [(get-line-number rdr) (int (dec (get-column-number rdr)))])
        the-list (read-delimited \) rdr true)
        [end-line end-column] (when (indexing-reader? rdr)
                                [(get-line-number rdr) (int (get-column-number rdr))])]
    (with-meta (if (empty? the-list)
                 '()
                 (clojure.lang.PersistentList/create the-list))
      (when start-line
        (merge
         (when-let [file (get-file-name rdr)]
           {:file file})
         {:line start-line
          :column start-column
          :end-line end-line
          :end-column end-column})))))

(defn- read-vector
  [rdr _]
  (let [[start-line start-column] (when (indexing-reader? rdr)
                                    [(get-line-number rdr) (int (dec (get-column-number rdr)))])
        the-vector (read-delimited \] rdr true)
        [end-line end-column] (when (indexing-reader? rdr)
                                [(get-line-number rdr) (int (get-column-number rdr))])]
    (with-meta the-vector
      (when start-line
        (merge
         (when-let [file (get-file-name rdr)]
           {:file file})
         {:line start-line
          :column start-column
          :end-line end-line
          :end-column end-column})))))

(defn- read-map
  [rdr _]
  (let [[start-line start-column] (when (indexing-reader? rdr)
                                    [(get-line-number rdr) (int (dec (get-column-number rdr)))])
        the-map (read-delimited \} rdr true)
        map-count (count the-map)
        [end-line end-column] (when (indexing-reader? rdr)
                                [(get-line-number rdr) (int (get-column-number rdr))])]
    (when (odd? map-count)
      (reader-error rdr "Map literal must contain an even number of forms"))
    (with-meta
      (if (zero? map-count)
        {}
        (RT/map (to-array the-map)))
      (when start-line
        (merge
         (when-let [file (get-file-name rdr)]
           {:file file})
         {:line start-line
          :column start-column
          :end-line end-line
          :end-column end-column})))))

(defn- read-number
  [reader initch]
  (loop [sb (doto (StringBuilder.) (.append initch))
         ch (read-char reader)]
    (if (or (whitespace? ch) (macros ch) (nil? ch))
      (let [s (str sb)]
        (unread reader ch)
        (or (match-number s)
            (reader-error reader "Invalid number format [" s "]")))
      (recur (doto sb (.append ch)) (read-char reader)))))

(defn- escape-char [sb rdr]
  (let [ch (read-char rdr)]
    (case ch
      \t "\t"
      \r "\r"
      \n "\n"
      \\ "\\"
      \" "\""
      \b "\b"
      \f "\f"
      \u (let [ch (read-char rdr)]
           (if (== -1 (Character/digit (int ch) 16))
             (reader-error rdr "Invalid unicode escape: \\u" ch)
             (read-unicode-char rdr ch 16 4 true)))
      \x (let [ch (read-char rdr)]
           (if (== -1 (Character/digit (int ch) 16))
             (reader-error rdr "Invalid unicode escape: \\x" ch)
             (read-unicode-char rdr ch 16 2 true)))
      (if (numeric? ch)
        (let [ch (read-unicode-char rdr ch 8 3 false)]
          (if (> (int ch) 0337)
            (reader-error rdr "Octal escape sequence must be in range [0, 377]")
            ch))
        (reader-error rdr "Unsupported escape character: \\" ch)))))

(defn- read-string*
  [reader _]
  (loop [sb (StringBuilder.)
         ch (read-char reader)]
    (case ch
      nil (reader-error reader "EOF while reading string")
      \\ (recur (doto sb (.append (escape-char sb reader)))
                (read-char reader))
      \" (str sb)
      (recur (doto sb (.append ch)) (read-char reader)))))

(defn- read-symbol
  [rdr initch]
  (let [[line column] (when (indexing-reader? rdr)
                        [(get-line-number rdr) (int (dec (get-column-number rdr)))])]
    (when-let [token (read-token rdr initch)]
      (case token

        ;; special symbols
        "nil" nil
        "true" true
        "false" false
        "/" '/
        "NaN" Double/NaN
        "-Infinity" Double/NEGATIVE_INFINITY
        ("Infinity" "+Infinity") Double/POSITIVE_INFINITY

        (or (when-let [p (parse-symbol token)]
              (with-meta (symbol (p 0) (p 1))
                (when line
                  (merge
                   (when-let [file (get-file-name rdr)]
                     {:file file})
                   {:line line
                    :column column
                    :end-line (get-line-number rdr)
                    :end-column (int (inc (get-column-number rdr)))}))))
            (reader-error rdr "Invalid token: " token))))))

(def ^:dynamic *alias-map*
  "Map from ns alias to ns, if non-nil, it will be used to resolve read-time
   ns aliases instead of (ns-aliases *ns*).

   Defaults to nil"
  nil)

(defn- resolve-ns [sym]
  (or ((or *alias-map*
           (ns-aliases *ns*)) sym)
      (find-ns sym)))

(defn- read-keyword
  [reader initch]
  (let [ch (read-char reader)]
    (if-not (whitespace? ch)
      (let [token (read-token reader ch)
            s (parse-symbol token)]
        (if s
          (let [^String ns (s 0)
                ^String name (s 1)]
            (if (identical? \: (nth token 0))
              (if ns
                (let [ns (resolve-ns (symbol (subs ns 1)))]
                  (if ns
                    (keyword (str ns) name)
                    (reader-error reader "Invalid token: :" token)))
                (keyword (str *ns*) (subs name 1)))
              (keyword ns name)))
          (reader-error reader "Invalid token: :" token)))
      (reader-error reader "Invalid token: :"))))

(defn- wrapping-reader
  [sym]
  (fn [rdr _]
    (list sym (read rdr true nil true))))

(defn- read-meta
  [rdr _]
  (log-source rdr
    (let [[line column] (when (indexing-reader? rdr)
                          [(get-line-number rdr) (int (dec (get-column-number rdr)))])
          m (desugar-meta (read rdr true nil true))]
      (when-not (map? m)
        (reader-error rdr "Metadata must be Symbol, Keyword, String or Map"))
      (let [o (read rdr true nil true)]
        (if (instance? IMeta o)
          (let [m (if (and line (seq? o))
                    (assoc m :line line :column column)
                    m)]
            (if (instance? IObj o)
              (with-meta o (merge (meta o) m))
              (reset-meta! o m)))
          (reader-error rdr "Metadata can only be applied to IMetas"))))))

(defn- read-set
  [rdr _]
  (let [[start-line start-column] (when (indexing-reader? rdr)
                                    [(get-line-number rdr) (int (dec (get-column-number rdr)))])
        the-set (PersistentHashSet/createWithCheck (read-delimited \} rdr true))
        [end-line end-column] (when (indexing-reader? rdr)
                                [(get-line-number rdr) (int (get-column-number rdr))])]
    (with-meta the-set
      (when start-line
        (merge
         (when-let [file (get-file-name rdr)]
           {:file file})
         {:line start-line
          :column start-column
          :end-line end-line
          :end-column end-column})))))

(defn- read-discard
  [rdr _]
  (doto rdr
    (read true nil true)))

(def ^:private ^:dynamic arg-env)

(defn- garg [n]
  (symbol (str (if (== -1 n) "rest" (str "p" n))
               "__" (RT/nextID) "#")))

(defn- read-fn
  [rdr _]
  (if (thread-bound? #'arg-env)
    (throw (IllegalStateException. "Nested #()s are not allowed")))
  (binding [arg-env (sorted-map)]
    (let [form (read (doto rdr (unread \()) true nil true) ;; this sets bindings
          rargs (rseq arg-env)
          args (if rargs
                 (let [higharg (key (first rargs))]
                   (let [args (loop [i 1 args (transient [])]
                                (if (> i higharg)
                                  (persistent! args)
                                  (recur (inc i) (conj! args (or (get arg-env i)
                                                                 (garg i))))))
                         args (if (arg-env -1)
                                (conj args '& (arg-env -1))
                                args)]
                     args))
                 [])]
      (list 'fn* args form))))

(defn- register-arg [n]
  (if (thread-bound? #'arg-env)
    (if-let [ret (arg-env n)]
      ret
      (let [g (garg n)]
        (set! arg-env (assoc arg-env n g))
        g))
    (throw (IllegalStateException. "Arg literal not in #()")))) ;; should never hit this

(declare read-symbol)

(defn- read-arg
  [rdr pct]
  (if-not (thread-bound? #'arg-env)
    (read-symbol rdr pct)
    (let [ch (peek-char rdr)]
      (cond
       (or (whitespace? ch)
           (macro-terminating? ch)
           (nil? ch))
       (register-arg 1)

       (identical? ch \&)
       (do (read-char rdr)
           (register-arg -1))

       :else
       (let [n (read rdr true nil true)]
         (if-not (integer? n)
           (throw (IllegalStateException. "Arg literal must be %, %& or %integer"))
           (register-arg n)))))))

(defn- read-eval
  [rdr _]
  (when-not *read-eval*
    (reader-error rdr "#= not allowed when *read-eval* is false"))
  (eval (read rdr true nil true)))

(def ^:private ^:dynamic gensym-env nil)

(defn- read-unquote
  [rdr comma]
  (if-let [ch (peek-char rdr)]
    (if (identical? \@ ch)
      ((wrapping-reader 'clojure.core/unquote-splicing) (doto rdr read-char) \@)
      ((wrapping-reader 'clojure.core/unquote) rdr \~))))

(declare syntax-quote*)
(defn- unquote-splicing? [form]
  (and (seq? form)
       (= (first form) 'clojure.core/unquote-splicing)))

(defn- unquote? [form]
  (and (seq? form)
       (= (first form) 'clojure.core/unquote)))

(defn- expand-list [s]
  (loop [s (seq s) r (transient [])]
    (if s
      (let [item (first s)
            ret (conj! r
                       (cond
                        (unquote? item)          (list 'clojure.core/list (second item))
                        (unquote-splicing? item) (second item)
                        :else                    (list 'clojure.core/list (syntax-quote* item))))]
        (recur (next s) ret))
      (seq (persistent! r)))))

(defn- flatten-map [form]
  (loop [s (seq form) key-vals (transient [])]
    (if s
      (let [e (first s)]
        (recur (next s) (-> key-vals
                          (conj! (key e))
                          (conj! (val e)))))
      (seq (persistent! key-vals)))))

(defn- register-gensym [sym]
  (if-not gensym-env
    (throw (IllegalStateException. "Gensym literal not in syntax-quote")))
  (or (get gensym-env sym)
      (let [gs (symbol (str (subs (name sym)
                                  0 (dec (count (name sym))))
                            "__" (RT/nextID) "__auto__"))]
        (set! gensym-env (assoc gensym-env sym gs))
        gs)))

(defn ^:dynamic resolve-symbol [s]
  (if (pos? (.indexOf (name s) "."))
    s
    (if-let [ns-str (namespace s)]
      (let [^Namespace ns (resolve-ns (symbol ns-str))]
        (if (or (nil? ns)
                (= (name (ns-name ns)) ns-str)) ;; not an alias
          s
          (symbol (name (.name ns)) (name s))))
      (if-let [o ((ns-map *ns*) s)]
        (if (class? o)
          (symbol (.getName ^Class o))
          (if (var? o)
            (symbol (-> ^Var o .ns .name name) (-> ^Var o .sym name))))
        (symbol (name (ns-name *ns*)) (name s))))))

(defn- add-meta [form ret]
  (if (and (instance? IObj form)
           (seq (dissoc (meta form) :line :column :end-line :end-column :file :source)))
    (list 'clojure.core/with-meta ret (syntax-quote* (meta form)))
    ret))

(defn- syntax-quote-coll [type coll]
  (let [res (list 'clojure.core/sequence
                  (list 'clojure.core/seq
                        (cons 'clojure.core/concat
                              (expand-list coll))))]
    (if type
      (list 'clojure.core/apply type res)
      res)))

(defn map-func [coll]
  (if (>= (count (:val coll)) 16)
    'clojure.core/hash-map
    'clojure.core/array-map))

(defn- syntax-quote* [form]
  (->>
   (cond
    (special-symbol? form) (list 'quote form)

    (symbol? form)
    (list 'quote
          (if (namespace form)
            (let [maybe-class ((ns-map *ns*)
                               (symbol (namespace form)))]
              (if (class? maybe-class)
                (symbol (.getName ^Class maybe-class) (name form))
                (resolve-symbol form)))
            (let [sym (name form)]
              (cond
               (.endsWith sym "#")
               (register-gensym form)

               (.startsWith sym ".")
               form

               (.endsWith sym ".")
               (let [csym (symbol (subs sym 0 (dec (count sym))))]
                 (symbol (.concat (name (resolve-symbol csym)) ".")))
               :else (resolve-symbol form)))))

    (unquote? form) (second form)
    (unquote-splicing? form) (throw (IllegalStateException. "splice not in list"))

    (coll? form)
    (cond

     (instance? IRecord form) form
     (map? form) (syntax-quote-coll (map-func form) (flatten-map form))
     (vector? form) (list 'clojure.core/vec (syntax-quote-coll nil form))
     (set? form) (syntax-quote-coll 'clojure.core/hash-set form)
     (or (seq? form) (list? form))
     (let [seq (seq form)]
       (if seq
         (syntax-quote-coll nil seq)
         '(clojure.core/list)))

     :else (throw (UnsupportedOperationException. "Unknown Collection type")))

    (or (keyword? form)
        (number? form)
        (char? form)
        (string? form)
        (nil? form)
        (instance? Boolean form)
        (instance? Pattern form))
    form

    :else (list 'quote form))
   (add-meta form)))

(defn- read-syntax-quote
  [rdr backquote]
  (binding [gensym-env {}]
    (-> (read rdr true nil true)
      syntax-quote*)))

(defn- macros [ch]
  (case ch
    \" read-string*
    \: read-keyword
    \; read-comment
    \' (wrapping-reader 'quote)
    \@ (wrapping-reader 'clojure.core/deref)
    \^ read-meta
    \` read-syntax-quote ;;(wrapping-reader 'syntax-quote)
    \~ read-unquote
    \( read-list
    \) read-unmatched-delimiter
    \[ read-vector
    \] read-unmatched-delimiter
    \{ read-map
    \} read-unmatched-delimiter
    \\ read-char*
    \% read-arg
    \# read-dispatch
    nil))

(defn- dispatch-macros [ch]
  (case ch
    \^ read-meta                ;deprecated
    \' (wrapping-reader 'var)
    \( read-fn
    \= read-eval
    \{ read-set
    \< (throwing-reader "Unreadable form")
    \" read-regex
    \! read-comment
    \_ read-discard
    nil))

(defn- read-tagged* [rdr tag f]
  (let [o (read rdr true nil true)]
    (f o)))

(defn- read-ctor [rdr class-name]
  (when-not *read-eval*
    (reader-error "Record construction syntax can only be used when *read-eval* == true"))
  (let [class (Class/forName (name class-name) false (RT/baseLoader))
        ch (read-past whitespace? rdr)] ;; differs from clojure
    (if-let [[end-ch form] (case ch
                             \[ [\] :short]
                             \{ [\} :extended]
                             nil)]
      (let [entries (to-array (read-delimited end-ch rdr true))
            numargs (count entries)
            all-ctors (.getConstructors class)
            ctors-num (count all-ctors)]
        (case form
          :short
          (loop [i 0]
            (if (>= i ctors-num)
              (reader-error rdr "Unexpected number of constructor arguments to " (str class)
                            ": got" numargs)
              (if (== (count (.getParameterTypes ^Constructor (aget all-ctors i)))
                      numargs)
                (Reflector/invokeConstructor class entries)
                (recur (inc i)))))
          :extended
          (let [vals (RT/map entries)]
            (loop [s (keys vals)]
              (if s
                (if-not (keyword? (first s))
                  (reader-error rdr "Unreadable ctor form: key must be of type clojure.lang.Keyword")
                  (recur (next s)))))
            (Reflector/invokeStaticMethod class "create" (object-array [vals])))))
      (reader-error rdr "Invalid reader constructor form"))))

(defn- read-tagged [rdr initch]
  (let [tag (read rdr true nil false)]
    (if-not (symbol? tag)
      (reader-error rdr "Reader tag must be a symbol"))
    (if-let [f (or (*data-readers* tag)
                   (default-data-readers tag))]
      (read-tagged* rdr tag f)
      (if (.contains (name tag) ".")
        (read-ctor rdr tag)
        (if-let [f *default-data-reader-fn*]
          (f tag (read rdr true nil true))
          (reader-error rdr "No reader function for tag " (name tag)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *read-eval*
  "Defaults to true.

   ***WARNING***
   This setting implies that the full power of the reader is in play,
   including syntax that can cause code to execute. It should never be
   used with untrusted sources. See also: eastwood.copieddeps.dep10.clojure.tools.reader.edn/read.

   When set to logical false in the thread-local binding,
   the eval reader (#=) and *record/type literal syntax* are disabled in read/load.
   Example (will fail): (binding [*read-eval* false] (read-string \"#=(* 2 21)\"))

   When set to :unknown all reads will fail in contexts where *read-eval*
   has not been explicitly bound to either true or false. This setting
   can be a useful diagnostic tool to ensure that all of your reads
   occur in considered contexts."
  true)

(def ^:dynamic *data-readers*
  "Map from reader tag symbols to data reader Vars.
   Reader tags without namespace qualifiers are reserved for Clojure.
   Default reader tags are defined in eastwood.copieddeps.dep10.clojure.tools.reader/default-data-readers
   and may be overridden by binding this Var."
  {})

(def ^:dynamic *default-data-reader-fn*
  "When no data reader is found for a tag and *default-data-reader-fn*
   is non-nil, it will be called with two arguments, the tag and the value.
   If *default-data-reader-fn* is nil (the default value), an exception
   will be thrown for the unknown tag."
  nil)

(def default-data-readers
  "Default map of data reader functions provided by Clojure.
   May be overridden by binding *data-readers*"
  {'inst #'data-readers/read-instant-date
   'uuid #'data-readers/default-uuid-reader})

(defn read
  "Reads the first object from an IPushbackReader or a java.io.PushbackReader.
   Returns the object read. If EOF, throws if eof-error? is true.
   Otherwise returns sentinel. If no stream is providen, *in* will be used.

   ***WARNING***
   Note that read can execute code (controlled by *read-eval*),
   and as such should be used only with trusted sources.

   To read data structures only, use eastwood.copieddeps.dep10.clojure.tools.reader.edn/read

   Note that the function signature of eastwood.copieddeps.dep10.clojure.tools.reader/read and
   eastwood.copieddeps.dep10.clojure.tools.reader.edn/read is not the same for eof-handling"
  ([] (read *in*))
  ([reader] (read reader true nil))
  ([reader eof-error? sentinel] (read reader eof-error? sentinel false))
  ([reader eof-error? sentinel recursive?]
     (when (= :unknown *read-eval*)
       (reader-error "Reading disallowed - *read-eval* bound to :unknown"))
     (try
       (loop []
         (log-source reader
           (let [ch (read-char reader)]
             (cond
              (whitespace? ch) (recur)
              (nil? ch) (if eof-error? (reader-error reader "EOF") sentinel)
              (number-literal? reader ch) (read-number reader ch)
              (comment-prefix? ch) (do (read-comment reader) (recur))
              :else (let [f (macros ch)]
                      (if f
                        (let [res (f reader ch)]
                          (if (identical? res reader)
                            (recur)
                            res))
                        (read-symbol reader ch)))))))
       (catch Exception e
         (if (ex-info? e)
           (let [d (ex-data e)]
             (if (= :reader-exception (:type d))
               (throw e)
               (throw (ex-info (.getMessage e)
                               (merge {:type :reader-exception}
                                      d
                                      (if (indexing-reader? reader)
                                        {:line   (get-line-number reader)
                                         :column (get-column-number reader)
                                         :file   (get-file-name reader)}))
                               e))))
           (throw (ex-info (.getMessage e)
                           (merge {:type :reader-exception}
                                  (if (indexing-reader? reader)
                                    {:line   (get-line-number reader)
                                     :column (get-column-number reader)
                                     :file   (get-file-name reader)}))
                           e)))))))

(defn read-string
  "Reads one object from the string s.
   Returns nil when s is nil or empty.

   ***WARNING***
   Note that read-string can execute code (controlled by *read-eval*),
   and as such should be used only with trusted sources.

   To read data structures only, use eastwood.copieddeps.dep10.clojure.tools.reader.edn/read-string

   Note that the function signature of eastwood.copieddeps.dep10.clojure.tools.reader/read-string and
   eastwood.copieddeps.dep10.clojure.tools.reader.edn/read-string is not the same for eof-handling"
  [s]
  (when (and s (not (identical? s "")))
    (read (string-push-back-reader s) true nil false)))

(defmacro syntax-quote
  "Macro equivalent to the syntax-quote reader macro (`)."
  [form]
  (binding [gensym-env {}]
    (syntax-quote* form)))
