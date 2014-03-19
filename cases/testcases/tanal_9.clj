(ns testcases.tanal-9)

(defrecord RecordTest [a b])

(defn mytest []
  (= (RecordTest. 1 2) #testcases.tanal_9.RecordTest{:a 1, :b 2}))
