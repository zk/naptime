(ns naptime.heroku.worker
  (:require [naptime.worker :as w]
            [somnium.congomongo :as mon]))

;; pull from config

(mon/mongo! :db :naptime)
(mon/add-index! :jobs [:next-update])

(try
  (mon/create-collection! :job-logs :capped true :size (* 1024 1024 10))
  (catch Exception e
    (println "job-logs collection already exists.")))

(try
  (mon/create-collection! :worker-logs :capped true :size (* 1024 1024 10))
  (catch Exception e
    (println "worker-logs collection already exists.")))


(def ^{:doc "Max request threads"} *max-capacity* 20)
(def ^{:doc "Sleep time per run loop iteration"} *sleep-time* 10)

(def *used-capacity* (atom 0))

(defn run! [worker-id]
  (while true
    (w/run-loop! worker-id *used-capacity* *max-capacity*)
    (Thread/sleep *sleep-time*)))

(run! (str (java.util.UUID/randomUUID)))

#_(doseq [n (range 10)]
  (mon/insert! :jobs {:endpoint "http://google.com"
                      :period 5000
                      :next-update 0
                      :locked false}))

