Please don't create "snapshot" releases. Consider instead:

* can you / people perform `lein install`?
* can one gain confidence through better tests?

---

Steps to prepare in making a new release:

Make sure CI is green.

Update changes.md with any differences in behavior.

Places where version number should be updated:

* `resources/EASTWOOD_VERSION`
* README.md
* changes.md (header)

Update the changelog in changes.md

Commit all of those changes.

Tag it with a version tag, e.g.:

    % git tag -a v1.4.2 -m "1.4.2"

'git push' by default does not push tags to the remote server.  To
cause that to happen, use:

    % git push origin --tags

----------------------------------------------------------------------
Then release to Clojars.org via the CI integration.

----------------------------------------------------------------------

