Dec 24 2013: timbre latest version fails 'lein check' because of lack
of android.util.Log class.  It can run tests with:

    lein with-profile +test test

and

    lein with-profile +test eastwood

works.  I customized project.clj to include dependencies on projects
needed by the different timbre appenders.
