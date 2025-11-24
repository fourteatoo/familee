(ns fourteatoo.familee.http
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clj-commons.digest :as digest]
   [clj-http.client :as http]
   [fourteatoo.familee.cookies :as cookies]
   [java-time.api :as jt]))

(def ^:dynamic *debug-http* false)

(def origin-url "https://familylink.google.com")
(def goog-api-key "AIzaSyAQb1gupaJhY3CXQy2xmTwJMcjmot3M2hw")

(defn- response-with-json [response]
  (assoc response :json (json/parse-string (:body response) csk/->kebab-case-keyword)))

(defn make-sapisid-hash
  ([timestamp sapisid origin]
   (let [hash (digest/sha-1 (str timestamp " " sapisid " " origin))]
     (str timestamp "_" hash)))
  ([sapisid origin]
   (make-sapisid-hash (jt/to-millis-from-epoch (jt/instant)) sapisid origin)))

(defn- make-headers [sapisid]
  (let [hash (make-sapisid-hash sapisid origin-url)
        authorization (str "SAPISIDHASH " hash)]
    {:user-agent "Mozilla/5.0 (X11; Linux x86_64; rv:144.0) Gecko/20100101 Firefox/144.0"
     :origin origin-url
     :content-type "application/json"
     :x-goog-api-key goog-api-key
     :authorization authorization}))

(defn- merge-default-options [opts]
  (merge-with #(if (map? %1)
                 (merge %1 %2)
                 %2)
              {:headers (make-headers (cookies/get-sapisid))
               :cookie-store (cookies/cookie-jar)
               :debug *debug-http*
               :save-request? *debug-http*
               :cookie-policy :standard}
              opts))

(defn http-get [url & [opts]]
  (-> (http/get url (merge-default-options opts))
      response-with-json))

(defn http-post [url & [opts]]
  (-> (http/post url (merge-default-options opts))
      response-with-json))

(defn http-put [url & [opts]]
  (-> (http/put url (merge-default-options opts))
      response-with-json))

