(ns naptime.core
  (:require [clj-http.client :as http])
  (:import [java.util Timer TimerTask]))

(defn timer-task-proxy
  "Creates a TimerTask that executes `f`."
  [f]
  (proxy [TimerTask] []
    (run []
      (f))))

(defn make-job [endpoint period]
  {:endpoint endpoint
   :period period})

;; refactor vv

(defn exec! [job]
  #_(http/get (:endpoint job)))

(defn on-run-timer [jobs-atom period]
  (fn []
    (doseq [job (get @jobs-atom period)]
      (println "jobs" @jobs-atom)
      (println "exec" job)
      (exec! job))))

(defn make-timer! [timer jobs-atom period]
  (let [timer-task (timer-task-proxy (on-run-timer jobs-atom period))]
    (.schedule timer timer-task (long 0) (long period))
    timer-task))

(defn add-timer! [timer timers-atom jobs-atom period]
  (let [existing (get @timers-atom period)]
    (if existing
      @timers-atom
      (swap! timers-atom
             assoc
             period
             (make-timer! timer jobs-atom period)))))

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

(defn make-scheduler [timer timers-atom jobs-atom job]
  (swap! jobs-atom add-job job)
  (add-timer! timer timers-atom jobs-atom (:period job)))

(defn make-unscheduler [jobs-atom job]
  (swap! jobs-atom remove-job job))









