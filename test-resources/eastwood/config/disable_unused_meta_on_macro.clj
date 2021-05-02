(disable-warning
 {:linter :unused-meta-on-macro
  :if-inside-macroexpansion-of '#{clojure.core/let}
  :reason "Support a deftest"})
