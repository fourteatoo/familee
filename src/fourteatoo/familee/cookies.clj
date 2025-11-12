(ns fourteatoo.familee.cookies
  (:require
   [clj-http.cookies :as cookies]
   [clojure-ini.core :as ini]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [clojure.string :as s]
   [java-time.api :as jt]
   [next.jdbc :as jdbc]
   [fourteatoo.familee.conf :as conf]
   [fourteatoo.familee.jsonlz4]))

(defn- slurp-firefox-cookies [filename & [host]]
  (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:file:" filename
                                               "?mode=ro&nolock=1&immutable=1")})]
    (->> (jdbc/execute! ds ["select * from moz_cookies where host like ?" (or host "%")]
                        jdbc/unqualified-snake-kebab-opts)
         (map #(assoc % :comment (str "source " filename))))))

(comment
  (count (slurp-firefox-cookies (cookies-db-file) "%.google.com"))
  (count (slurp-firefox-cookies (cookies-db-file))))

(defn- fix-cookie-domain [cookie]
  (when (s/starts-with? (.getDomain cookie) ".")
    (.setAttribute cookie org.apache.http.cookie.ClientCookie/DOMAIN_ATTR "true"))
  cookie)

;; convert from the SQLite table row to clj-http cookie
(defn- firefox->cookie [firefox-cookie]
  (-> (cookies/to-basic-client-cookie
       [(:name firefox-cookie)
        (cond-> (assoc (select-keys firefox-cookie [:value :path])
                       :comment (or (:comment firefox-cookie)
                                    "from Firefox SQLite DB")
                       :discard false)
          (:is-secure firefox-cookie)
          (assoc :secure (= 1 (:is-secure firefox-cookie)))
          (:host firefox-cookie)
          (assoc :domain (:host firefox-cookie))
          (:expiry firefox-cookie)
          (assoc :expires (jt/java-date (:expiry firefox-cookie))))])
      fix-cookie-domain))

(defn- firefox-directory []
  (io/file (System/getProperty "user.home") ".mozilla" "firefox"))

(defn- profiles-file []
  (io/file (System/getProperty "user.home") ".mozilla" "firefox" "profiles.ini"))

(defn- read-profiles-ini []
  (ini/read-ini (profiles-file)))

(defn- get-user-profile-directory []
  (->> (read-profiles-ini)
       (filter (fn [[section vars]]
                 (s/starts-with? section "Install")))
       (map val)
       (map #(get % "Default"))
       first
       (io/file (firefox-directory))))

(defn- cookies-db-file []
  (io/file (get-user-profile-directory) "cookies.sqlite"))

(comment
  (get-user-profile-directory))

(defn- slurp-firefox-session-cookies [file]
  (->> (decompress-jsonlz4 file)
       :cookies
       (map #(assoc % :comment (str "source " file)))))

(defn- firefox-session->cookie [session-cookie]
  (-> (cookies/to-basic-client-cookie
       [(:name session-cookie)
        (assoc (select-keys session-cookie [:value :path :secure])
               :comment (or (:comment session-cookie)
                            "from Firefox session")
               :discard false
               :expires (jt/java-date (jt/plus (jt/zoned-date-time) (jt/years 2)))
               :domain (:host session-cookie))])
      fix-cookie-domain))

(defn- fill-cookie-jar [jar cookies]
  (run! (partial cookies/add-cookie jar) cookies))

(defn- steal-browser-cookies [& [jar]]
  (let [jar (or jar (cookies/cookie-store))]
    (->> (slurp-firefox-cookies (cookies-db-file) "%google.com")
         (map firefox->cookie)
         (fill-cookie-jar jar))
    (->> (slurp-firefox-session-cookies (io/file (get-user-profile-directory)
                                                 "sessionstore-backups"
                                                 "recovery.jsonlz4"))
         (filter (fn [c]
                     (re-matches #"\.google\.com$" (:host c))))
         (map firefox-session->cookie)
         (fill-cookie-jar jar))
    jar))

(def default-cookies-ttl 15)

(let [cache (atom {:jar (cookies/cookie-store)
                   :epoch nil})]
  (defn cookie-jar []
    (let [{:keys [jar epoch]} @cache
          now (jt/instant)]
      (when (or (not epoch)
                (jt/before? (jt/plus epoch (jt/seconds (or (conf/conf :cookies-ttl)
                                                           default-cookies-ttl)))
                            now))
        (reset! cache {:jar (steal-browser-cookies jar)
                       :epoch now}))
      jar)))

(defn get-cookies [jar]
  (->> (.getCookies jar)
       (map cookies/to-cookie)
       (map (fn [[k v]]
              (assoc v :name k)))))

(comment
  (count (get-cookies (cookie-jar))))

(defn- get-cookie [jar name domain]
  (->> (get-cookies jar)
       (filter (fn [c]
                 (and (= name (:name c))
                      (= domain (:domain c)))))
       first))

(defn get-sapisid []
  (:value (get-cookie (cookie-jar) "SAPISID" ".google.com")))
