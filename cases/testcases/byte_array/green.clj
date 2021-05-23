(ns testcases.byte-array.green
  "https://github.com/jonase/eastwood/issues/188")

(def class-byte-array (.getClass (byte-array 0)))
