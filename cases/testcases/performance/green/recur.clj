(ns testcases.performance.green.recur
  "Like `testcases.performance.green.recur` but adds a `long`` call
  to address the recur warning."
  (:require
   [clojure.string :as string]))

(def ^:const vlq-base-shift 5)
(def ^:const vlq-base (bit-shift-left 1 vlq-base-shift))
(def ^:const vlq-base-mask (dec vlq-base))
(def ^:const vlq-continuation-bit vlq-base)

(defn to-vlq-signed [v]
  (if (neg? v)
    (inc (bit-shift-left (- v) 1))
    (+ (bit-shift-left v 1) 0)))

(defn from-vlq-signed [v]
  (let [neg? (= (bit-and v 1) 1)
        shifted (bit-shift-right v 1)]
    (if neg?
      (- shifted)
      shifted)))

(defn decode [^String s]
  (let [l (.length s)]
    (loop [i 0 result 0 shift 0]
      (when (>= i l)
        (throw (Error. "Expected more digits in base 64 VLQ value.")))
      (let [digit (rand-int 10)]
        (let [i (inc i)
              continuation? (pos? (bit-and digit vlq-continuation-bit))
              digit (bit-and digit vlq-base-mask)
              result (+ result (bit-shift-left digit shift))
              shift (long (+ shift vlq-base-shift))]
          (if continuation?
            (recur i result shift)
            (lazy-seq
             (cons (from-vlq-signed result)
                   (let [s (.substring s i)]
                     (when-not (string/blank? s)
                       (decode s)))))))))))
