(disable-warning
 {:linter :def-in-def
  :if-inside-macroexpansion-of '#{testcases.def-in-def.red3/my-macro}
  :reason "Support a deftest"})
