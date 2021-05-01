(disable-warning
 {:linter :wrong-tag
  :if-inside-macroexpansion-of '#{unrelated.ns/another-macro}
  :reason "Support a deftest"})
