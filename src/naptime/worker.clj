(ns naptime.worker
  "Pulls jobs out of the database that are scheduled to be
   run (next-update < current-time), runs the job, and updates
   next-update to be current-time + period."
  (:require [naptime.env :as env]
            [naptime.http-client :as http]
            [somnium.congomongo :as mon])
  (:import [org.apache.http.conn ConnectTimeoutException]
           [java.net SocketTimeoutException]
           [java.net UnknownHostException]))

(defn unix-ts []
  (System/currentTimeMillis))

(defn uuid []
  (str (java.util.UUID/randomUUID)))

(defn fetch-and-lock-next-job!
  "Grabs the next job that's scheduled to be run. Atomically
  locks job on fetch."
  []
  (mon/fetch-and-modify
   env/jobs-coll
   {:locked false :next-update {:$lte (unix-ts)}}
   {:$set {:locked true :lock-expiry (+ (unix-ts) env/lock-timeout)}}
   :sort {:next-update -1}
   :upsert? false
   :return-new? true))

(defn unlock-job! [job]
  (when job
   (mon/fetch-and-modify
    env/jobs-coll
    {:_id (:_id job)}
    {:$set {:locked false
            :lock-expiry nil}}
    :upsert? false
    :return-new? true)))

(defn log-job!
  "Log interesting info about an executed job."
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
                          :timestamp (unix-ts)}))

(defn log-worker!
  "Log interesting information about a worker."
  [wid used-capacity max-capacity]
  #_(println wid used-capacity)
  (mon/insert! :worker-logs {:worker-id wid
                             :used-capacity used-capacity
                             :max-capacity max-capacity
                             :timestamp (unix-ts)}))

(defn update-job-status! [job status]
  (when job
    (mon/fetch-and-modify env/jobs-coll
                          {:_id (:_id job)}
                          {:$set {:status status}}
                          :upsert? false)))

(defn set-next-update!
  "next update time = last update time + period"
  [{:keys [_id next-update period]}]
  (mon/fetch-and-modify
   env/jobs-coll
   {:_id _id}
   ;; change skew characteristics here
   {:$set {:next-update (+ (if (= 0 next-update)
                             (unix-ts)
                             next-update)
                           period)}}
   :upsert? false
   :return-new? true))

(defn clear-expired-locks! []
  (doseq [job (mon/fetch env/jobs-coll
                         :where {:lock-expiry {:$lte (unix-ts)}})]
    (env/log "Lock expired on " job ". Clearing.")
    (unlock-job! job)))

(defn with-next-job
  "Passes next job (if available) to `f`. Handles locking / unlocking
  of the job."
  [used-capacity-atom max-capacity f]
  (when (< @used-capacity-atom max-capacity)
    (when-let [job (fetch-and-lock-next-job!)]
      (swap! used-capacity-atom inc)
      (future (try
                (f job)
                (finally
                 (swap! used-capacity-atom dec)
                 (set-next-update! job)
                 (unlock-job! job)))))))

(defn execute-job
  "Connect to HTTP endpoint with connection and response
  timeouts. Returns a status string based on result of request."
  [{:keys [endpoint period]}]
  (let [connect-timeout (int (Math/floor (* period 0.3)))
        response-timeout (int (Math/floor (* period 0.7)))]
    (try
      (-> endpoint
          (http/get {:conn-timeout connect-timeout
                     :socket-timeout response-timeout})
          :status
          str)
      (catch ConnectTimeoutException e "Connect Timeout")
      (catch SocketTimeoutException e "Response Timeout")
      (catch UnknownHostException e "Unknown Host")
      (catch Exception e "Unknown Error"))))

(defn run-loop! [worker-id
                 used-capacity-atom
                 max-capacity]
  (log-worker! worker-id @used-capacity-atom max-capacity)
  (with-next-job used-capacity-atom max-capacity
    (fn [{:keys [next-update endpoint period] :as job}]
      (let [start        (unix-ts)
            start-lag    (- start next-update)
            status       (execute-job job)
            request-time (- (unix-ts) start)]
        (log-job! worker-id
                  endpoint
                  period
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
   * `:run-loop-sleep` -- Sleep time per run loop iteration."
  [& opts]
  (let [opts (apply hash-map opts)
        worker-id           (get opts :worker-id (uuid))
        used-capacity-atom  (get opts :used-capacity-atom (atom 0))
        max-capacity        (get opts :max-capacity 20)
        run-loop-sleep      (get opts :run-loop-sleep 10)]
    (reset! used-capacity-atom 0)
    (while true
      (run-loop! worker-id used-capacity-atom max-capacity)
      (clear-expired-locks!)
      (Thread/sleep run-loop-sleep))))


