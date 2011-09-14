(ns naptime.core
  (:import [java.util Timer TimerTask]))

(defn- timer-task-proxy
  "Creates a TimerTask that executes `f`."
  [f]
  (proxy [TimerTask] []
    (run []
      (f))))

(def *jobs* (atom {}))

(defn add-job [jobs period f]
  (assoc jobs period
         (conj (get jobs period #{})
               f)))

(defn jobs-for [jobs period]
  (get jobs period))

(add-job {} 1000 #(println "hi"))


(defn schedule [jobs period f]
  (swap! jobs add-job period f))


(def timer (Timer.))

(.schedule timer (timer-task-proxy #(println "hi!!!!!!")) (long 1000))





