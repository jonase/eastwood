(disable-warning
 {:linter :unused-meta-on-macro
  :if-inside-macroexpansion-of '#{unrelated.ns/let}
  :reason "Support a deftest"})
