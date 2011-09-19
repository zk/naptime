(ns naptime.heroku.worker
  (:use [naptime.heroku.env :only (env setup-mongo!)])
  (:require [naptime.worker :as w]
            [somnium.congomongo :as mon]))

;; pull from config

(setup-mongo! "naptime" "localhost" "27017")
(mon/add-index! :jobs [:next-update])

(try
  (mon/create-collection! :job-logs :capped true :size (* 1024 1024 4))
  (catch Exception e
    (println "job-logs collection already exists.")))

(try
  (mon/create-collection! :worker-logs :capped true :size (* 1024 1024 4))
  (catch Exception e
    (println "worker-logs collection already exists.")))

(def ^{:doc "Max request threads"}
  *max-capacity* (Integer/parseInt (env :worker-max-capacity "20")))

(def ^{:doc "Sleep time per run loop iteration"}
  *sleep-time* (Integer/parseInt (env :worker-sleep-time "10")))

(defn print-worker-init [worker-id]
  (println "*** Worker Starting ***")
  (println "  Worker ID:" worker-id)
  (println "  Max HTTP Request Threads:" *max-capacity*)
  (println "  Run Loop Sleep Time (ms):" *sleep-time*)
  (println "***********************")
  (println))


(defn run! [worker-id used-capacity]
  (while true
    (w/run-loop! worker-id used-capacity *max-capacity*)
    (Thread/sleep *sleep-time*)))

(defn -main []
  (let [worker-id (str (java.util.UUID/randomUUID))
        used-capacity-atom (atom 0)]
    (print-worker-init worker-id)
    (run! worker-id used-capacity-atom)))

(-main)





