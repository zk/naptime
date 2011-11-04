(ns naptime.test.worker
  (:use naptime.worker :reload)
  (:use clojure.test
        [naptime.web :only (schedule!)])
  (:require [naptime.env :as env]
            [somnium.congomongo :as mon]))

;; # Integration Tests

;; Requires a running MongoDB instance

(defmacro test-mongo [& body]
  `(binding [env/jobs-coll :test-jobs
             env/job-logs-coll :test-job-logs]
     (mon/drop-coll! env/jobs-coll)
     (mon/drop-coll! env/jobs-logs-coll)
     ~@body
     (mon/drop-coll! env/jobs-coll)
     (mon/drop-coll! env/jobs-logs-coll)))

(deftest ^:integration test-clear-locks
  (test-mongo
   (binding [env/lock-timeout -1000]
     (schedule! "http://foo.com" 1000)
     (fetch-and-lock-next-job!)
     (clear-expired-locks!)
     (is (= 0 (-> (mon/fetch env/jobs-coll :where {:locked true})
                  count))))))
