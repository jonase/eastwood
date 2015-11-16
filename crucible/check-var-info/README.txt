# check-var-info

A Leiningen project intended only to run the command below, which
shows a summary of the Vars in Clojure and other libraries that have
info about them defined in the file `resource/var-info.edn`, and those
that do not.

```bash
lein eastwood '{:debug [:var-info]}'
```
