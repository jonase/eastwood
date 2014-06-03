Steps to prepare in making a new release:

Change any snapshot versions of dependencies in project.clj to
non-snapshot versions.

Redo any unit tests 'lein test' and/or crucible tests, and check for
any changes in behavior.  Compare against output of previous release.
Update changes.md and changes-detailed.md with any differences in
behavior.


Places where version number should be updated:

* project.clj just after jonase/eastwood
* README.md in install instructions, and instructions for developers
* src/eastwood/core.clj var *eastwood-version*
* src/leiningen/eastwood.clj var eastwood-version-string

Update the change log in changes.md

Commit all of those changes.

Tag it with a version tag, e.g.:

    % git tag -a eastwood-0.1.2

I don't put much into the commit comments other than 'Eastwood version
0.1.2'

'git push' by default does not push tags to the remote server.  To
cause that to happen, use:

    % git push origin --tags

Then ask Jonas Enlund to create the release to Clojars.org


When that is complete, then pick the next version number, at least a
temporary one for development purposes, and update it in these places
with -SNAPSHOT appended:

* project.clj just after jonase/eastwood
* README.md instructions for developers (if not already done above)
* src/eastwood/core.clj var *eastwood-version*
* src/leiningen/eastwood.clj var eastwood-version-string
