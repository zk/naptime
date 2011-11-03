(ns naptime.heroku.worker
  (:use [naptime.heroku.env :only (env setup-mongo!)])
  (:require [naptime.worker :as worker]
            [somnium.congomongo :as mon]))

;; pull from config

(setup-mongo! "naptime" "localhost" "27017")
(mon/add-index! :jobs [:next-update])

(def ^{:doc "Max request threads"}
  max-capacity (Integer/parseInt (env :worker-max-capacity "20")))

(def ^{:doc "Sleep time per run loop iteration"}
  sleep-time (Integer/parseInt (env :worker-sleep-time "10")))

(def ^{:doc "Connect timeout in ms"}
  connect-timeout (Integer/parseInt (env :worker-connect-timeout "2000")))

(def ^{:doc "Response timeout in ms"}
  response-timeout (Integer/parseInt (env :response-timeout "2000")))

(defn print-worker-init [worker-id]
  (println "*** Worker Starting ***")
  (println "  Worker ID:" worker-id)
  (println "  Max HTTP Request Threads:" max-capacity)
  (println "  Run Loop Sleep Time (ms):" sleep-time)
  (println "  HTTP Connect Timeout: " connect-timeout)
  (println "  HTTP Response Timeout: " response-timeout)
  (println "***********************")
  (println))


(defn -main []
  (let [worker-id (str (java.util.UUID/randomUUID))]
    (print-worker-init worker-id)
    (worker/run-join! :max-capacity max-capacity
                      :run-loop-sleep sleep-time
                      :connect-timeout connect-timeout
                      :response-timeout response-timeout)))
