(ns testcases.unhinted-reflective-call.unused-foreign-reflective-code
  "A ns that merely `require`s a ns that is supposed to emit reflection warnings at compiler time."
  (:require
   [reflection-example.core]))
