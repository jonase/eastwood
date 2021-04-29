(ns testcases.speced-def-example
  "Exercises the `speced.def` library"
  (:require
   [clojure.spec.alpha :as spec]
   [nedap.speced.def :as speced]
   [clojure.string :as string]))

(spec/def ::foo int?)

(speced/defn ^pos? foo []
  1)

(speced/defn ^string? foo2 []
  "")

(speced/defn bar [^pos? x]
  x)

(speced/defn bar2 [^string? x]
  x)

(speced/defn baz [{:keys [^pos? x]}]
  x)

(speced/defn baz2 [{:keys [^string? x]}]
  x)

(speced/defn ^String string1 []
  "")

(speced/defn string2 ^String []
  "")

(speced/defn string3 [{:keys [^String a]}]
  "")

(speced/defn ^::foo kw1 [])

(speced/defn kw2 [^::foo x])

(speced/defn kw3 [{:keys [^::foo x]}])

(speced/defprotocol Prot
  ""
  (^::foo method1 [this] "")
  (^String method2 [this] "")
  (^pos? method3 [this] "")
  (^string? method4 [this] "")
  (method5 [this ^::foo x] "")
  (method6 [this ^String x] "")
  (method7 [this ^pos? x] "")
  (method8 [this ^string? x] ""))

(defn uses-letfn []
  (speced/letfn [(^pos? inner-foo []
                  1)

                 (inner-bar [^pos? x]
                   x)

                 (inner-baz [{:keys [^pos? x]}]
                   x)

                 (inner-bar2 [^string? x]
                   x)

                 (inner-baz2 [{:keys [^string? x]}]
                   x)

                 (^String inner-string1 []
                  "")

                 (inner-string2 ^String []
                   "")

                 (inner-string3 [{:keys [^String a]}]
                   "")

                 (^::foo inner-kw1 [])

                 (inner-kw2 [^::foo x])

                 (inner-kw3 [{:keys [^::foo x]}])]
    42))

(defn uses-fn []
  (let [inner-foo (speced/fn ^pos? []
                    1)

        inner-bar (speced/fn [^pos? x]
                    x)

        inner-baz (speced/fn [{:keys [^pos? x]}]
                    x)

        inner-foo2 (speced/fn ^string? []
                     1)

        inner-bar2 (speced/fn [^string? x]
                     x)

        inner-baz2 (speced/fn [{:keys [^string? x]}]
                     x)

        inner-string1 (speced/fn ^String []
                        "")

        inner-string2 (speced/fn ^String []
                        "")

        inner-string3 (speced/fn [{:keys [^String a]}]
                        "")

        inner-kw1 (speced/fn ^::foo [])

        inner-kw2 (speced/fn [^::foo x])

        inner-kw3 (speced/fn [{:keys [^::foo x]}])]
    42))

(defn uses-let []
  (speced/let [^pos? a               1
               ^string? aa           ""
               ^::foo b              1
               ^String c             ""
               {:keys [^pos? d]}     {:d 1}
               {:keys [^string? dd]} {:dd ""}
               {:keys [^::foo e]}    {:e 1}
               {:keys [^String f]}   {:f ""}]
    [a b c d e]))

;; a real-world example
(speced/defn ^::speced/nilable ^set? extract-refers [libspec]
  (let [libspec (vec libspec)
        index (some->> libspec
                       (keep-indexed (fn [i x]
                                       (when (#{:refer} x)
                                         i)))
                       (first)
                       (inc))
        refers (when index
                 (get libspec index))]
    (when (coll? refers)
      (set refers))))

;; another real-world example
(speced/defn ^boolean? contains-dynamic-assertions?
  [{{{ast-form :form} :ast} :wrong-pre-post
    msg                     :msg}]
  (let [[_fn* fn-tails] (if (coll? ast-form)
                          ast-form
                          [])
        [_arglist & body] (if (coll? fn-tails)
                            fn-tails
                            [])]
    (->> body
         (some (fn [form]
                 (when (and (coll? form)
                            (= 'clojure.core/assert
                               (first form)))
                   (let [v      (second form)
                         v-name (when (symbol? v)
                                  (name v))]
                     (and v-name
                          (string/includes? msg v-name)
                          (= \*
                             (first v-name)
                             (last v-name)))))))
         (boolean))))
