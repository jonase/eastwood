(ns testcases.constant-test.some-thread-last.green)

(some->> ["https.proxyPort" "http.proxyPort"]
         (some #(System/getProperty %))
         Integer/parseInt)
