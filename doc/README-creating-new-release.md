Steps to prepare in making a new release:

Make sure CI is green.

Update changes.md with any differences in behavior.

Places where version number should be updated:

* project.clj just after jonase/eastwood
* README.md in install instructions, and instructions for developers
* changes.md (header)
* src/eastwood/lint.clj var *eastwood-version*
  * (happens automatically)
* src/leiningen/eastwood.clj var eastwood-version-string
  * (happens automatically)

Update the change log in changes.md

Commit all of those changes.

Tag it with a version tag, e.g.:

    % git tag -a eastwood-0.1.2

I don't put much into the commit comments other than 'Eastwood version
0.1.2'

'git push' by default does not push tags to the remote server.  To
cause that to happen, use:

    % git push origin --tags

----------------------------------------------------------------------
Then release to Clojars.org via the CI integration.

----------------------------------------------------------------------

