TBD: What is going on here?  Why does (Math/abs (f2 -3)) cause a
reflection warning, but neither of the other variants does?

```clojure
user=> (clojure-version)
"1.6.0"
user=> (set! *warn-on-reflection* true)
true
user=> (defn ^{:tag 'long} f1 [x] (inc x))
#'user/f1
user=> (Math/abs (f1 -3))
2
user=> (defn ^{:tag 'long} f2 [^long x] (inc x))
#'user/f2
user=> (Math/abs (f2 -3))
Reflection warning, NO_SOURCE_PATH:6:1 - call to static method abs on java.lang.Math can't be resolved (argument types: java.lang.Object).
2
user=> (defn ^{:tag 'long} f3 ^long [^long x] (inc x))
#'user/f3
user=> (Math/abs (f3 -3))
2
```


If you give a primitive type tag such as `^long` or `^double` on a
Var, the compiler _does not_ create a function returning a primitive
value.  It will not issue any warning or error, either.

Worse, in some situations the compiler will throw an exception if it
attempts to use a primitive type tag on a Var, e.g. when a
non-function value is used as an argument in a Java interop call.

Eastwood will issue a `:wrong-tag` warning for any Var with a type tag
that is a primitive type, i.e. one of: `byte`, `short`, `int`, `long`,
`float`, `double`, `boolean`, `char`, `void`.  Also if it is any of
the array-of-primitive types, e.g. `bytes`, `shorts`, etc.

```clojure
user=> (clojure-version)
"1.6.0"
user=> (set! *warn-on-reflection* true)
true

;; ^long type tag *on the Var f1* does not cause the function to
;; return a primitive long.  It returns an Object.  Thus the
;; reflection when attempting the Java interop call to Math/abs, even
;; if we try giving a type tag in the Java interop call.

user=> (defn ^long f1 [^long x] (inc x))
#'user/f1
user=> (Math/abs (f1 -3))
Reflection warning, NO_SOURCE_PATH:4:1 - call to static method abs on java.lang.Math can't be resolved (argument types: java.lang.Object).
2
user=> (Math/abs ^long (f1 -3))
Reflection warning, NO_SOURCE_PATH:5:1 - call to static method abs on java.lang.Math can't be resolved (argument types: java.lang.Object).
2

;; It doesn't help to try to use ^{:tag 'long} instead of ^long

user=> (defn ^{:tag 'long} f2 [^long x] (inc x))
#'user/f2
user=> (Math/abs (f2 -3))
Reflection warning, NO_SOURCE_PATH:7:1 - call to static method abs on java.lang.Math can't be resolved (argument types: java.lang.Object).
2
user=> (Math/abs ^long (f2 -3))
Reflection warning, NO_SOURCE_PATH:8:1 - call to static method abs on java.lang.Math can't be resolved (argument types: java.lang.Object).
2

;; No type tag on a Var with a non-function value also gives
;; reflection warning trying to call Math/abs, unless we type hint it
;; in the interop call.

user=> (def n1 -2)
#'user/n1
user=> (Math/abs n1)
Reflection warning, NO_SOURCE_PATH:10:1 - call to static method abs on java.lang.Math can't be resolved (argument types: unknown).
2
user=> (Math/abs ^long n1)
2

;; Trying to use the type tag ^long on such a Var gives an exception
;; when compiling the Java interop call, not merely a reflection
;; warning.

user=> (def ^long n2 -2)
#'user/n2
user=> (Math/abs n2)
CompilerException java.lang.IllegalArgumentException: Unable to resolve classname: clojure.core$long@1461578, compiling:(NO_SOURCE_PATH:14:1) 

;; Using a type tag of ^{:tag 'long} helps in this case, even though
;; it does not for Vars naming functions.

user=> (def ^{:tag 'long} n3 -2)
#'user/n3
user=> (Math/abs n3)
2
```

## Type tags on function arguments

If you want to define a function that takes primitive arguments,
without boxing (i.e. wrapping them in a Java object like Long or
Double), you can do so only for long and double types.  The compiler
gives an error if you attempt to use any primitive type other than
long or double.

```clojure
;; ok
(defn f [^long x] (inc x))

;; Error during compilation: only long and double primitives are
;; supported.
(defn f [^int x] (inc x))
```

You can also use other classes as type tags of arguments, but they
will always compile to arguments of type Object.  The type tags can
still be used by the Clojure compiler to avoid reflection on Java
interop calls within the body of the function.

## Type tags on function return values

If you want to define a function that returns a primitive value
without boxing, again you can do so only for long and double types.
Note the position of the type tag: it is just before the argument
vector.  Why?  Because Clojure functions can be defined with multiple
arities, and each arity can have its own independent return type tag.
The compiler gives an error if you attempt to use any primitive type
other than long or double.

```clojure
;; ok
(defn f ^long [^long x] (inc x))

;; also ok
(defn f
  (^long [x]
    (inc x))
  (^double [x y]
    (+ x y)))

;; Clojure accepts this definition without errors or warnings, but
;; does *not* use the type tag to affect the function return type.
;; See section "Type tags on Vars" below.
(defn ^long f [^long x] (inc x))

;; Error during compilation: only long and double primitives are
;; supported.
(defn f ^float [x] (inc x))
```

Again, you may use other classes as type tags for a function return
value, but they will always compile to a Java method with a return
value of type Object.  The type tag can still be used by the Clojure
compiler to avoid reflection on Java interop calls where the function
is called.

Note: It is recommended to use a fully qualified class name for
function return values.  For example, use the type tag
`^java.util.List` rather than just `^List`.  If you use the non-full
name `List` as a return type tag, and the function is called from
another namespace where you do not import the class `java.util.List`,
the compiler will throw an exception that it cannot resolve the class
name `List`.  This is not necessary for classes in `java.lang` such as
`Long`, `Object`, etc., since those are auto-imported by Clojure.

Ticket [CLJ-1232](http://dev.clojure.org/jira/browse/CLJ-1232) has
been filed for Clojure about this behavior.  It is not yet clear
whether any change will be made due to that ticket.  Eastwood tries to
provide a helpful error message about what you should do to modify
your code if it finds this in your code.


## Type tags on Vars

A type tag on the Var, i.e. specified just before the name being
defined, is used by Clojure to avoid reflection in Java interop calls.
Here is an example REPL session demonstrating this:

```clojure
user=> *clojure-version*
{:major 1, :minor 6, :incremental 0, :qualifier nil}
user=> (set! *warn-on-reflection* true)
true
user=> (import '(java.util LinkedList))
java.util.LinkedList

;; l1 has no type tag, thus the compiler uses reflection to invoke the
;; size method on it.
user=> (def l1 (LinkedList.))
#'user/l1
user=> (.size l1)
Reflection warning, NO_SOURCE_PATH:5:1 - reference to field size can't be resolved.
0

;; l2 has a type tag, enabling the compiler to pick the right method
;; and avoid reflection.
user=> (def ^LinkedList l2 (LinkedList.))
#'user/l2
user=> (.size l2)
0

;; Similarly for tags on Vars that name functions
user=> (defn f1 [] (LinkedList.))
#'user/f1
user=> (.size (f1))
Reflection warning, NO_SOURCE_PATH:9:1 - reference to field size can't be resolved.
0
user=> (defn ^LinkedList f2 [] (LinkedList.))
#'user/f2
user=> (.size (f2))
0

;; As in the previous section, you can also put the type tag for
;; functions on the argument vector.
user=> (defn f3 ^LinkedList [] (LinkedList.))
#'user/f3
user=> (.size (f3))
0
```

However, note that using type tags on Vars only works for
non-primitive Java types.  The example REPL session below shows that
it does not work for a `^double` type tag, but this applies equally to
all other primitive type tags, including those for arrays of
primitives like `^doubles`.

```clojure
user=> *clojure-version*
{:major 1, :minor 6, :incremental 0, :qualifier nil}
user=> (set! *warn-on-reflection* true)
true

;; n1 has no type tag, so Math/abs call uses reflection, unless the
;; type tag is given inside the Math/abs call.

user=> (def n1 2.0)
#'user/n1
user=> (Math/abs n1)
Reflection warning, NO_SOURCE_PATH:4:1 - call to static method abs on java.lang.Math can't be resolved (argument types: unknown).
2.0
user=> (Math/abs ^double n1)
2.0

;; We try to use a ^double type tag on n2, but get a compiler error
;; unless the Java interop call overrides it.
user=> (def ^double n2 2.0)
#'user/n2
user=> (Math/abs n2)
CompilerException java.lang.IllegalArgumentException: Unable to resolve classname: clojure.core$double@54d9be5a, compiling:(NO_SOURCE_PATH:7:1) 
user=> (Math/abs ^double n2)
2.0

;; Similarly for type tags on Vars naming functions.  f1 has no type
;; tag, so Math/abs call uses reflection unless the type tag is given
;; inside the Java interop call.

user=> (defn f1 [] 2.0)
#'user/f1
user=> (Math/abs (f1))
Reflection warning, NO_SOURCE_PATH:10:1 - call to static method abs on java.lang.Math can't be resolved (argument types: unknown).
2.0
user=> (Math/abs ^double (f1))
2.0

;; Attempting to put a ^double type tag on f2 gives a compiler error
;; on the Java interop call, unless a type tag inside the call
;; overrides it.

user=> (defn ^double f2 [] 2.0)
#'user/f2
user=> (Math/abs (f2))
CompilerException java.lang.IllegalArgumentException: Unable to resolve classname: clojure.core$double@54d9be5a, compiling:(NO_SOURCE_PATH:12:1) 
user=> (Math/abs ^double (f2))
2.0

;; For functions, this is the correct method to get a long or double
;; primitive return type.  Using the type tag inside the Java interop
;; call here can be done, but is not needed to avoid reflection.

user=> (defn f3 ^double [] 2.0)
#'user/f3
user=> (Math/abs (f3))
2.0
user=> (Math/abs ^double (f3))
2.0
```





(defn ^nothere myfn (^rettype1 [^arg1tag arg1] body-for-1arg) (^rettype2 [^arg1tag arg1 ^arg2tag arg2] body-for-2-args))


Transcript of #clojure IRC discussion on Apr 6 2014 about what tags
are useful in Clojure, and which are not.

After that are examples of expressions in Eastwood crucible projects
that throw exceptions when analyzed with tools.analyzer(.jvm) version
0.3.0, but not with version 0.2.2.

andyf_: I was curious whether there was a relatively short set of rules I could use in Eastwood to warn about badly placed :tag metadata
andyf_: I don't have such a set of rules in my head yet, and was hoping you could help me bypass a big chunk of guessing, experimentation, and reading code, if you had such knowledge ready at hand.
Bronsa: andyf_: the only place where I saw people put metadata where it actually isn't doing what they think it's doing is in def symbols
andyf_: Do you know of any good reasons to put tag metadata on symbols being def'd?
andyf_: i.e. does the compiler itself ever pay attention to it there?
Bronsa: andyf_: maybe, I don't remember, let me check
andyf_: So the short rule might look something like (def ^nothere myfn ^ret-tag-here [^arg1tag arg1 ^arg2tag arg2 ...] fn-body)
andyf_: Oops, that should be defn, not def
andyf_: and more generally (defn ^nothere myfn (^rettype1 [^arg1tag arg1] body-for-1arg) (^rettype2 [^arg1tag arg1 ^arg2tag arg2] body-for-2-args))
Bronsa: andyf_: actually the compiler uses tag attached on Vars too
Bronsa: andyf_: the only issue really is if you're tagging primitives
Bronsa: it works fine for objects
andyf_: So those code snippets I showed are what should be used for primitive type tags, and the only ones supported are ^long and ^double ?
Bronsa: yeah
Bronsa: if you tag with a primitive tag the Var rather than the arglist there are going to be two problems
andyf_: And if they are non-primitive tags, they will work in the same places as for primitive ones, but a non-primitive tag on a Var "works the same way" as if it is on the arg vector?
Bronsa: - the Var meta is going to be evaluated so you'll get :tag #<the-primitive-function> instead of the tag
andyf_: Yeah, I've seen that case plenty of times now
Bronsa: - even if you quote the tag, the compiler will not optimize the function with a prim interface for the return type
Bronsa: e.g. (defn ^{:tag 'long} x [^long a] a) will make x a IFn$LO rather than IFn$LL
Bronsa: so you get boxing on the return type
Bronsa: if you do (defn x ^long [^long a] a) however you get what you'd expect
andyf_: And non-primitive type tags are only used for avoiding reflection in Java interop calls, either in the function body for type tags on args, or wherever the function return value is used for the return type tag?
Bronsa: andyf_: yes
Bronsa: well, and to hint at the correct method too
andyf_: OK, I think I'm down to 1 question, and then I go off to experiment with these variations for my own confirmation: A non-primitive tag on the Var, or on the arg vector, work the same way, or nearly so?
Bronsa: say you have a class A which implements B and C, and you have a method foo that takes a B or a C, a type hint to B in a function that returns an A makes a call to foo resolve to the call to foo(B)
Bronsa: andyf_: that should be correct, I can't think of a case where that would be false
andyf_: So it sounds like the main cases where it would help if Eastwood gave warnings are for attempted primitive tags that are neither long nor double, anywhere they appear, or for ^long or ^double on Vars.
Bronsa: andyf_: well
andyf_: Oh, I guess ^int and such are still useful in a Java interop call?
Bronsa: right
Bronsa: I was just going to say that
andyf_: Are primitive hints ever used in a let or loop binding?
Bronsa: andyf_: you can do that
andyf_: By which I mean a code snippet like (let [^double x 0.0] let-body)
amalloy: can you? i think you have to write (let [x (double 0.0)] body)
amalloy: or, well, 0.0 is a double literal, so you don't have to. but like (long 0)
Bronsa: you can't do that specifically, but I believe you can do (let [^double x (something)] ..)
Bronsa: amalloy: looks like you can indeed
Bronsa: amalloy: (fn [] (let [^int a (Integer. 1)] (clojure.lang.RT/box a))) compiles to http://sprunge.us/OBjJ

----------------------------------------------------------------------
Contents at link http://sprunge.us/OBjJ

Compiled from "test.clj"
public final class user$fn__3 extends clojure.lang.AFunction {
  public static final java.lang.Object const__0;

  public static {};
    Code:
       0: lconst_1      
       1: invokestatic  #15                 // Method java/lang/Long.valueOf:(J)Ljava/lang/Long;
       4: putstatic     #17                 // Field const__0:Ljava/lang/Object;
       7: return        

  public user$fn__3();
    Code:
       0: aload_0       
       1: invokespecial #20                 // Method clojure/lang/AFunction."<init>":()V
       4: return        

  public java.lang.Object invoke();
    Code:
       0: new           #24                 // class java/lang/Integer
       3: dup           
       4: lconst_1      
       5: invokestatic  #30                 // Method clojure/lang/RT.intCast:(J)I
       8: invokespecial #33                 // Method java/lang/Integer."<init>":(I)V
      11: astore_1      
      12: aload_1       
      13: aconst_null   
      14: astore_1      
      15: checkcast     #35                 // class java/lang/Number
      18: invokestatic  #38                 // Method clojure/lang/RT.intCast:(Ljava/lang/Object;)I
      21: invokestatic  #42                 // Method clojure/lang/RT.box:(I)Ljava/lang/Number;
      24: areturn       
}
----------------------------------------------------------------------

Bronsa: while (fn [] (let [a (Integer. 1)] (clojure.lang.RT/box a))) compiles to http://sprunge.us/LQag

----------------------------------------------------------------------
Contents at link http://sprunge.us/LQag

Compiled from "test.clj"
public final class user$fn__5 extends clojure.lang.AFunction {
  public static final java.lang.Object const__0;

  public static {};
    Code:
       0: lconst_1      
       1: invokestatic  #15                 // Method java/lang/Long.valueOf:(J)Ljava/lang/Long;
       4: putstatic     #17                 // Field const__0:Ljava/lang/Object;
       7: return        

  public user$fn__5();
    Code:
       0: aload_0       
       1: invokespecial #20                 // Method clojure/lang/AFunction."<init>":()V
       4: return        

  public java.lang.Object invoke();
    Code:
       0: new           #24                 // class java/lang/Integer
       3: dup           
       4: lconst_1      
       5: invokestatic  #30                 // Method clojure/lang/RT.intCast:(J)I
       8: invokespecial #33                 // Method java/lang/Integer."<init>":(I)V
      11: astore_1      
      12: aload_1       
      13: aconst_null   
      14: astore_1      
      15: invokestatic  #37                 // Method clojure/lang/RT.box:(Ljava/lang/Object;)Ljava/lang/Object;
      18: areturn       
}
----------------------------------------------------------------------

Link from Bronsa to the change that checks for and throws these "Wrong
tag" exceptions:

https://github.com/clojure/tools.analyzer.jvm/commit/829c996198ecbc7023e8bd3f0757e830cd6b475e

Examples of expressions in Eastwood crucible projects that throw
exceptions when analyzed with tools.analyzer(.jvm) version 0.3.0, but
not with version 0.2.2.


----------------------------------------
File: cassaforte-2014-03-11/src/clojure/clojurewerkz/cassaforte/bytes.clj

(defn #^bytes to-bytes
  [^ByteBuffer byte-buffer]
  (let [bytes (byte-array (.remaining byte-buffer))]
    (.get byte-buffer bytes 0 (count bytes))
    bytes))

t.a(.j) 0.3.0 exception message:

Wrong tag: clojure.core$bytes@6747009b in def: to-bytes


----------------------------------------
File: cassaforte-2014-03-11/src/clojure/clojurewerkz/cassaforte/uuids.clj

(defn ^long unix-timestamp
  "Return the unix timestamp contained by the provided time-based UUID."
  [^UUID uuid]
  (UUIDs/unixTimestamp uuid))

t.a(.j) 0.3.0 exception message:

Wrong tag: clojure.core$long@483af9f5 in def: unix-timestamp

----------------------------------------
File: chash-2014-03-11/src/clojure/clojurewerkz/chash/ring.clj

(def ^{:const true :tag 'long}
  ring-top (dec (Math/pow 2 160)))

t.a(.j) 0.3.0 exception message:

Wrong tag: long in def: ring-top

----------------------------------------
