(ns fourteatoo.familee.core
  (:gen-class)
  (:require
   [clojure.data :refer [diff]]
   [java-time.api :as jt]
   [fourteatoo.familee.api :as api]
   [clojure.tools.cli :refer [parse-opts]]
   [mount.core :as mount]
   [clojure.pprint :as pp]
   [clojure.java.io :as io]
   [fourteatoo.familee.conf :as conf]
   [clojure.edn :as edn]
   [fourteatoo.familee.log :as log])
  (:import [java.io PushbackReader]))

(def ^:private cli-options
  [["-s" "--save FILE" "download current app restrictions status and save it into FILE"]
   ["-r" "--restore FILE" "restore app restrictions if different from FILE"]
   ["-d" "--diff FILE" "compare current restrictions with those in FILE"]
   ["-p" "--print" "print family and apps state"]
   ["-n" "--dry-run" "do not apply changes, just print them"]
   #_["-c" "--config FILE" "confirguration file"
      :parse-fn #(io/file %)
      :validate [#(.exists %) "Configuration file does not exist"]]
   ["-v" "--verbose" "increase logging verbosity"
    :default 0
    :update-fn inc]
   ["-h" "--help" "display program usage"]])

(defn- usage [summary errors]
  (println "usage: familee [option ...]")
  (doseq [e errors]
    (println e))
  (when summary
    (println summary))
  (System/exit -1))

(defn- parse-cli [args]
  (let [{:keys [arguments summary errors] :as result} (parse-opts args cli-options)]
    (when (or errors
              (seq arguments))
      (usage summary errors))
    result))

(defn- index-by [k l]
  (->> l
       (map (juxt k #(dissoc % k)))
       (into {})))

(defn- get-apps-usage []
  (->> (api/get-supervised-members)
       (map (fn [user]
              (-> (select-keys user [:user-id])
                  (assoc :name (get-in user [:profile :display-name]))
                  (assoc :apps (:apps (api/get-apps-usage (:user-id user)))))))
       (index-by :user-id)))

(defn- app-state->restriction [app]
  (-> (select-keys app [:title :package-name])
      (assoc :limit (api/app-limit app))))

(defn- fetch-restrictions-summary []
  (->> (get-apps-usage)
       (map (fn [[user-id config]]
              [user-id (update config :apps
                               (fn [apps]
                                 (->> apps
                                      (map app-state->restriction)
                                      (index-by :package-name))))]))
       (into {})))

(defn- save-restrictions [file]
  (with-open [out (io/writer (io/file file))]
    (pp/pprint (fetch-restrictions-summary) out)))

(comment
  (save-restrictions "/home/wcp/tmp/restrictions.edn"))

(defn- upload-restrictions [family]
  (->> family
       (run! (fn [[user-id user-config]]
               (->> (:apps user-config)
                    (run! (fn [[package pkg-config]]
                            (let [limit (:limit pkg-config)]
                              (api/update-restrictions user-id package limit)))))))))

(defn- pushback-reader [file]
  (PushbackReader. (io/reader (io/file file))))

(defn load-restrictions-summary-from-file [file]
  (with-open [in (pushback-reader file)]
    (edn/read in)))

(defn- strip-ancillary-info [family-restrictions]
  (->> (map (fn [[uid uconf]]
              [uid (update (select-keys uconf [:apps])
                           :apps
                           (fn [apps]
                             (->> (map (fn [[appid appconf]]
                                         [appid (select-keys appconf [:limit])])
                                       apps)
                                  (into {}))))])
            family-restrictions)
       (into {})))

(defn- diff-family-restrictions [r1 r2]
  (let [[a b _] (diff (strip-ancillary-info r1) (strip-ancillary-info r2))]
    [a b]))

(defn- restore-restrictions [file]
  (let [current (fetch-restrictions-summary)
        desired (load-restrictions-summary-from-file file)
        [_ delta] (diff-family-restrictions current desired)]
    (when (or (conf/opt :dry-run)
              (> (conf/opt :verbose) 0))
      (println "Delta to apply:")
      (pp/pprint delta))
    (when-not (conf/opt :dry-run)
      (upload-restrictions delta))))

(defn- diff-restrictions [file]
  (let [active-restrictions (fetch-restrictions-summary)
        stored-restrictions (load-restrictions-summary-from-file file)
        [only-active only-stored _] (diff-family-restrictions active-restrictions stored-restrictions)]
    (run! (fn [[user user-config]]
            (println "User" (get-in active-restrictions [user :name]))
            (->> (map (fn [[app-id app-config]]
                        (let [local-limit (get-in stored-restrictions [user :apps app-id :limit])]
                          {:title (get-in active-restrictions [user :apps app-id :title])
                           :package app-id
                           :stored local-limit
                           :active (:limit app-config)}))
                      (:apps user-config))
                 (pp/print-table [:title :package :active :stored])))
          only-active)))

(defn print-family-configuration []
  (pp/pprint (get-apps-usage)))

(defn -main [& args]
  (let [{:keys [options summary]} (parse-cli args)]
    (binding [conf/options options]
      (mount/start)
      (clj-http.client/with-connection-pool {}
        (cond (:save options)
              (save-restrictions (:save options))
              (:restore options)
              (restore-restrictions (:restore options))
              (:diff options)
              (diff-restrictions (:diff options))
              :else
              (usage summary nil)))
      (mount/stop)
      ;; don't wait for the hanging threads
      (System/exit 0))))
