On this machine:

MacBook Pro 2012 model with SSD drive
Mac OS X 10.9.5
JDK 1.7.0_45
Eastwood 0.2.2

% cd core.matrix-2015-11-12

% time lein with-profile +1.6 do clean, test
[ ... most output deleted ... ]
0 failures, 0 errors.
real	0m35.822s
user	0m50.771s
sys	0m1.624s

% time lein with-profile +1.6 do clean, eastwood
[ ... most output deleted ... ]
== Warnings: 225 (not including reflection warnings)  Exceptions thrown: 1
real	0m40.924s
user	1m2.915s
sys	0m1.700s


% time lein with-profile +1.7 do clean, test
[ ... most output deleted ... ]
0 failures, 0 errors.
real	0m21.325s
user	0m36.770s
sys	0m1.247s

% time lein with-profile +1.7 do clean, eastwood
== Warnings: 225 (not including reflection warnings)  Exceptions thrown: 1
real	0m33.944s
user	0m54.480s
sys	0m1.464s

The 1 exception thrown started like this:

== Linting clojure.core.matrix.impl.ndarray ==
Exception thrown during phase :analyze+eval of linting namespace clojure.core.matrix.impl.ndarray
ClassCastException clojure.lang.Keyword cannot be cast to clojure.lang.IObj
	clojure.core/with-meta--4146 (core.clj:217)
	clojure.core.matrix.impl.ndarray-magic/handle-symbol (form-init3141904029683248386.clj:42)
	clojure.core.matrix.impl.ndarray-magic/handle-forms/fn--21294 (form-init3141904029683248386.clj:76)
	clojure.walk/prewalk (walk.clj:64)
        ... lots more stacktrace output deleted here ...



On next line, +1.8 is Clojure 1.8.0-RC1
% time lein with-profile +1.8 do clean, test
[ ... most output deleted ... ]
0 failures, 0 errors.
real	0m22.157s
user	0m39.009s
sys	0m1.332s

% time lein with-profile +1.8 do clean, eastwood
== Warnings: 225 (not including reflection warnings)  Exceptions thrown: 1
real	0m33.896s
user	0m54.673s
sys	0m1.534s

1 exception stack trace was similar to the one shown with Clojure 1.7
above.
