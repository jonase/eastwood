(ns testcases.bytes-class.green2)

(defprotocol ToBytes
  (to-bytes [x]))

;; An usage in the wild: git.io/JnMOT
(extend-protocol ToBytes
  (class (byte-array 0))
  (to-bytes [x] x))

;; ensure that the construct in fact works (else we could be linting something that doesn't work in the first place):
(assert (-> "" .getBytes to-bytes))
