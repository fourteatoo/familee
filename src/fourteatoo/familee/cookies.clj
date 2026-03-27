(ns fourteatoo.familee.cookies
  (:require
   [clojure-ini.core :as ini]
   [clojure.java.io :as io]
   [clojure.string :as s]
   [hato.client :as http]
   [fourteatoo.familee.conf :as c]
   [fourteatoo.familee.jsonlz4 :as jsonlz4]
   [next.jdbc :as jdbc]
   [fourteatoo.familee.misc :as misc]
   [fourteatoo.familee.log :as log])
  (:import [java.net CookieHandler CookieManager CookiePolicy CookieStore HttpCookie URI]))


(defonce default-cookie-policy CookiePolicy/ACCEPT_ALL)

(defn make-cookie-manager [& [store policy]]
  (CookieManager. store
                  (or policy default-cookie-policy)))

(defn- get-cookie-store [cookie-manager]
  (.getCookieStore cookie-manager))

(defn- as-cookie-store [store-or-manager]
  (if (instance? CookieStore store-or-manager)
    store-or-manager
    (get-cookie-store store-or-manager)))

(defn- cookie-uri [cookie]
  (let [domain (.getDomain cookie)]
    (if domain
      (URI. (str "https://" domain))
      nil)))

(defn add-cookie [store-or-manager cookie]
  (.add (as-cookie-store store-or-manager) (cookie-uri cookie) cookie))

(defn- slurp-firefox-cookies [filename & [host]]
  (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:file:" filename
                                               "?mode=ro&nolock=1&immutable=1")})]
    (->> (jdbc/execute! ds ["select * from moz_cookies where host like ?" (or host "%")]
                        jdbc/unqualified-snake-kebab-opts)
         (map #(assoc % :comment (str "source " filename))))))

;; convert from the SQLite table row to java.net cookie
(defn- firefox->cookie [firefox-cookie]
  (doto (HttpCookie. (:name firefox-cookie) (:value firefox-cookie))
    (.setComment (:comment firefox-cookie))
    (.setDomain (:host firefox-cookie))
    (.setPath (or (:path firefox-cookie) "/"))
    (.setSecure (= 1 (:is-secure firefox-cookie)))
    (.setMaxAge
     -1 #_(if (:expiry firefox-cookie)
       (- (:expiry firefox-cookie)
          (quot (System/currentTimeMillis) 1000))
       -1))
    (.setVersion 0)))

(defn- user-home []
  (System/getProperty "user.home"))

(defn- firefox-directory []
  (if (c/conf :firefox-directory)
    (io/file (c/conf :firefox-directory))
    (io/file (user-home) ".mozilla" "firefox")))

(defn- profiles-file []
  (io/file (firefox-directory) "profiles.ini"))

(defn- read-profiles-ini []
  (ini/read-ini (profiles-file)))

(defn- user-profile-subdir []
  (->> (read-profiles-ini)
       (filter (fn [[section vars]]
                 (s/starts-with? section "Install")))
       (map val)
       (map #(get % "Default"))
       first
       io/file))

(defn get-user-profile-directory []
  (->> (or (c/conf :firefox-profile-directory)
                     (user-profile-subdir))
       (io/file (firefox-directory))
       log/spy))

(defn- cookies-db-file []
  (io/file (get-user-profile-directory)
           "cookies.sqlite"))

(defn- recovery-file []
  (io/file (get-user-profile-directory)
           "sessionstore-backups"
           "recovery.jsonlz4"))

(defn- slurp-firefox-session-cookies [file]
  (->> (jsonlz4/decompress-jsonlz4 file)
       :cookies
       (map #(assoc % :comment (str "source " file)))))

(defn- firefox-session->cookie [session-cookie]
  (doto (HttpCookie. (:name session-cookie) (:value session-cookie))
    (.setComment (:comment session-cookie))
    (.setDomain (:host session-cookie))
    (.setPath (or (:path session-cookie) "/"))
    (.setSecure (boolean (:secure session-cookie)))
    (.setMaxAge -1)
    (.setVersion 0)))

(defn- fill-cookie-store! [cookie-store cookies]
  (run! (partial add-cookie cookie-store) cookies))



(defn steal-browser-cookies []
  (concat
   (->> (slurp-firefox-cookies (cookies-db-file) "%google.com")
        (map firefox->cookie))
   (->> (slurp-firefox-session-cookies (recovery-file))
        (filter (fn [c]
                  (re-matches #"\.google\.com$" (:host c))))
        (map firefox-session->cookie))))

(comment
  (->> (steal-browser-cookies)
       (map #(.hasExpired %))))

(def steal-browser-cookies-cached (misc/cached steal-browser-cookies 15))

(defn refresh-cookies! [cookie-manager]
  (->> (steal-browser-cookies-cached)
       (fill-cookie-store! (.getCookieStore cookie-manager))))

(defn get-cookies [cookie-manager-or-store]
  (.getCookies (as-cookie-store cookie-manager-or-store)))

(defn- get-cookie [store name domain]
  (->> (get-cookies store)
       (filter (fn [c]
                 (and (= name (.getName c))
                      (= domain (.getDomain c)))))
       first))

(defn get-sapisid [cookie-store]
  (-> (as-cookie-store cookie-store)
      (get-cookie "SAPISID" ".google.com")
      .getValue))
