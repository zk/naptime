(ns naptime.dev
  (:require [naptime.web :as web]
            [naptime.worker :as worker]
            [nsfw.server :as server]
            [somnium.congomongo :as mon]))

(mon/mongo! :db :naptime)

(defonce s (server/make (var web/routes) :port 8080 :max-threads 20))

(server/restart s)

(worker/run-join!)
