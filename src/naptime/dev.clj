(ns naptime.dev
  (:require [naptime.core :as core]
            [nsfw.server :as server]))

(defonce s (server/make (var core/entry-handler) :port 8080 :max-threads 20))




