(ns fourteatoo.familee.http
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clj-commons.digest :as digest]
   [hato.client :as http]
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
    {"user-agent" "Mozilla/5.0 (X11; Linux x86_64; rv:144.0) Gecko/20100101 Firefox/144.0"
     "origin" origin-url
     "content-type" "application/json"
     "x-goog-api-key" goog-api-key
     "authorization" authorization}))

(def cookie-manager (cookies/make-cookie-manager))

(def client
  (http/build-http-client {:connect-timeout 10000
                           :cookie-handler cookie-manager
                           :redirect-policy :always}))

(defn- merge-default-options [opts]
  (merge-with #(if (map? %1)
                 (merge %1 %2)
                 %2)
              {:headers (make-headers (cookies/get-sapisid cookie-manager))
               :http-client client}
              opts))

(comment
  (cookies/refresh-cookies! cookie-manager)
  (map bean (.getCookies (.getCookieStore cookie-manager)))
  (.getURIs (.getCookieStore cookie-manager))
  (def cookie (#'cookies/firefox->cookie {:name "foo" :value "bar" :host "httpbin.org"}))
  (#'cookies/cookie-uri cookie)
  (cookies/add-cookie cookie-manager cookie)
  (:body (http/get "https://httpbin.org/cookies" {:http-client client :as :json})))

(defn http-get [url & [opts]]
  (cookies/refresh-cookies! cookie-manager)
  (-> (http/get url (merge-default-options opts))
      response-with-json))

(defn http-post [url & [opts]]
  (cookies/refresh-cookies! cookie-manager)
  (-> (http/post url (merge-default-options opts))
      response-with-json))

(defn http-put [url & [opts]]
  (cookies/refresh-cookies! cookie-manager)
  (-> (http/put url (merge-default-options opts))
      response-with-json))

