(ns naptime.worker
  (:require [clj-http.client :as http]
            [somnium.congomongo :as mon]))

(def query-js
  "function() {
var t = arguments[0];
var res =  db.jobs.findOne({$where: \"(\" + t + \" - this['last-update']) > this.period\"});
if(res) {
  var lu = res['last-update'];
  res['last-update'] = t;
  db.jobs.save(res);
  return [lu, res];
}

return null;
}")

(defn next-job []
  (let [res (mon/server-eval query-js (System/currentTimeMillis))]
    (when res
      [(long (first res))
       (assoc (second res)
         :last-update (long (:last-update (second res))))])))


;; refactor me!
(defn run-loop! [used-capacity-atom max-capacity]
  (if (< @used-capacity-atom max-capacity)
    (do (swap! used-capacity-atom inc)
        (let [[prev-last-update job] (next-job)]
          (try
            (future
              (try
                (println "error delta:" (- (:last-update job)
                                           (+ prev-last-update (:period job))))
                (http/get (:endpoint job))
                (finally
                 (swap! used-capacity-atom dec))))
            (catch Exception e
              (println "set job last update back to" prev-last-update)))))))


;;;


