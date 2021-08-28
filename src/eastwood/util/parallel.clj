(ns eastwood.util.parallel
  "Vendored from https://github.com/nedap/utils.collections. EPL 2.0"
  (:import
   (clojure.lang ITransientCollection)))

(defn divide-by
  "Divides `coll` in `n` parts. The parts can have disparate sizes if the division isn't exact."
  [n coll]
  (let [the-count (count coll)
        seed [(-> the-count double (/ n) Math/floor)
              (rem the-count n)
              []
              coll]
        recipe (iterate (fn [[quotient remainder output input]]
                          (let [chunk-size (+ quotient (if (pos? remainder)
                                                         1
                                                         0))
                                addition (take chunk-size input)
                                result (cond-> output
                                         (seq addition) (conj addition))]
                            [quotient
                             (dec remainder)
                             result
                             (drop chunk-size input)]))
                        seed)
        index (inc n)]
    (-> recipe
        (nth index)
        (nth 2))))

(defn into!
  "Analog to `clojure.core/into`."
  ^ITransientCollection
  [^ITransientCollection a
   ^ITransientCollection b]
  (reduce conj! a (persistent! b)))

(defn partitioning-pmap
  "`clojure.core/pmap` replacement. Avoids creating more threads than necessary for CPU-bound tasks.

  e.g. for a `coll` of 20 items and a 6-core machine, 6 fixed threads are used at most, as opposed to 20 shifting threads."
  [f coll]
  (if-not (seq coll)
    (vec coll)
    (let [cpus (-> (Runtime/getRuntime) .availableProcessors)]
      (->> coll
           (divide-by cpus)
           (pmap (fn [work]
                   (->> work
                        (mapv f)
                        transient)))
           (reduce into!)
           (persistent!)))))
