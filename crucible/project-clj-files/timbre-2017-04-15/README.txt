Oct 9 2017: timbre latest version fails 'lein check' because of lack
of several classes for the 5 namespaces named as exceptions in the
lint.sh script.  It can run tests with:

    lein with-profile +test test

and

    lein with-profile +test eastwood

works.  I customized project.clj to include dependencies on projects
needed by the different timbre appenders.
