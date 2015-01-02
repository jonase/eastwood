(ns eastwood.test.commentreader-test
  (:use [clojure.test])
  (:require [eastwood.copieddeps.dep10.clojure.tools.reader :as tr]
            [eastwood.copieddeps.dep10.clojure.tools.reader.reader-types :as rt]
            [eastwood.util :as util]
            [clojure.data :as data]
            [clojure.pprint :as pp]
            [clojure.string :as str]))

(def empty-ordering-comment-info-map
  (util/ordering-map [
                      :comments
                      :form
                      :line
                      :column
                      :end-line
                      :end-column
                      ]))

(defn fixup [comment-info]
  (->> comment-info
       (map #(select-keys % [:form :comments :line :column :end-line :end-column]))
       (map #(into empty-ordering-comment-info-map %))
       (sort-by (juxt :line :column))))

(defn rs-ci [s]
  (let [rdr (rt/source-logging-push-back-reader s)
        [val form-locs] (tr/read-form-locs rdr)
        comment-info (tr/comment-block-info form-locs)]
    [val comment-info]))

(defmacro read-test [comment-info expected-comment-info]
  `(let [actual# (fixup ~comment-info)
         exp# (fixup ~expected-comment-info)
         diffs# (take 2 (data/diff actual# exp#))]
;;     (println (format "dbg: comment-info="))
;;     (pp/pprint ~comment-info)
;;     (println (format "     (fixup comment-info)"))
;;     (pp/pprint actual#)
;;     (println (format "--------------------"))
;;     (if (= diffs# [nil nil])
;;       (println (format "   Result: PASSED")))
     (when (not= diffs# [nil nil])
       (println (format "   Result: FAILED"))
       (println "Pretty-printed diffs between actual and expected results:")
       (pp/pprint diffs#))
     (is (= diffs# [nil nil]))))

;; Functions in tools.reader for macros.  Should have at least one
;; test case for each of these:

;;(defn- macros [ch]
;;  (case ch
;;    \" read-string*               ; done: s3 delim, s5 top-level
;;    \: read-keyword               ; done: s3 delim, s6 top-level
;;    \; read-comment-remembering-contents   ; done: every comment
;;    \' (wrapping-reader 'quote)   ; done: s3 delim, s7 top-level
;;    \@ (wrapping-reader 'clojure.core/deref) ; done: s16 top-level.  Result seems wrong.
;;    \^ read-meta                  ; don: s17 delim, tbd top-level
;;    \` read-syntax-quote          ; tbd
;;    \~ read-unquote               ; tbd
;;    \( read-list                  ; done: s1 delim and top-level
;;    \) read-unmatched-delimiter   ; N/A
;;    \[ read-vector                ; done: s1 delim, s8 top-level
;;    \] read-unmatched-delimiter   ; N/A
;;    \{ read-map                   ; done: s3 delim, s9 top-level
;;    \} read-unmatched-delimiter   ; N/A
;;    \\ read-char*                 ; done: s3 delim, s10 top-level
;;    \% read-arg                   ; done: s15 delim (top-level doesn't make sense)
;;    \# read-dispatch              ; see dispatch-macros below
;;    nil))                         ; done: s1 on symbol foo, s4 top-level
;;            digit, - sign         ; done: s1 delim (7a), s3 delim (negativeint), tbd top-level

;; Also for dispatch-macros:

;; (defn- dispatch-macros [ch]
;;   (case ch
;;     \^ read-meta    ;deprecated  ; tbd
;;     \' (wrapping-reader 'var)    ; done: s18 top-level
;;     \( read-fn                   ; done: s15 top-level
;;     \= read-eval                 ; tbd
;;     \{ read-set                  ; done: s3 delim, s11 top-level
;;     \< (throwing-reader "Unreadable form")  ; tbd
;;     \" read-regex                ; done: s13 delim, s14 top-level
;;     \! read-comment-remembering-contents  ; tbd
;;     \_ read-discard              ; done: s12 top-level - Might want to change result?
;;     nil))         read-tagged    ; done: s20

(deftest comment-reader-tests

  (let [s1 (str/join "\n"
            [";;#1a"
             ";;#1b"
             "(;;defn-a"
             " ;;#defn-b"
             " defn ;;foo-a"
             "      ;;foo-b"
             "      foo"
             " ;;argvec-a"
             " ;;argvec-b"
             " [;;x-a"
             "  ;;x-b"
             "  x;;argvec-end-a"
             "   ;;argvec-end-b"
             "   ]"
             "  ;;body-a"
             "  ;;body-b"
             "  (;;+a"
             "   ;;+b"
             "   + ;;xa"
             "     ;;xb"
             "     x ;;7a"
             "       ;;7b"
             "       7;;)a"
             "        ;;)b"
             "        );;body-end-a"
             "         ;;body-end-b"
             "         )"
             ";;very-end-a"
             ";;very-end-b"
             ])
        [form comment-info] (rs-ci s1)]
    (is (= form '(defn foo [x] (+ x 7))))
    (read-test comment-info
     '#{
        {:comments [";#1a" ";#1b"],
         :form (defn foo [x] (+ x 7)),
         :line 3,
         :column 1,
         :end-line 27,
         :end-column 11}
        {:comments [";defn-a" ";#defn-b"],
         :form defn,
         :line 5,
         :column 2,
         :end-line 5,
         :end-column 6}
        {:comments [";foo-a" ";foo-b"],
         :form foo,
         :line 7,
         :column 7,
         :end-line 7,
         :end-column 10}
        {:comments [";argvec-a" ";argvec-b"],
         :form [x],
         :line 10,
         :column 2,
         :end-line 14,
         :end-column 5}
        {:comments [";x-a" ";x-b"],
         :form x,
         :line 12,
         :column 3,
         :end-line 12,
         :end-column 4}
        {:comments [";+a" ";+b"],
         :form +,
         :line 19,
         :column 4,
         :end-line 19,
         :end-column 5}
        {:comments [";xa" ";xb"],
         :form x,
         :line 21,
         :column 6,
         :end-line 21,
         :end-column 7}
        {:comments [";7a" ";7b"],
         :form 7,
         :line 23,
         :column 8,
         :end-line 23,
         :end-column 9}
        {:comments [";body-a" ";body-b"],
         :form (+ x 7),
         :line 17,
         :column 3,
         :end-line 25,
         :end-column 10}
        }
     ))

  (let [s2 (str/join "\n"
            ["(defn ;; foo comment"
             "  foo"
             "  ;; argvec"
             "  [x]"
             "  ;; body comment #1"
             "  ;;another"
             "  (+ x 7))"
             ])
        [form comment-info] (rs-ci s2)]
    (is (= form '(defn foo [x] (+ x 7))))
    (read-test comment-info
     '#{{:comments ["; foo comment"],
         :form foo,
         :line 2,
         :column 3,
         :end-line 2,
         :end-column 6}
        {:comments ["; argvec"],
         :form [x],
         :line 4,
         :column 3,
         :end-line 4,
         :end-column 6}
        {:comments ["; body comment #1" ";another"],
         :form (+ x 7),
         :line 7,
         :column 3,
         :end-line 7,
         :end-column 10}}
     ))

  (let [s3 (str/join "\n"
            ["(defn foo [x]"
             "  (println"
             "    ;string"
             "    \"hello\""
             "    ;keyword"
             "    :world"
             "    ;quoted"
             "    'sym"
             "    ;char"
             "    \\a"
             "    ;map"
             "    {:b ;negativeint"
             "        -2}"
             "    ;set"
             "    #{5 2 3}"
             "    ))"
             ])
        [form comment-info] (rs-ci s3)]
    (is (= form
           '(defn foo [x] (println "hello" :world 'sym \a {:b -2} #{5 2 3}))))
    (read-test comment-info
     '#{
        {:comments ["string"],
         :form "hello",
         :line 4,
         :column 5,
         :end-line 4,
         :end-column 12}
        {:comments ["keyword"],
         :form :world,
         :line 6,
         :column 5,
         :end-line 6,
         :end-column 11}
        {:comments ["quoted"],
         :form 'sym,
         :line 8,
         :column 5,
         :end-line 8,
         :end-column 9}
        {:comments ["char"],
         :form \a,
         :line 10,
         :column 5,
         :end-line 10,
         :end-column 7}
        {:comments ["map"],
         :form {:b -2},
         :line 12,
         :column 5,
         :end-line 13,
         :end-column 12}
        {:comments ["negativeint"],
         :form -2,
         :line 13,
         :column 9,
         :end-line 13,
         :end-column 11}
        {:comments ["set"],
         :form #{3 2 5},
         :line 15,
         :column 5,
         :end-line 15,
         :end-column 13}
        }))

  (let [s4 (str/join "\n"
            [";#1"
             " foo"])
        [form comment-info] (rs-ci s4)]
    (is (= form 'foo))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form foo,
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 5}
        }))

  (let [s5 (str/join "\n"
            [";#1"
             " \"foo\""])
        [form comment-info] (rs-ci s5)]
    (is (= form "foo"))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form "foo",
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 7}
        }))

  (let [s6 (str/join "\n"
            [";#1"
             " :foo"])
        [form comment-info] (rs-ci s6)]
    (is (= form :foo))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form :foo,
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 6}
        }))

  (let [s7 (str/join "\n"
            [";#1"
             " 'foo"])
        [form comment-info] (rs-ci s7)]
    (is (= form '(quote foo)))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form 'foo,
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 6}
        }))

  (let [s8 (str/join "\n"
            [";#1"
             " [5 6 7]"])
        [form comment-info] (rs-ci s8)]
    (is (= form [5 6 7]))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form [5 6 7],
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 9}
        }))

  (let [s9 (str/join "\n"
            [";#1"
             " {:a 1 :b -2}"])
        [form comment-info] (rs-ci s9)]
    (is (= form {:a 1 :b -2}))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form {:a 1, :b -2},
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 14}
        }))

  (let [s10 (str/join "\n"
             [";#1"
              " \\newline"])
        [form comment-info] (rs-ci s10)]
    (is (= form \newline))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form \newline,
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 10}
        }))

  (let [s11 (str/join "\n"
             [";#1"
              " #{:a 1 :b -2}"])
        [form comment-info] (rs-ci s11)]
    (is (= form #{:a 1 :b -2}))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form #{1 -2 :b :a},
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 15}
        }))

  ;; Note that the #_a form is treated like a comment in this case,
  ;; *and* a comment before it is associated with the commented-out
  ;; form.  It would be good to document the behavior here for those
  ;; who like to use the #_ prefix to comment out forms.
  (let [s12 (str/join "\n"
             [";#1"
              " #_a {:a 1 :b -2}"])
        [form comment-info] (rs-ci s12)]
    (is (= form {:a 1 :b -2}))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form a,
         :line 2,
         :column 4,
         :end-line 2,
         :end-column 5}
        }))

  ;; Clojure regex's are only equal if they also satisfy identical?
  ;; Convert them to normal strings before comparing against expected
  ;; results.
  (let [s13 (str/join "\n"
             [";#1"
              " ( #\"^[0-9]+\" )"])
        [form comment-info] (rs-ci s13)
        stringify-first (fn [f]
                          (cons (str (first f)) (rest f)))
        form (stringify-first form)
        comment-info (set [(update-in (first comment-info)
                                      [:form]
                                      stringify-first)])]
    (is (= form '("^[0-9]+")))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form ("^[0-9]+"),
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 16}
        }))

  (let [s14 (str/join "\n"
             [";#1"
              " #\"^[0-9]+\""])
        [form comment-info] (rs-ci s14)
        stringify-first (fn [f]
                          (cons (str (first f)) (rest f)))
        form (str form)
        comment-info (set [(update-in (first comment-info)
                                      [:form]
                                      str)])]
    (is (= form "^[0-9]+"))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form "^[0-9]+",
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 12}
        }))

  (let [s15 (str/join "\n"
             [";#1"
              " #(+ 7 ;the%"
              "       %)"])
        [form comment-info] (rs-ci s15)
        auto-gen-sym (util/get-in' form [1 0])
        upd (constantly :auto-gen-sym)
        form' (-> form
                  (util/update-in' [1 0] upd)
                  (util/update-in' [2 2] upd))
        comment-info (sort-by (juxt :line :column) comment-info)
        comment-info' (-> comment-info
                          (util/update-in' [0 :form 1 0] upd)
                          (util/update-in' [0 :form 2 2] upd)
                          (util/update-in' [1 :form] upd))]
    (is (= auto-gen-sym (util/get-in' form [2 2])))
    (is (= auto-gen-sym (util/get-in' comment-info [0 :form 1 0])))
    (is (= auto-gen-sym (util/get-in' comment-info [0 :form 2 2])))
    (is (= auto-gen-sym (util/get-in' comment-info [1 :form])))
    (is (= form' '(fn* [:auto-gen-sym] (+ 7 :auto-gen-sym))))
    (read-test comment-info'
     '#{
        {:comments ["the%"],
         :form :auto-gen-sym,
         :line 3,
         :column 8,
         :end-line 3,
         :end-column 9}
        {:comments ["#1"],
         :form (fn* [:auto-gen-sym] (+ 7 :auto-gen-sym)),
         :line 2,
         :column 2,
         :end-line 3,
         :end-column 10}
        }))

  (let [s16 (str/join "\n"
             [";#1"
              " @var"])
        [form comment-info] (rs-ci s16)]
    (is (= form ))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form @var,
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 6}
        }))

  (let [s17 (str/join "\n"
             ["(;#1"
              " ^{:a 1 :b 2} foo"
              " ^{:c 3 :d 4} ;#2"
              "                  bar)"])
        [form comment-info] (rs-ci s17)]
    (is (= form '(foo bar)))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form foo,  ; note this is the symbol foo with the metadata. TBD: Would be best to check that the metadata is correct, but this does not do that.
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 18}
        {:comments ["#2"],
         :form bar,
         :line 4,
         :column 19,
         :end-line 4,
         :end-column 22}
     }))

  (let [s18 (str/join "\n"
             [";#1"
              " #'foo"])
        [form comment-info] (rs-ci s18)]
    (is (= form '(var foo)))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form (var foo),
         :file nil,
         :line 2,
         :column 2,
         :end-line 2,
         :end-column 7}
        }))

  (let [s19 (str/join "\n"
             ["( ;#1"
              "  #'foo )"])
        [form comment-info] (rs-ci s19)]
    (is (= form '((var foo))))
    (read-test comment-info
     '#{
        {:comments ["#1"],
         :form (var foo),
         :line 2,
         :column 3,
         :end-line 2,
         :end-column 8}
        }))

  (let [s20 (str/join "\n"
             ["["
              "  ;-0x042e comment"
              "  -0x042e"
              "  ;-36rCRAZY comment"
              "  -36rCRAZY"
              "  ;0.314e+1 comment"
              "  0.314e+1"
              "  ;true comment"
              "  true"
              "  ;false comment"
              " false"
              "  ;nil comment"
              " nil"
              "  ;\\newline comment"
              " \\newline"
              "  ;:ns/kw comment"
              " :ns/kw"
              "  ;-Infinity comment"
              " -Infinity"
              "  ;char #1 comment"
              "  \\f"
              "  ;char #2 comment"
              "  \\u0194"
              "  ;char #3 comment"
              "  \\x61"
              "  ;char #4 comment"
              "  \\newline"
              "  ;char #5 comment"
              "  \\o377"
              "  ;set comment"
              "  #{8 9}"
              "  ;() comment"
              "  ()"
              "  ;#inst comment"
              "  #inst \"2010-11-12T13:14:15.666\""
              ;;" '^{:meta []} bar  "
              " ]" ])
        [form comment-info] (rs-ci s20)]
    (is (= form [
                 -0x042e
                 -36rCRAZY
                 0.314e+1
                 true
                 false
                 nil
                 \newline
                 :ns/kw
                 Double/NEGATIVE_INFINITY
                 \f
                 \u0194
                 \a   ;; Note: Clojure as of 1.7.0-alpha4 and earlier
                      ;; does not support \x61 syntax.  It treats \x
                      ;; as the character in the string "x".
                 \newline
                 \o377
                 #{8 9}
                 ()
                 #inst "2010-11-12T13:14:15.666"
                 ]))
    (read-test comment-info
      #{
        {:comments ["-0x042e comment"],
         :form -0x042e,
         :line 3,
         :column 3,
         :end-line 3,
         :end-column 10}
        {:comments ["-36rCRAZY comment"],
         :form -36rCRAZY,
         :line 5,
         :column 3,
         :end-line 5,
         :end-column 12}
        {:comments ["0.314e+1 comment"],
         :form 0.314e+1,
         :line 7,
         :column 3,
         :end-line 7,
         :end-column 11}
        {:comments ["true comment"],
         :form true,
         :line 9,
         :column 3,
         :end-line 9,
         :end-column 7}
        {:comments ["false comment"],
         :form false,
         :line 11,
         :column 2,
         :end-line 11,
         :end-column 7}
        {:comments ["nil comment"],
         :form nil,
         :line 13,
         :column 2,
         :end-line 13,
         :end-column 5}
        {:comments ["\\newline comment"],
         :form \newline,
         :line 15,
         :column 2,
         :end-line 15,
         :end-column 10}
        {:comments [":ns/kw comment"],
         :form :ns/kw,
         :line 17,
         :column 2,
         :end-line 17,
         :end-column 8}
        {:comments ["-Infinity comment"],
         :form Double/NEGATIVE_INFINITY,
         :line 19,
         :column 2,
         :end-line 19,
         :end-column 11}
        {:comments ["char #1 comment"],
         :form \f,
         :line 21,
         :column 3,
         :end-line 21,
         :end-column 5}
        {:comments ["char #2 comment"],
         :form \u0194,
         :line 23,
         :column 3,
         :end-line 23,
         :end-column 9}
        {:comments ["char #3 comment"],
         :form \a,
         :line 25,
         :column 3,
         :end-line 25,
         :end-column 7}
        {:comments ["char #4 comment"],
         :form \newline,
         :line 27,
         :column 3,
         :end-line 27,
         :end-column 11}
        {:comments ["char #5 comment"],
         :form \o377,
         :line 29,
         :column 3,
         :end-line 29,
         :end-column 8}
        {:comments ["set comment"],
         :line 31,
         :column 3,
         :end-line 31,
         :end-column 9,
         :form #{9 8}}
        {:comments ["() comment"],
         :line 33,
         :column 3,
         :end-line 33,
         :end-column 5,
         :form ()}
        {:comments ["#inst comment"],
         :form #inst "2010-11-12T13:14:15.666-00:00",
         :line 35,
         :column 3,
         :end-line 35,
         :end-column 34}
        }))

(comment

  (let [s21 (str/join "\n"
             [""])
        [form comment-info] (rs-ci s21)]
    (is (= form ))
    (read-test comment-info
     '#{
        }))
)

  )



(comment

(use 'clojure.pprint)
(require '[eastwood.util :as u])
(require '[eastwood.copieddeps.dep10.clojure.tools.reader.impl.commons :as c] :reload)
(require '[eastwood.copieddeps.dep10.clojure.tools.reader.reader-types :as rt] :reload)
(require '[eastwood.copieddeps.dep10.clojure.tools.reader :as tr] :reload)

(defn r [s] (tr/read (rt/source-logging-push-back-reader s)))
(defn i [] (tr/comment-info))

(def s "(def foo [x] (inc x))")
(def s "(def foo;argvec\n [x] (inc x))")
(def s "(defn ;; foo comment\n foo\n ;; argvec\n [x]\n ;; body comment #1\n ;;another\n (+ x 7))")
(def s ";;#1a\n;;#1b\n(;;defn-a\n;;#defn-b\ndefn ;;foo-a\n;;foo-b\n foo\n ;;argvec-a\n;;argvec-b\n [;;x-a\n;;x-b\nx;;argvec-end-a\n;;argvec-end-b\n]\n ;;body-a\n;;body-b\n (;;+a\n;;+b\n+ ;;xa\n;;xb\nx ;;7a\n;;7b\n7;;)a\n;;)b\n);;body-end-a\n;;body-end-b\n);;very-end-a\n;;very-end-b\n")
(def s ";#1\n ( ;defn\n defn ;argvec\n [ ;x\n x ;argvec-end\n ] ;body-end\n ) ;very-end")
(def s ";#1\n ( ;defn\n defn ;argvec\n [ x ;argvec-end\n ] ;body-end\n ) ;very-end")
(def s ";#1\n ( ;defn\n defn ;argvec\n [ x ] ) ;very-end")
(def s "( defn ;argvec\n [ x ] )")


;; In case there is an exception thrown, and you want to reinitialize
;; the atoms back to their initial state.
(reset! c/saved-comments-atom [[]])    (reset! rt/log-source-depth 0)

(def s ";#1\n ( ;defn\n defn ;argvec\n [ ;x\n x ;argvec-end\n ] ;body-end\n ) ;very-end")

(def s ";#1\n ( ;defn\n defn ;argvec\n [ ;inner-vec\n [ ;x\n x ] ;argvec-end\n ] ;body-end\n ) ;very-end")

(def x (r s)) (def c (i)) (u/pprint-form x)
(pprint c)


)
