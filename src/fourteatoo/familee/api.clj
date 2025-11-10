(ns fourteatoo.familee.api
  (:require [fourteatoo.familee.http :as http]
            [cheshire.core :as json]
            [mount.core :as mount]))

(def api-base-url "https://kidsmanagement-pa.clients6.google.com/kidsmanagement/v1")

(defn get-family-members []
  (-> (http/http-get (str api-base-url "/families/mine/members"))
      :json))

(defn get-supervised-members []
  (->> (get-family-members)
       :members
       (filter #(get-in % [:member-supervision-info :is-supervised-member]))))

(defn get-apps-usage [account-id]
  (-> (http/http-get (str api-base-url "/people/" account-id "/appsandusage")
                     {:query-params {:capabilities ["CAPABILITY_APP_USAGE_SESSION"
                                                    "CAPABILITY_SUPERVISION_CAPABILITIES"]}})
      :json))

(defn app-limit [app]
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
  "Limit can be `:block`, `:allow`, or number of minutes."
  [account-id package limit]
  (let [body [account-id [(apply vector [package] (limit-to-update limit))]]]
    (-> (http/http-post (str api-base-url "/people/" account-id "/apps:updateRestrictions")
                        {:body (json/generate-string body)
                         :headers {:content-type "application/json+protobuf"}})
        :json)))

(comment
  (let [pkg "com.google.android.youtube"
        account-id "112679673099751393232"
        body [account-id [[[pkg] nil nil]]]]
    (binding [http/*debug-http* true]
      (-> (http/http-post (str api-base-url "/people/" account-id "/apps:updateRestrictions")
                          {:body (json/generate-string body)
                           :headers {:content-type "application/json+protobuf"}})
          :json))))

(comment
  (:members (get-family-members))
  (def account-id "112679673099751393232")
  (def restrictions (:apps (get-apps-usage account-id)))
  (update-restrictions account-id
                       "com.google.android.youtube"
                       :unrestricted))
