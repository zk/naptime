(ns naptime.heroku.web
  (:require [naptime.web :as web]
            [nsfw.server :as server]
            [somnium.congomongo :as mon]))

(mon/mongo! :db :naptime)

(defonce s (server/make (var web/routes) :port 8080 :max-threads 20))

(server/restart s)
