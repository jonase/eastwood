(ns eastwood.test.cc-compare)

;; comparison-class throws exceptions for some types that might be
;; useful to include.

(defn comparison-class [x]
  (cond (nil? x) ""
        ;; Lump all numbers together since Clojure's compare can
        ;; compare them all to each other sensibly.
        (number? x) "java.lang.Number"

        ;; sequential? includes lists, conses, vectors, and seqs of
        ;; just about any collection, although it is recommended not
        ;; to use this to compare seqs of unordered collections like
        ;; sets or maps (vectors should be OK). This should be
        ;; everything we would want to compare using cmp-seq-lexi
        ;; below. TBD: Does it leave anything out?  Include anything
        ;; it should not?
        (sequential? x) "clojure.lang.Sequential"

        (set? x) "clojure.lang.IPersistentSet"
        (map? x) "clojure.lang.IPersistentMap"
        (.isArray (class x)) "java.util.Arrays"

        ;; Comparable includes Boolean, Character, String, Clojure
        ;; refs, and many others.
        (instance? Comparable x) (.getName (class x))

        (or (instance? java.lang.Class x)
            (instance? clojure.lang.Var x)) (.getName (class x))

        :else (throw
               (ex-info (format "cc-cmp does not implement comparison of values with class %s"
                                (.getName (class x)))
                        {:value x}))))

(defn cmp-seq-lexi
  "Compare sequences x and y in lexicographic order, using comparator
  cmpf to compare elements from x and y to each other. As a comparator
  function, cmp-seq-lexi returns a negative, 0, or positive integer if x
  is less than, equal to, or greater than y."
  [cmpf x y]
  (loop [x x
         y y]
    (if (seq x)
      (if (seq y)
        (let [c (long (cmpf (first x) (first y)))]
          (if (zero? c)
            (recur (rest x) (rest y))
            c))
        ;; else we reached end of y first, so x > y
        1)
      (if (seq y)
        ;; we reached end of x first, so x < y
        -1
        ;; Sequences contain same elements. x = y
        0))))

;; The same result can be obtained by calling cmp-seq-lexi on two
;; vectors, but this one should allocate less memory comparing
;; vectors.

(defn cmp-vec-lexi
  "Compare vectors x and y in lexicographic order, using comparator
  cmpf to compare elements from x and y to each other. As a comparator
  function, cmp-seq-lexi returns a negative, 0, or positive integer if x
  is less than, equal to, or greater than y.

  It is possible to use cmp-seq-lexi on vectors, too, but this function
  should be faster and allocate less memory than cmp-seq-lexi, but only
  works for vectors."
  [cmpf x y]
  (let [x-len (count x)
        y-len (count y)
        len (min x-len y-len)]
    (loop [i 0]
      (if (== i len)
        ;; If all elements 0..(len-1) are same, shorter vector comes
        ;; first.
        (compare x-len y-len)
        (let [c (long (cmpf (x i) (y i)))]
          (if (zero? c)
            (recur (inc i))
            c))))))

(defn cmp-array-lexi
  "Compare Java arrays x and y in lexicographic order, using
  comparator cmpf to compare elements from x and y to each other. As a
  comparator function, cmp-array-lexi returns a negative, 0, or positive
  integer if x is less than, equal to, or greater than y."
  [cmpf ^"[J" x ^"[J" y]
  (let [x-len (alength x)
        y-len (alength y)
        len (min x-len y-len)]
    (loop [i 0]
      (if (== i len)
        ;; If all elements 0..(len-1) are same, shorter array comes
        ;; first.
        (compare x-len y-len)
        (let [c (long (cmpf (aget x i) (aget y i)))]
          (if (zero? c)
            (recur (inc i))
            c))))))

(defn cc-cmp
  "cc-cmp compares two values x and y. As a comparator, it returns a
  negative, 0, or positive integer if x is less than, equal to, or
  greater than y.

  cc-cmp can compare values of many types, including numbers, strings,
  symbols, keywords, vectors, lists, sequences, sets, maps, records,
  Java arrays, anything that implements the Java Comparable
  interface (including booleans, characters, File, URL, UUID, Clojure
  refs), and nil.

  Unlike the function compare, it sorts vectors in lexicographic order.
  It also sorts lists, sequences, and Java arrays in lexicographic
  order, on which compare throws exceptions.

  Also unlike compare, cc-cmp can compare values of different types to
  each other without throwing an exception, if it can compare them at
  all. Values of different types are sorted relative to each other by a
  string that is the name of their class. Note that all numbers use the
  string \"java.lang.Number\" so that numbers are sorted together,
  rather than separated out by type, and for a similar reason all
  vectors, lists, and sequences use the string
  \"clojure.lang.Sequential\". All sets use the string
  \"clojure.lang.IPersistentSet\", and all maps and records use the
  string \"clojure.lang.IPersistentMap\". All Java arrays use the
  string \"java.util.Arrays\" (that is not the name of any instantiable
  class -- it is simply used for sorting Java arrays versus other
  objects).

  cc-cmp throws an exception if given any type of value not mentioned
  above, e.g. Java arrays."

  [x y]
  (let [x-cls (comparison-class x)
        y-cls (comparison-class y)
        c (compare x-cls y-cls)]
    (cond (not= c 0) c  ;; different classes

          ;; Compare Class instances by their names as converted to
          ;; strings.
          (#{"java.lang.Class" "clojure.lang.Var"} x-cls)
          (compare (str x) (str y))

          ;; Compare sets to each other as sequences, with elements in
          ;; sorted order.
          (= x-cls "clojure.lang.IPersistentSet")
          (cmp-seq-lexi cc-cmp (sort cc-cmp x) (sort cc-cmp y))

          ;; Compare maps to each other as sequences of [key val]
          ;; pairs, with pairs in order sorted by key.
          (= x-cls "clojure.lang.IPersistentMap")
          (cmp-seq-lexi cc-cmp
                        (sort-by key cc-cmp (seq x))
                        (sort-by key cc-cmp (seq y)))

          (= x-cls "java.util.Arrays")
          (cmp-array-lexi cc-cmp x y)

          ;; Make a special check for two vectors, since cmp-vec-lexi
          ;; should allocate less memory comparing them than
          ;; cmp-seq-lexi. Both here and for comparing sequences, we
          ;; must use cc-cmp recursively on the elements, because if
          ;; we used compare we would lose the ability to compare
          ;; elements with different types.
          (and (vector? x) (vector? y)) (cmp-vec-lexi cc-cmp x y)

          ;; This will compare any two sequences, if they are not both
          ;; vectors, e.g. a vector and a list will be compared here.
          (= x-cls "clojure.lang.Sequential")
          (cmp-seq-lexi cc-cmp x y)

          :else (compare x y))))
