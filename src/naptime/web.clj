(ns naptime.web
  (:use [hiccup core [page-helpers :only (doctype)]]
        [nsfw render middleware]
        ring.util.response
        [ring.middleware params keyword-params])
  (:require [naptime.core :as core]
            [net.cgrand.moustache :as mous]
            [nsfw.server :as server]
            [somnium.congomongo :as mon])
  (:import [java.util Timer]))

(defn schedule! [endpoint period]
  (mon/fetch-and-modify :jobs
                        {:endpoint endpoint}
                        {:endpoint endpoint :period period :last-update 0}
                        :upsert? true
                        :return-new? true))

(defn unschedule! [endpoint]
  (mon/destroy! :jobs {:endpoint endpoint}))

;;;

(defn css-rule [rule]
  (let [sels (reverse (rest (reverse rule)))
        props (last rule)]
    (str (apply str (interpose " " (map name sels)))
         "{" (apply str (map #(str (name (key %)) ":" (val %) ";") props)) "}")))

(defn css
  "Quick and dirty dsl for inline css rules, similar to hiccup."
  [& rules]
  (html [:style {:type "text/css"}
         (apply str (map css-rule rules))]))

(def page-css
  (css
   [:* {:padding :0px
        :margin :0px}]
   [:body {:font-family "arial"
           :text-align "center"}]
   [:table {:width "400px"
            :margin-left "auto"
            :margin-right "auto"
            :border "solid #ccc 1px"
            :padding "10px;"}]
   [:td.endpoint {:text-align "left"}]
   [:td.period {:text-align "right"}]))

(defn render-jobs [jobs]
  (html
   [:table 
    (map #(vector :tr
                  [:td {:class "endpoint"} (:endpoint %)]
                  [:td {:class "period"} (:period %)]) jobs)]))

(defn index [jobs]
  (html
   (doctype :html5)
   [:html
    [:head page-css]
    [:body
     [:h1 "Hooray, it's naptime!"]
     (render-jobs jobs)]]))

(defn parse-period [p]
  (try
    (Long/parseLong p)
    (catch Exception e nil)))

(defn error-response [endpoint period]
  (-> (response (str "Sorry! Bad arguments -- {:endpoint " endpoint " :period " period "}"))
      (status 422)
      (header "Content-Type" "text/html")))

(defn read [{:keys [endpoint]}]
  (render :text (mon/fetch-one :jobs :where {:endpoint endpoint})))

(defn create [{:keys [endpoint period]}]
  (if (and endpoint period)
    (do (render :text (schedule! endpoint period))
        (-> (response (format "Ok! Scheduled to hit %s every %f seconds." endpoint (double (/ period 1000))))
            (status 200)))
    (error-response endpoint period)))

(defn delete [{:keys [endpoint period]}]
  (if endpoint
    (do (unschedule! endpoint)
        (response (format "Ok! endpoint %s is unscheduled!"
                          endpoint)))
    (error-response endpoint period)))

(defn wrap-args [handler]
  (fn [req]
    (handler
     (assoc req
       :endpoint (-> req :params :endpoint)
       :period (parse-period (-> req :params :period))))))

(def routes
  (mous/app
   wrap-stacktrace
   wrap-params
   wrap-keyword-params
   wrap-args
   [""] (fn [r]
          (-> (response (index (mon/fetch :jobs)))
              (content-type "text/html")
              (status 200)))
   ["read"] read
   ["create"] create
   ["delete"] delete))

;;;

