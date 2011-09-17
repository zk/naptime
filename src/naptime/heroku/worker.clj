(ns naptime.heroku.worker
  (:require [naptime.worker :as w]
            [somnium.congomongo :as mon]))

;; pull from config

(mon/mongo! :db :naptime)
(mon/add-index! :jobs [:last-update])

(def *max-capacity* 20)
(def *sleep-time* 10)

(def *used-capacity* (atom 0))

(defn run! []
  (while true
    (w/run-loop! *used-capacity* *max-capacity*)
    (Thread/sleep *sleep-time*)))

(run!)
