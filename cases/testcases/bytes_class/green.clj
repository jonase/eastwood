(ns testcases.bytes-class.green)

(defprotocol ToBytes
  (to-bytes [x]))

(extend-protocol ToBytes
  (Class/forName "[B")
  (to-bytes [x] x))

;; ensure that the construct in fact works (else we could be linting something that doesn't work in the first place):
(assert (-> "" .getBytes to-bytes))
