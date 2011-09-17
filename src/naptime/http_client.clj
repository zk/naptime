(ns naptime.http-client
  (:import [com.ning.http.client
            AsyncHttpClient
            AsyncCompletionHandler
            Response]
           [java.util.concurrent Future]))

(def client (AsyncHttpClient.))

(defn completion-handler-proxy [on-completed on-error]
  (proxy [AsyncCompletionHandler] []
    (onCompleted [^Response r]
      (on-completed r))
    (onThrowable [^Throwable t]
      (println "error" t)
      (on-error t))))



#_(def f (.execute
        (.prepareGet client "http://www.google.com")
        (completion-handler-proxy
         (fn [r] (println "success!" r))
         (fn [t] (println "error")))))


#_(time
 (do (doseq [n (range 1 23)]
       (.execute
        (.prepareGet client "http://www.google.com")
        (completion-handler-proxy
         (fn [r] )
         (fn [t] ))))
     (println "done!")))


#_(time
 (.execute
       (.prepareGet client "http://www.google.com")
       (completion-handler-proxy
        (fn [r] (println "success!" r))
        (fn [t] (println "error")))))




