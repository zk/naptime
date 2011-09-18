(ns naptime.heroku.web
  (:use [naptime.heroku.env :only (env setup-mongo!)])
  (:require [naptime.web :as web]
            [nsfw.server :as server]
            [somnium.congomongo :as mon]))

(setup-mongo! "naptime" "localhost" "27017")

(def port (Integer/parseInt (env :port "8080")))

(def max-threads (Integer/parseInt (env :web-max-threads "20")))

(defn print-web-init []
  (println "*** Web Starting ***")
  (println "  Port:" port)
  (println "  Max Web Threads:" max-threads)
  (println "********************")
  (println))

(defonce s (server/make (var web/routes) :port port :max-threads max-threads))

(defn -main []
  (print-web-init)
  (server/restart s))
