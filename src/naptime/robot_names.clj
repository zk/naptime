(ns naptime.robot-names
  (:require [naptime.robot-names.jp :as jp]
            [naptime.robot-names.en :as en])
  (:import [java.security MessageDigest]
           [java.math BigInteger]))

(def names-list (vec (shuffle (concat jp/names en/names))))

(def names-count (+ (count jp/names)
                    (count en/names)))

(defn sha1-bigint [s]
  (BigInteger. (.digest (MessageDigest/getInstance "SHA1") (.getBytes s))))

(defn lookup
  "Consistently map strings to names"
  [s]
  (get names-list
       (dec (mod (sha1-bigint s) names-count))
       "???"))

