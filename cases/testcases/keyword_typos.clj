(ns testcases.keyword-typos)

;; Just a simple test that the :keyword-typos linter can actually
;; issue warnings.

(def my-records
  [ {:name "John Q. Public", :address "123 Elm St", :occupation "fool"}
    {:name "Jane Doe", :address "678 Vine Rd", :occupation "Accountant"}
    {:name "Rajesh Koothrapali", :address "Los Angeles", :occuption "physicist"}
    ])

;; Issue #163: George Simms pointed out that keywords that differ only
;; in the presence or absence of an initial '_' character are
;; reasonably common when interacting with Datomic.

;; The example below is completely made up.  I have never worked with
;; Datomic before.  It simply contains a pair of keywords that differ
;; only in presence/absence of a leading '_' character.

(def datomic-common-pattern
  [ :contoso/foo :contoso/_foo ])
