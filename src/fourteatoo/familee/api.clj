(ns fourteatoo.familee.api
  (:require [fourteatoo.familee.http :as http]
            [fourteatoo.familee.conf :refer [conf]]
            [cheshire.core :as json]
            [mount.core :as mount]))


(defn- api-base-url []
  (str (or (conf :kids-management-host)
           "https://kidsmanagement-pa.clients6.google.com")
       "/kidsmanagement/v1"))

(defn get-family-members
  "Query the family nucelus composition.  The JSON reply is returned
  unchanged.  The members are under the `:members` key."
  []
  (-> (http/http-get (str (api-base-url) "/families/mine/members"))
      :json))

(defn get-supervised-members
  "Query the the family members unders supervision.  Return a list."
  []
  (->> (get-family-members)
       :members
       (filter #(get-in % [:member-supervision-info :is-supervised-member]))))

(defn get-apps-usage
  "Query the app configurations for the user `user-id`."
  [user-id]
  (-> (http/http-get (str (api-base-url) "/people/" user-id "/appsandusage")
                     {:query-params {:capabilities ["CAPABILITY_APP_USAGE_SESSION"
                                                    "CAPABILITY_SUPERVISION_CAPABILITIES"]}})
      :json))

(defn get-applied-time-limits [user-id]
  (-> (http/http-get (str (api-base-url) "/people/" user-id "/appliedTimeLimits")
                     {:query-params {:capabilities ["TIME_LIMIT_CLIENT_CAPABILITY_SCHOOLTIME"]}})
      :json))

(defn app-limit
  "Given an app as returned by the `get-apps-usage`, return either:
  `:allowed`, `:blocked`, `:unlimited` or the time in minutes."
  [app]
  (cond (get-in app [:supervision-setting :usage-limit :enabled])
        ;; limit in minutes
        (get-in app [:supervision-setting :usage-limit :daily-usage-limit-mins])

        (and (not (get-in app [:supervision-setting :hidden]))
             (get-in app [:supervision-setting :always-allowed-app-info :always-allowed-state]))
        ;; allowed 24/7 regardless the daily screen time limitations
        :unlimited

        (get-in app [:supervision-setting :hidden])
        ;; never allowed
        :blocked

        (not (get-in app [:supervision-setting :hidden]))
        ;; limited by the daily screen time
        :allowed))

(defn- limit-to-update [limit]
  (case limit
    :blocked
    ;; never ever
    [[1]]

    :allowed
    ;; restricted by the daily screen time
    [nil nil]

    :unlimited
    ;; this is 24/7, no restriction at all
    [nil nil [1]]

    ;; limit in minutes
    (do
      (when-not (integer? limit)
        (throw (ex-info "Limit not among the expected values" {:limit limit})))
      [nil [limit 1]])))

(defn update-restrictions
  "Update the app identified by `package` for the user `user-id` to
  `limit`.  `limit` can be either: `:blocked`, `:allowed`,
  `:unlimited`, or an integer value which is interpreted as time in
  minutes."
  [user-id package limit]
  (let [body [user-id [(apply vector [package] (limit-to-update limit))]]]
    (-> (http/http-post (str (api-base-url) "/people/" user-id "/apps:updateRestrictions")
                        {:body (json/generate-string body)
                         :headers {:content-type "application/json+protobuf"}})
        :json)))
