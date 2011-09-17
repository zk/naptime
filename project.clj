(defproject naptime "0.1"
  :description "REST based URL hook scheduler."
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [nsfw "0.2.4"]
                 [com.ning/async-http-client "1.6.2"]
                 [congomongo "0.1.7-SNAPSHOT"]]
  :dev-dependencies [[swank-clojure "1.4.0-SNAPSHOT"]
                     [lein-marginalia "0.6.1"]]
  :repositories {"sonatype"
                 "http://oss.sonatype.org/content/repositories/releases"})

