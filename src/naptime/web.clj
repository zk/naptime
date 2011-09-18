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


;;;;;;;;;;;;;;;;;
;; Persistence ;;
;;;;;;;;;;;;;;;;;

(defn schedule! [endpoint period]
  (mon/fetch-and-modify :jobs
                        {:endpoint endpoint}
                        {:endpoint endpoint :period period :next-update 0 :locked false}
                        :upsert? true
                        :return-new? true))

(defn unschedule! [endpoint]
  (mon/destroy! :jobs {:endpoint endpoint}))


;;;;;;;;;;;;;
;; Helpers ;;
;;;;;;;;;;;;;

(defn url-encode [s]
  (try
    (java.net.URLEncoder/encode s)
    (catch Exception e nil)))

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


;;;;;;;;;;;;;;;
;; Templates ;;
;;;;;;;;;;;;;;;

(def page-css
  (css
   [:* {:padding :0px
        :margin :0px}]
   [:h1 {:font-family "Helvetica"
         :margin-top "80px"
         :margin-bottom "30px"}]
   [:body {:font-family "Georgia"
           :text-align "center"}]
   [:div.jobs {:width "600px"
               :margin-left "auto"
               :margin-right "auto"
               :border "solid #ccc 1px"
               :padding "20px"
               :font-size "12pt"
               :border-spacing "0px"
               :margin-bottom "30px"}]
   [:div.jobs:hover {:border "solid #999 1px"}]
   [:table {:width "100%"}]
   [:tr {:padding "0px"
         :margin "0px"
         :border "none"}]
   [:td {:padding "4px"}]
   [:td.endpoint {:text-align "left"
                  :padding-left "10px"}]
   [:td.status {:color "green"}]
   ["td.status .warning" {:color "red"}]
   [:td.period {:text-align "center"
                :width "55px"
                :color "#777"}]
   [:td.about {:text-align "center"
               :width "115px"}]
   [:a.about {:text-decoration "none"
              :color "#88f"}]
   [:a.about:hover {:text-decoration "none"
                    :color "blue"}]
   [:div.create {:border "solid #ccc 1px"
                 :width "600px"
                 :margin-left "auto"
                 :margin-right "auto"
                 :padding "20px"}]
   [:div.create:hover {:border "solid #999 1px"}]
   [:form {:padding "0px"
           :margin "0px"}]
   [:input.text {:border "solid #999 1px"
                 :font-size "9pt"}]
   [:input.endpoint {:width "250px"
                     :margin-right "138px"}]
   [:input.period {:width "75px"
                   :margin-right "10px"}]))

(defn render-jobs [jobs]
  (html
   [:div {:class "jobs"}
    [:table 
     (map #(let [endpoint (:endpoint %)
                 period (:period %)
                 status (:status %)]
             [:tr
              [:td {:class "endpoint"} endpoint]
              [:td {:class "status"}
               (if (= "200" status)
                 status
                 [:span {:class "warning"}
                  status])]
              [:td {:class "period"} period]
              [:td {:class "about"}
               [:a {:class "about"
                    :href (str "/" (url-encode endpoint))} "about"]]])
          jobs)]]))

(defn create-or-update-form []
  (html
   [:div {:class "create"}
    [:form {:method "GET"
            :action "/create"}
     [:input {:type "text"
              :name "endpoint"
              :class "text endpoint"}]
     [:input {:type "text"
              :name "period"
              :class "text period"}]
     [:input {:type "submit"
              :name "create"
              :value "new / update"}]]]))

(defn index [jobs]
  (html
   (doctype :html5)
   [:html
    [:head page-css]
    [:body
     [:h1 "Hooray, it's naptime!"]
     (render-jobs jobs)
     (create-or-update-form)]]))



;;;;;;;;;;;;;;;
;; Responses ;;
;;;;;;;;;;;;;;;

(defn error-response [endpoint period]
  (-> (response (str "Sorry! Bad arguments -- {:endpoint " endpoint " :period " period "}"))
      (status 422)
      (header "Content-Type" "text/html")))

(defn read [{:keys [endpoint]}]
  (render :text (mon/fetch-one :jobs :where {:endpoint endpoint})))

(defn create [{:keys [endpoint period]}]
  (if (and endpoint period)
    (do (schedule! endpoint period)
        (-> (response (format "Ok! Scheduled to hit %s every %f seconds." endpoint (double (/ period 1000))))
            (status 200)))
    (error-response endpoint period)))

(defn delete [{:keys [endpoint period]}]
  (if endpoint
    (do (unschedule! endpoint)
        (response (format "Ok! endpoint %s is unscheduled!"
                          endpoint)))
    (error-response endpoint period)))

(defn about [endpoint]
  (fn [req]
    (render :text (str (mon/fetch-one :jobs :where {:endpoint endpoint})))))


;;;;;;;;;;;;;;;;;;;;;;;;;
;; Middleware / Routes ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-period [p]
  (try
    (Long/parseLong p)
    (catch Exception e nil)))

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
   ["delete"] delete
   [endpoint] (about endpoint)))

;;;

