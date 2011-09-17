(ns naptime.heroku.worker
  (:require [naptime.worker :as w]
            [somnium.congomongo :as mon]))

;; pull from config

(mon/mongo! :db :naptime)
(mon/add-index! :jobs [:next-update])

(def ^{:doc "Max request threads"} *max-capacity* 20)
(def ^{:doc "Sleep time per run loop iteration"} *sleep-time* 10)

(def *used-capacity* (atom 0))

(defn run! []
  (while true
    (w/run-loop! *used-capacity* *max-capacity*)
    (Thread/sleep *sleep-time*)))

(run!)

(println @*used-capacity*)


#_(doseq [n (range 10)]
  (mon/insert! :jobs {:endpoint "http://google.com"
                      :period 5000
                      :next-update 0
                      :locked false}))

