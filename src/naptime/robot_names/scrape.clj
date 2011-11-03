(ns naptime.robot-names.scrape
  (:require [clj-http.client :as client]))


(defn jp-gen-url [letter]
  (str "http://japanese.about.com/library/blboysname_" letter ".htm"))

(defn jp-fetch [url]
  (try
    (client/get url)
    (catch Exception e nil)))

(defn jp-find-names [html]
  (re-seq #"center\"><font size=\"2\" face=\"Verdana\">(.*)</font></td>" html))


(defn jp-names []
  (->> (range 97 123)
       (map char)
       (map jp-gen-url)
       (map jp-fetch)
       (filter identity)
       (map :body)
       (mapcat jp-find-names)
       (map second)))

(def en-path "/tmp/baby-names.html")

(defn en-names []
  (->> (slurp en-path)
       (re-seq #"<td>(\d+)</td>\s+<td>(.*)</td>\s+<td>\w+</td>")
       (map #(nth % 2))
       sort))


