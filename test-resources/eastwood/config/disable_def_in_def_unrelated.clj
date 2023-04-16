(disable-warning
 {:linter :def-in-def
  :if-inside-macroexpansion-of '#{unrelated.namespace/my-macro}
  :reason "Support a deftest"})
