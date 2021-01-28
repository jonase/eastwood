(ns testcases.imported-var
  "mimics a var imported from another namespace. i.e. by potemkin/import-vars"
  (:require [clojure.string :as str]))

(def join
  str/join)

(alter-meta! #'join merge (meta #'str/join))
