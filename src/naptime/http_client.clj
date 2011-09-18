(ns naptime.http-client
  (:require [clj-http.core :as core]
            [clj-http.util :as util]
            [clj-http.client :as cl])
  (:refer-clojure :exclude (get)))

;; From clj-http.client, minus exception handling.

(defn wrap-request
  "Returns a battaries-included HTTP request function coresponding to the given
   core client. See client/client."
  [request]
  (-> request
      cl/wrap-redirects
      cl/wrap-decompression
      cl/wrap-input-coercion
      cl/wrap-output-coercion
      cl/wrap-query-params
      cl/wrap-basic-auth
      cl/wrap-user-info
      cl/wrap-accept
      cl/wrap-accept-encoding
      cl/wrap-content-type
      cl/wrap-method
      cl/wrap-url))

(def #^{:doc
  "Executes the HTTP request corresponding to the given map and returns the
   response map for corresponding to the resulting HTTP response.

   In addition to the standard Ring request keys, the following keys are also
   recognized:
   * :url
   * :method
   * :query-params
   * :basic-auth
   * :content-type
   * :accept
   * :accept-encoding
   * :as

  The following additional behaviors over also automatically enabled:
   * Gzip and deflate responses are accepted and decompressed
   * Input and output bodies are coerced as required and indicated by the :as
     option."}
  request
  (wrap-request #'core/request))

(defn get
  "Like #'request, but sets the :method and :url as appropriate."
  [url & [req]]
  (request (merge req {:method :get :url url})))

