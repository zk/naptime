(ns naptime.core
  (:require [clj-http.client :as http])
  (:import [java.util Timer TimerTask]))

(defn timer-task-proxy
  "Creates a TimerTask that executes `f`."
  [f]
  (proxy [TimerTask] []
    (run []
      f)))

(def timer (Timer.))

(defn make-job [uri period]
  {:uri uri
   :period period})

;; # CRUD Ops

(defn add-job [jobs job]
  (let [period (:period job)]
    (assoc jobs period
           (conj (get jobs period #{})
                 job))))

(defn remove-job [jobs job]
  (let [period (:period job)]
    (assoc jobs period
           (disj (get jobs period #{})
                 job))))

(defn jobs-for [jobs period]
  (get jobs period))

(def *jobs* (atom {}))

(defn schedule! [jobs-atom job]
  (swap! jobs-atom add-job job))

(defn unschedule! [jobs-atom job]
  (swap! jobs-atom remove-job job))

(defn exec! [job]
  (http/get (:uri job)))


(def *timers* (atom {}))

(defn make-timer! [jobs-atom period]
  (timer-task-proxy
   (fn []
     (doseq [job (get @jobs-atom period)]
       (println "exec" job)
       (exec! job)))))

(defn add-timer! [timers jobs-atom period]
  (let [existing (get timers period)]
    (if existing
      timers
      (assoc timers (make-timer jobs-atom period)))))


(schedule! *jobs* {:uri "http://google.com"
                   :period 1000})
(.run (make-timer! *jobs* 1000))
