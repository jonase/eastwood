Nov 9 2014:

    lein eastwood '{:namespaces [:source-paths]}'

is required.  It seems that linting any individual test namespace on
its own causes exceptions, most often because of attempting to open
network connections to a server that doesn't exist (or I am running
the tests incorrectly, but I don't want to anyway while linting).
