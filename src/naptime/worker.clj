(ns naptime.worker
  "Pulls jobs out of the database that are scheduled to be
   run (next-update < current-time), runs the job, and updates
   next-update to be current-time + period."
  (:require [naptime.http-client :as http]
            [somnium.congomongo :as mon])
  (:import [org.apache.http.conn ConnectTimeoutException]
           [java.net SocketTimeoutException]
           [java.net UnknownHostException]))

(defn fetch-and-lock-next-job!
  "Grabs the next job that's scheduled to be run. Atomically
  locks job on fetch."
  []
  (mon/fetch-and-modify
   :jobs
   {:locked false
    :next-update {:$lte (System/currentTimeMillis)}}
   {:$set {:locked true}}
   :sort {:next-update -1}
   :upsert? false
   :return-new? true))

(defn unlock-job!
  "Unlocks a job."
  [job]
  (mon/fetch-and-modify
   :jobs
   {:_id (:_id job)}
   {:$set {:locked false}}
   :upsert? false
   :return-new? true))

;; With-next-job needs to handle future stuff too.

(defn set-next-update!
  "next update time = last update time + period"
  [job]
  (mon/fetch-and-modify
   :jobs
   {:_id (:_id job)}
   ;; change skew characteristics here
   {:$set {:next-update (+ (if (= 0 (:next-update job))
                             (System/currentTimeMillis)
                             (:next-update job))
                           (:period job))}}
   :upsert? false
   :return-new? true))

(defn with-next-job
  "Passes next job (if available) to `f`. Handles locking / unlocking
  of the job."
  [used-capacity-atom max-capacity f]
  (when (< @used-capacity-atom max-capacity)
    (let [job (fetch-and-lock-next-job!)]
      (when job
        (swap! used-capacity-atom inc)
        (future
          (try
            (f job)
            (finally
             (swap! used-capacity-atom dec)
             (set-next-update! job)
             (unlock-job! job))))))))


(defn log-job!
  "Log interesting info about the job."
  [wid endpoint period start-lag response-status request-time]
  (println wid
           endpoint
           period
           request-time
           response-status
           start-lag)
  (mon/insert! :job-logs {:worker-id wid
                          :endpoint endpoint
                          :period period
                          :start-lag start-lag
                          :request-time request-time
                          :response-status response-status
                          :timestamp (System/currentTimeMillis)}))

(defn log-worker!
  "Log interesting information about the worker."
  [wid used-capacity max-capacity]
  #_(println wid used-capacity)
  (mon/insert! :worker-logs {:worker-id wid
                             :used-capacity used-capacity
                             :max-capacity max-capacity
                             :timestamp (System/currentTimeMillis)}))

(defn update-job-status! [job status]
  (mon/fetch-and-modify :jobs
                        {:_id (:_id job)}
                        {:$set {:status status}}
                        :upsert? false))

(defn execute-job
  "Connect to HTTP endpoint with connection and response timeouts."
  [job connect-timeout response-timeout]
  (try
    (-> job
        :endpoint
        (http/get {:conn-timeout connect-timeout
                   :socket-timeout response-timeout})
        :status
        str)
    (catch ConnectTimeoutException e
      "Connect Timeout")
    (catch SocketTimeoutException e "Response Timeout")
    (catch UnknownHostException e "Unknown Host")
    (catch Exception e "Unknown Error")))

(defn unix-ts []
  (System/currentTimeMillis))

(defn run-loop! [worker-id
                 used-capacity-atom
                 max-capacity
                 connect-timeout
                 response-timeout]
  (log-worker! worker-id @used-capacity-atom max-capacity)
  (with-next-job used-capacity-atom max-capacity
    (fn [job]
      (let [start (unix-ts)
            start-lag (- start (:next-update job))
            status (execute-job job connect-timeout response-timeout)
            request-time (- (unix-ts) start)]
        (log-job! worker-id
                  (:endpoint job)
                  (:period job)
                  start-lag
                  status
                  request-time)
        (update-job-status! job status)))))

(defn run-join!
  "Continuously pull and run work. Options are:
   * `:worker-id` -- Unique string to identify this worker.
   * `:used-capacity-atom` -- Atom which hold the number of
     running HTTP requests.
   * `:max-capacity` -- Max number of concurrent HTTP requests.
   * `:run-loop-sleep` -- Sleep time per run loop iteration.
   * `:connect-timeout` -- Endpoint connection timeout (ms).
   * `:response-timeout` -- Timeout for recvng response."
  [& opts]
  (let [opts (apply hash-map opts)
        worker-id (or (:worker-id opts) (str (java.util.UUID/randomUUID)))
        used-capacity-atom (or (:used-capacity-atom opts) (atom 0))
        max-capacity (or (:max-capacity opts) 20)
        run-loop-sleep (or (:run-loop-sleep opts) 10)
        connect-timeout (or (:connect-timeout opts) 2000)
        response-timeout (or (:response-timeout opts) 2000)]
    (reset! used-capacity-atom 0)
    (while true
      (run-loop! worker-id used-capacity-atom max-capacity connect-timeout response-timeout)
      (Thread/sleep run-loop-sleep))))



