(ns naptime.env
  (:require [clojure.string :as str]
            [somnium.congomongo :as mon]))

;; Env vars helpers

(defn to-env-key [kw]
  (-> kw name str/upper-case (str/replace #"-" "_")))

(defn to-keyword [env-key]
  (-> env-key str/lower-case (str/replace #"_" "-") keyword))

(defn system-env [] (into {} (System/getenv)))

(defn env [kw & [default]]
  (let [val (->> kw
                to-env-key
                (get (system-env)))]
    (or val
        default)))

(def lock-timeout (env :worker-lock-timeout 5000))

(def jobs-coll :jobs)

(def job-logs-coll :job-logs)

(defn setup-mongo! [default-db default-host default-port & [default-user default-pass]]
  (mon/mongo! :db (env :mongo-db default-db)
              :host (env :mongo-host default-host)
              :port (Integer/parseInt (env :mongo-port default-port)))
  (let [u (env :mongo-user default-user)
        p (env :mongo-password default-pass)]
    (when (and u p)
      (mon/authenticate u p)))
  (try
    (mon/create-collection! :job-logs :capped true :size (* 1024 1024 4))
    (catch Exception e
      (println "job-logs collection already exists.")))
  (try
    (mon/create-collection! :worker-logs :capped true :size (* 1024 1024 4))
    (catch Exception e
      (println "worker-logs collection already exists."))))

(defn log [& args]
  (println (apply str args)))
