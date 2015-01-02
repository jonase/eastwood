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
        (do (when ch
              (unread rdr ch))
            (str sb))
        (recur (.append sb ch) (read-char rdr))))))

(declare read-tagged-remembering-loc)

(defn- read-dispatch
  [rdr _]
  (if-let [ch (read-char rdr)]
    (if-let [dm (dispatch-macros ch)]
      (dm rdr ch)
      (if-let [obj (read-tagged-remembering-loc (doto rdr (unread ch)) ch)] ;; ctor reader is implemented as a taggged literal
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
  (let [[start-line start-column] (starting-line-col-info rdr)
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
                      (when start-line
                        (str ", starting at line " start-line " and column " start-column)))))))

(defn- read-list
  [rdr _]
  (let [[start-line start-column] (starting-line-col-info rdr)
        the-list (read-delimited \) rdr true)
        [end-line end-column] (ending-line-col-info rdr)]
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
  (let [[start-line start-column] (starting-line-col-info rdr)
        the-vector (read-delimited \] rdr true)
        [end-line end-column] (ending-line-col-info rdr)]
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
  (let [[start-line start-column] (starting-line-col-info rdr)
        the-map (read-delimited \} rdr true)
        map-count (count the-map)
        [end-line end-column] (ending-line-col-info rdr)]
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
  (let [[line column] (starting-line-col-info rdr)]
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
                   (let [[end-line end-column] (ending-line-col-info rdr)]
                     {:line line
                      :column column
                      :end-line end-line
                      :end-column end-column})))))
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
    (let [[line column] (starting-line-col-info rdr)
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
  (let [[start-line start-column] (starting-line-col-info rdr)
        ;; subtract 1 from start-column so it includes the # in the leading #{
        start-column (if start-column (dec start-column))
        the-set (PersistentHashSet/createWithCheck (read-delimited \} rdr true))
        [end-line end-column] (ending-line-col-info rdr)]
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
  ;; We use sequence rather than seq here to fix http://dev.clojure.org/jira/browse/CLJ-1444
  ;; But because of http://dev.clojure.org/jira/browse/CLJ-1586 we still need to call seq on the form
  (let [res (list 'clojure.core/sequence
                  (list 'clojure.core/seq
                        (cons 'clojure.core/concat
                              (expand-list coll))))]
    (if type
      (list 'clojure.core/apply type res)
      res)))

(defn map-func [coll]
  (if (>= (count coll) 16)
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

(def read-char-remembering-loc
  (wrap-read-fn-remembering-loc read-char* :char))

(def read-number-remembering-loc
  (wrap-read-fn-remembering-loc read-number :number))

(def read-string-remembering-loc
  (wrap-read-fn-remembering-loc read-string* :string))

(def read-symbol-remembering-loc
  (wrap-read-fn-remembering-loc read-symbol :symbol))

(def read-keyword-remembering-loc
  (wrap-read-fn-remembering-loc read-keyword :keyword))

(def read-meta-remembering-loc
  (wrap-read-fn-remembering-loc read-meta :meta))

(def ^:private read-fn-remembering-loc
  (wrap-read-fn-remembering-loc read-fn :fn))

(def ^:private read-arg-remembering-loc
  (wrap-read-fn-remembering-loc read-arg :arg))

(def ^:private read-quote-remembering-loc
  (wrap-read-fn-remembering-loc (wrapping-reader 'quote) :quote))

(def ^:private read-deref-remembering-loc
  (wrap-read-fn-remembering-loc (wrapping-reader 'clojure.core/deref) :deref))

(def ^:private read-var-remembering-loc
  (wrap-read-fn-remembering-loc (wrapping-reader 'var) :var-quote))

(defn wrap-coll-read-fn-remembering-locs [read-coll-fn kind]
  (fn [reader initch]
    (let [v (read-coll-fn reader initch)]
      (swap! saved-forms-atom conj
             (merge (meta v) {:kind kind, :form v}))
      v)))

(def ^:private read-list-remembering-loc
  (wrap-coll-read-fn-remembering-locs read-list :list))

(def ^:private read-vector-remembering-loc
  (wrap-coll-read-fn-remembering-locs read-vector :vector))

(def ^:private read-map-remembering-loc
  (wrap-coll-read-fn-remembering-locs read-map :map))

(def ^:private read-set-remembering-loc
  (wrap-coll-read-fn-remembering-locs read-set :set))

(defn- macros [ch]
  (case ch
    \" read-string-remembering-loc
    \: read-keyword-remembering-loc
    \; read-comment-remembering-contents
    \' read-quote-remembering-loc
    \@ read-deref-remembering-loc
    \^ read-meta-remembering-loc
    \` read-syntax-quote ;;(wrapping-reader 'syntax-quote)
    \~ read-unquote
    \( read-list-remembering-loc
    \) read-unmatched-delimiter
    \[ read-vector-remembering-loc
    \] read-unmatched-delimiter
    \{ read-map-remembering-loc
    \} read-unmatched-delimiter
    \\ read-char-remembering-loc
    \% read-arg-remembering-loc
    \# read-dispatch
    nil))

(defn- dispatch-macros [ch]
  (case ch
    \^ read-meta                ;deprecated
    \' read-var-remembering-loc
    \( read-fn-remembering-loc
    \= read-eval
    \{ read-set-remembering-loc
    \< (throwing-reader "Unreadable form")
    \" read-regex-remembering-loc  ;; column info includes surrounding " but not the leading #
    \! read-comment-remembering-contents
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

(def read-tagged-remembering-loc
  (wrap-read-fn-remembering-loc read-tagged :tagged))

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
              (number-literal? reader ch) (read-number-remembering-loc reader
                                                                       ch)
              :else (let [f (macros ch)]
                      (if f
                        (let [res (f reader ch)]
                          (if (identical? res reader)
                            (recur)
                            res))
                        (read-symbol-remembering-loc reader ch)))))))
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

(defn read-form-locs
  "Same as read, except return a vector of 2 values.  The first is the
return value from read.  The second is a sequence of maps, each
describing an expression read and its starting and ending location in
the input.  This has only been tested by calling it with a
source-logging-push-back-reader, although it may work with other
reader types, too."
  [& args]
  ;; At least for now, clear saved-forms-atom at the beginning, since
  ;; calling read can have the side effect of appending things to it,
  ;; without clearing it at the end.  That is a memory leak waiting to
  ;; happen if it is not corrected, and someone calls read but not
  ;; this function.
  (reset! saved-forms-atom [])
  (try
    (let [v (apply read args)]
      [v @saved-forms-atom])
    (finally
      ;; Clear this out in a finally block, in case the read call
      ;; above throws an exception.
      (reset! saved-forms-atom []))))

(defn- cmp-forms-key [a]
  ;; Make :coll-end-marker items always come before anything else that
  ;; has the same :line and :column, since another value may start at
  ;; the same line/column that a collection ends, e.g. the "[3 4]" in
  ;; "[[1 2][3 4]]" has the same :line and :column as the :end-line
  ;; and :end-column of "[1 2]".
  [(:line a) (:column a) (if (= :coll-end-marker (:kind a)) 0 1)])

(defn- comment-form? [f]
  (= :comment (:kind f)))

(defn- first-ci [[_ form-locs]]
  (let [fs (drop-while (complement comment-form?) form-locs)
        [comments remaining] (split-with comment-form? fs)]
    (when (and (seq comments) (seq remaining))
;;      (println (format "dbg: comments=%s"
;;                       (mapv :form comments)))
;;      (println (format "     (first remaining)=%s"
;;                       (first remaining)))
      [(assoc (first remaining)
         :comments (mapv :form comments))
       (rest remaining)])))

(defn comment-block-info
  "Take a sequence of form-locs as returned by read-form-locs, and
return a sequence of maps describing each block of consecutive
comments in the Clojure code read, and the expression that immediately
follows the comment block."
  [form-locs]
  ;; Add :coll-end-marker values with :line and :column of where
  ;; collections end in the source code.  These are useful for
  ;; ensuring that any comments after the last item in a collection,
  ;; but before the ending delimiter, are not associated with any
  ;; forms.
  (let [form-locs (mapcat (fn [info]
                            (if (#{:list :vector :map :set} (:kind info))
                              [info
                               (assoc info
                                 :kind :coll-end-marker
                                 :line (:end-line info)
                                 :column (:end-column info))]
                              [info]))
                          form-locs)
        form-locs (sort-by cmp-forms-key form-locs)]
    (->> (iterate first-ci [nil form-locs])
         (take-while identity)
         (map first)
         (remove #(= :coll-end-marker (:kind %)))
         ;; remove the initial nil
         rest)))
