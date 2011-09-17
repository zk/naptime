(ns naptime.worker
  (:require [clj-http.client :as http]
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

(defn with-next-job
  "Passes next job (possibly nil) to `f`. Handles locking / unlocking of the job."
  [f]
  (let [job (fetch-and-lock-next-job!)]
    (try
      (f job)
      (finally
       (when job
        (mon/update! :jobs
                     {:_id (:_id job)}
                     ;; change skew characteristics here
                     {:$set {:next-update (+ (if (= 0 (:next-update job))
                                               (System/currentTimeMillis)
                                               (:next-update job))
                                             (:period job))}})
        (unlock-job! job))))))

(defn log-job!
  "Log interesting info about the job."
  [wid endpoint period start-lag request-time]
  (println wid
           endpoint
           period
           request-time
           start-lag)
  (mon/insert! :job-logs {:worker-id wid
                          :endpoint endpoint
                          :period period
                          :start-lag start-lag
                          :request-time request-time
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
                      delta (- start (:next-update job))]
                  (http/get (:endpoint job))
                  (log-job! worker-id
                            (:endpoint job)
                            (:period job)
                            delta
                            (- (System/currentTimeMillis) start))))
              (finally
               (swap! used-capacity-atom dec)))))))))

;;;
