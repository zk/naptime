(ns naptime.worker
  (:require [naptime.http-client :as http]
            [somnium.congomongo :as mon]))

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

;;
;; !!! The unlocking and setting of next-update needs to
;;     happend in the future.  Current impl allows multiple runs
;;     if request cycle takes longer than period.
;;
;;     Refactor this ASAP.
;;

(defn with-next-job
  "Passes next job (possibly nil) to `f`. Handles locking / unlocking of the job."
  [f]
  (let [job (fetch-and-lock-next-job!)]
    (try
      (f job)
      (finally
       (when job
         (mon/fetch-and-modify
          :jobs
          {:_id (:_id job)}
          ;; change skew characteristics here
          {:$set {:next-update (+ (if (= 0 (:next-update job))
                                    (System/currentTimeMillis)
                                    (:next-update job))
                                  (:period job))}}
          :upsert? false
          :return-new? true)
         (unlock-job! job))))))

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


;; refactor me!
(defn run-loop! [worker-id used-capacity-atom max-capacity]
  (log-worker! worker-id @used-capacity-atom max-capacity)
  (if (< @used-capacity-atom max-capacity)
    (do
      (swap! used-capacity-atom inc)
      (with-next-job
        (fn [job]
          (future
            (try
              (when job
                (let [start (System/currentTimeMillis)
                      delta (- start (:next-update job))
                      status (try
                               (str (:status (http/get (:endpoint job))))
                               (catch java.net.UnknownHostException e "Unknown Host")
                               (catch Exception e "Unknown Error"))]
                  (log-job! worker-id
                            (:endpoint job)
                            (:period job)
                            delta
                            status
                            (- (System/currentTimeMillis) start))
                  (mon/fetch-and-modify :jobs
                                        {:_id (:_id job)}
                                        {:$set {:status status}}
                                        :upsert? false)))
              (finally
               (swap! used-capacity-atom dec)))))))))

(defn run-join!
  "Continuously pull and run work. Options are:
   * `:worker-id` -- Unique string to identify this worker.
   * `:used-capacity-atom` -- Atom which hold the number of
     running HTTP requests.
   * `:max-capacity` -- Max number of concurrent HTTP requests.
   * `:run-loop-sleep` -- Sleep time per run loop iteration."
  [& opts]
  (let [opts (apply hash-map opts)
        worker-id (or (:worker-id opts) (java.util.UUID/randomUUID))
        used-capacity-atom (or (:used-capacity-atom opts) (atom 0))
        max-capacity (or (:max-capacity opts) 20)
        run-loop-sleep (or (:run-loop-sleep opts) 10)]
    (reset! used-capacity-atom 0)
    (while true
      (run-loop! worker-id used-capacity-atom max-capacity)
      (Thread/sleep run-loop-sleep))))



