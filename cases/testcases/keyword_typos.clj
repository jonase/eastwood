(ns testcases.keyword-typos)

;; Just a simple test that the :keyword-typos linter can actually
;; issue warnings.

(def my-records
  [ {:name "John Q. Public", :address "123 Elm St", :occupation "fool"}
    {:name "Jane Doe", :address "678 Vine Rd", :occupation "Accountant"}
    {:name "Rajesh Koothrapali", :address "Los Angeles", :occuption "physicist"}
    ])
