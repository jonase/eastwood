
### `:bad-arglists` - arglists that does not match the actual number of arguments that a function/macro was defined to take

TBD: Give examples of function/macro definitions from Clojure and
other libraries that will cause this warning, and some that will not,
even though they explicitly specify a value for :arglists.

TBD: Perhaps also give this example.  Is it possible for Eastwood to
ever warn about a function defined in this way?  I am guessing it
would not be practical to do so, and whatever :arglists is specified
will never cause a :bad-arglists warning, but verify that.

```clojure
(def
 ^{:tag Boolean
   :doc "Returns false if (pred x) is logical true for every x in
  coll, else true."
   :arglists '([pred coll])
   :added "1.0"}
 not-every? (comp not every?))
```

This linter was created because of the belief that it is better if
the value of `:arglists` for vars accurately represents the number of
arguments that can be used to call the function/macro, as opposed
to some other thing used purely for documentation purposes.

It is true that even Clojure itself does not conform to this
restriction.  For example, the arglists of `defn`, `defmacro`, and
several other macros override `:arglists` for purposes of clearer
documentation.  However, all but these few exceptional macros 

Other facts supporting this belief are:

The value of metadata key :arglists is set automatically by macros
like defn and defmacro.

The Clojure compiler uses these arglists to determine things like
the type of the return value of a function call.

It would be nice if Eastwood (in particular its :wrong-arity
linter) and other Clojure development tools could rely upon
:arglists matching the actual arities of the function or macro that
have been defined.
