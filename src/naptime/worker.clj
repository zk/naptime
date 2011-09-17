(ns naptime.worker
  (:require [clj-http.client :as http]
            [somnium.congomongo :as mon]))

(defn fetch-and-lock-next-job! []
  (mon/fetch-and-modify
   :jobs
   {:locked false
    :next-update {:$lt (System/currentTimeMillis)}}
   {:$set {:locked true}}
   :sort {:next-update -1}
   :upsert? false
   :return-new? true))

(defn unlock-job! [job]
  (mon/fetch-and-modify
   :jobs
   {:_id (:_id job)}
   {:$set {:locked false}}
   :upsert? false
   :return-new? true))

(defn with-next-job [f]
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


;; refactor me!
(defn run-loop! [used-capacity-atom max-capacity]
  (if (< @used-capacity-atom max-capacity)
    (do
      (swap! used-capacity-atom inc)
      (with-next-job
        (fn [job]
          (future
            (try
              (when job
                (println (:endpoint job)
                         (:period job)
                         (- (System/currentTimeMillis)
                            (:next-update job)))
                (http/get (:endpoint job)))
              (finally
               (swap! used-capacity-atom dec)))))))))

;;;
