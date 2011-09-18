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

(defn goog-sparkline [w h y-min y-max color data]
  (str "https://chart.googleapis.com/chart?"
       "chxt=y&"
       "chds=" y-min "," y-max "&"
       "chxr=0," y-min "," y-max "&"
       "chs="
       w
       "x"
       h
       "&cht=lc&"
       "chco=" color "&"
       "chd=t:"
       (->> data
            (interpose ",")
            (apply str))))


;;;;;;;;;;;;;;;
;; Templates ;;
;;;;;;;;;;;;;;;

(def about-page-css
  (css
   [:.about-job {:text-align "left"
                 :width "600px"
                 :margin-left "auto"
                 :margin-right "auto"
                 :position "relative"
                 :margin-top "50px"}]
   [".about-job .status" {:position "absolute"
                          :top "0px"
                          :right "0px"
                          :color "green"
                          :font-size "14pt"}]
   [".about-job .status .label" {:color "#999"}]
   [".about-job .warning" {:color "red"}]
   [".about-job .period" {:position "absolute"
                          :top "22px"
                          :right "0px"
                          :font-size "14pt"}]
   [".about-job .period .label" {:color "#999"}]
   [".about-job .charts" {:margin-top "60px"
                          :margin-bottom "60px"}]
   [".about-job .chart" {:text-align "center"}]
   [".about-job .lag-chart" {:float "left"}]
   [".about-job .request-chart" {:float "right"}]))

(def main-css
  (css
   [:* {:padding :0px
        :margin :0px}]
   [:.clear {:clear "both"}]
   [:h1 {:font-family "Helvetica"
         :margin-top "80px"
         :margin-bottom "30px"}]
   [:body {:font-family "Georgia"
           :text-align "center"}]
   [:em {:font-weight "bold"
         :font-style "normal"}]
   [:a {:text-decoration "none"
        :color "#88f"}]
   [:a:hover {:text-decoration "none"
              :color "blue"}]
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
   [:div.jobs-controls {:text-align "right"
                        :width "640px"
                        :margin-right "auto"
                        :margin-left "auto"
                        :padding-right "3px"}]
   [:div.create {:border "solid #ccc 1px"
                 :width "600px"
                 :margin-left "auto"
                 :margin-right "auto"
                 :padding "20px"
                 :margin-bottom "30px"}]
   [:div.create:hover {:border "solid #999 1px"}]
   [:form {:padding "0px"
           :margin "0px"}]
   [:input.text {:border "solid #999 1px"
                 :font-size "9pt"}]
   [:input.endpoint {:width "250px"
                     :margin-right "138px"}]
   [:input.period {:width "75px"
                   :margin-right "10px"}]
   [:.instructions {:color "#aaa"}]
   [:.workers {:margin-bottom "30px"}]
   [:.worker.chart {:margin-bottom "20px"}]))

(def page-css (str main-css about-page-css))

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
                    :href (str "/" (url-encode endpoint))} "about"]
               "&nbsp;&nbsp;&nbsp;"
               [:a {:class "delete"
                    :href (str "/delete?endpoint=" (url-encode endpoint))}
                "delete"]]])
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

(defn render-jobs-controls []
  (html
   [:div {:class "jobs-controls"}
    [:a {:href "/delete-all"} "clear all"]]))

(defn main-tpl [& body]
  (html
   (doctype :html5)
   [:html
    [:head
     [:title "naptime! -- REST based URL hook scheduler."]
     page-css]
    [:body
     [:h1 "Hooray, it's naptime!"]
     body
     [:div {:class "instructions"}
      "refresh this page for the latest status info."]]]))

(defn calc-load [worker]
  (float (* (/ (:used-capacity worker)
               (:max-capacity worker))
            100)))

(defn render-worker [worker]
  (html
   [:div {:class "chart worker"}
    [:img {:src (goog-sparkline 500
                                100
                                0
                                100
                                "FF3333"
                                (map calc-load (:logs worker)))
           :width 500
           :height 100}]
    [:br]
    "Load for worker "
    [:em (:worker-id worker)]
    " (%)"]))

(defn render-workers [workers]
  (html [:div {:class "workers"}
         (map render-worker workers)]))

(defn index [jobs workers]
  (main-tpl (render-jobs-controls)
            (render-jobs jobs)
            (create-or-update-form)
            (render-workers workers)))

(defn about-tpl [{:keys [endpoint status period]}
                 logs]
  (let [start-lags (map :start-lag logs)
        request-times (map :request-time logs)]
    (main-tpl
     [:div {:class "about-job"}
      [:h2 endpoint]
      [:span {:class "status"}
       [:span {:class "label"} "last status: "]
       [:span {:class (when-not (= "200" status) "warning")}
        status]]
      [:span {:class "period"}
       [:span {:class "label"} "period (ms): "]
       period]
      [:div {:class "charts"}
       [:div {:class "chart lag-chart"}
        [:img {:src (goog-sparkline 275
                                    50
                                    (apply min start-lags)
                                    (apply max start-lags)
                                    "0077CC"
                                    start-lags)
               :width 275
               :height 50}]
        [:br]
        "Start Time Lag (ms)"]
       [:div {:class "chart request-chart"}
        [:img {:src (goog-sparkline 275
                                    50
                                    (apply min request-times)
                                    (apply max request-times)
                                    "FF3333"
                                    request-times)
               :width 275
               :height 50}
         [:br]
         "Request Time (ms)"]]
       [:div {:class "clear"}]]])))

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
        (redirect "/"))
    (error-response endpoint period)))

(defn delete [{:keys [endpoint period]}]
  (if endpoint
    (do (unschedule! endpoint)
        (redirect "/"))))

(defn about [endpoint]
  (fn [req]
    (render about-tpl
            (mon/fetch-one :jobs :where {:endpoint endpoint})
            (mon/fetch :job-logs
                       :where {:endpoint endpoint}
                       :sort {:timestamp -1}
                       :limit 100))))

(defn delete-all [req]
  (mon/drop-coll! :jobs)
  (redirect "/"))


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

(defn worker-info []
  (let [worker-ids (mon/distinct-values :worker-logs "worker-id"
                                        :where {:timestamp {:$gt (- (System/currentTimeMillis)
                                                                    1000)}})]
    (->> worker-ids
         (map #(hash-map :worker-id %
                         :logs (mon/fetch :worker-logs
                                          :where {:worker-id %}
                                          :sort {:timestamp -1}
                                          :limit 100)))
         (sort-by :worker-id))))

(def routes
  (mous/app
   wrap-stacktrace
   wrap-params
   wrap-keyword-params
   wrap-args
   [""] (fn [r]
          (-> (response (index (mon/fetch :jobs)
                               (worker-info)))
              (content-type "text/html")
              (status 200)))
   ["create"] create
   ["delete"] delete
   ["delete-all"] delete-all
   [endpoint] (about endpoint)))

