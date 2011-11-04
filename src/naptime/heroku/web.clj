(ns naptime.heroku.web
  "Used by heroku to launch the web interface.  Also starts a worker
   run loop, because we might as well do some work in this process if
   it's running anyway."
  (:use [naptime.env :only (env setup-mongo!)])
  (:require [naptime.web :as web]
            [naptime.worker :as worker]
            [nsfw.server :as server]
            [somnium.congomongo :as mon]))

(def port (Integer/parseInt (env :port "8080")))
(def max-web-threads (Integer/parseInt (env :web-max-threads "20")))
(def web-worker-max-capacity (Integer/parseInt (env :web-worker-max-capacity "10")))

(defn print-web-init []
  (println "*** Web Starting ***")
  (println "  Port:" port)
  (println "  Max Web Threads:" max-web-threads)
  (println "  Max Worker Threads: " web-worker-max-capacity)
  (println "********************")
  (println))

(defonce s (server/make (var web/routes) :port port :max-threads max-web-threads))

(defn -main []
  (setup-mongo! "naptime" "localhost" "27017")
  (print-web-init)
  (server/restart s)
  (worker/run-join! :max-capacity web-worker-max-capacity))

