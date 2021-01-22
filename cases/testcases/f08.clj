(ns testcases.f08
  "Testcase for issue 307. It uses a dependency with an imported var"
  (:require
   [testcases.imported-var :as imported-var]))

(imported-var/join "z")

