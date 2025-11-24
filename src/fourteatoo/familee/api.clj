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

(defn get-devices
  "Query the devices of user `user-id`"
  [user-id]
  (-> (http/http-get (str (api-base-url) "/people/" user-id "/devices")
                     {:query-params {:includeUnmanagedDevices true}})
      :json))

(defn get-places
  "Query the family places"
  []
  (-> (http/http-get (str (api-base-url) "/families/mine/places"))
      :json))

(defn get-location
  "Locate `user-id` and return his/her current position."
  [user-id]
  (-> (http/http-get (str (api-base-url) "/families/mine/location/" user-id)
                     {:query-params {:locationRefreshMode "NORMAL"
                                     :supportedConsents "SUPERVISED_LOCATION_SHARING"}})
      :json))

(defn get-location-settings
  "Query the location settings for user `user-id`"
  [user-id]
  (-> (http/http-get (str (api-base-url) "/people/" user-id "/locationsettings"))
      :json))

(defn get-applied-time-limits
  "Query various time limitations that apply now or soon.  Such as
  today's bed time (downtime) or schooltime or daily usage.  For each
  device a map in `:applied-time-limits` contains entries such as
  `:today-window-limit-entry`, `:next-usage-limit-entry`,
  `:next-window-limit-entry`, `:today-window-limit-entries`,
  `:current-usage-limit-entry`, `:inactive-today-window-limit-entry`,
  and much more."
  [user-id]
  (-> (http/http-get (str (api-base-url) "/people/" user-id "/appliedTimeLimits")
                     {:query-params {:capabilities ["TIME_LIMIT_CLIENT_CAPABILITY_SCHOOLTIME"]}})
      :json))

(defn google-service-settings [user-id]
  (-> (http/http-get (str (api-base-url) "/people/" user-id "/googleServiceSettings")
                     {:query-params {"readMask.settingsTypes" "APP_ACTIVITY_SETTINGS"}})
      :json))

(defn get-time-limit [user-id]
  (-> (http/http-get (str (api-base-url) "/people/" user-id "/timeLimit")
                     {:query-params {:capabilities ["TIME_LIMIT_CLIENT_CAPABILITY_SCHOOLTIME"]}})
      :json))

(defn- get-supervision-modes [user-id]
  (-> (get-time-limit user-id)
      (get-in [:time-limit
               :supervision-modes])))

(defn- supervision-type->str [type]
  (if (keyword? type)
    (str (name type) "Mode")
    type))

(defn- get-supervision-id
  "The `type` can be `:schooltime` or `:downtime`.  Return the
  supervision mode id associated to the `type`."
  [user-id type]
  (let [type (supervision-type->str type)]
    (->> (get-supervision-modes user-id)
         (filter #(= (:type %) type))
         first
         :id)))

(defn- get-time-window-limits [user-id]
  (-> (get-time-limit user-id)
      (get-in [:time-limit
               :time-window-limit
               :entries])))

(defn- get-time-usage-limits [user-id]
  (mapcat :entries
          (-> (get-time-limit user-id)
              (get-in [:time-limit
                       :time-usage-limits]))))

(defn- get-time-window-limit-id
  "Get the window limit id associated to the supervision type and the
  day of the week.  The `supervision` can be `:schooltime` or
  `:downtime`.  The `weekday` can be a keyword or a lowercase string."
  [user-id supervision weekday]
  (let [weekday (name weekday)
        supervision-id (get-supervision-id user-id supervision)]
    (->> (get-time-window-limits user-id)
         (filter #(and (= (:effective-day %) weekday)
                       (= (:supervision-mode-id %) supervision-id)))
         first
         :id)))

(defn- get-time-usage-limit-id
  "Get the usage limit id associated to the day of the week.  The
  `weekday` can be a keyword or a lowercase string."
  [user-id weekday]
  (let [weekday (name weekday)]
    (->> (get-time-usage-limits user-id)
         (filter #(= (:effective-day %) weekday))
         first
         :id)))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- app-limit-to-update [limit]
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
  (let [body [user-id [(apply vector [package] (app-limit-to-update limit))]]]
    (-> (http/http-post (str (api-base-url) "/people/" user-id "/apps:updateRestrictions")
                        {:body (json/generate-string body)
                         :headers {:content-type "application/json+protobuf"}})
        :json)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- update-time-limit [user-id body]
  (-> (http/http-put (str (api-base-url) "/people/" user-id "/timeLimit:update")
                     {:body (json/generate-string body)
                      :debug true
                      :headers {:content-type "application/json+protobuf"}})
      :json))

(def ^:private activation->int {:enable 2 :disable 1})

(defn- encode-daily-limits [user-id limits]
  (let [{activations true limitations false} (group-by (comp keyword? val) limits)
        activations (map (fn [[day state]]
                           [(get-time-usage-limit-id user-id day)
                            (activation->int state)])
                         activations)
        limitations (map (fn [[day limit]]
                           [(get-time-usage-limit-id user-id day)
                            limit])
                         limitations)]
    [activations limitations]))

(defn- span->pair [span]
  ((juxt :from :to) span))

(defn- encode-window-spans [user-id window limits]
  (map (fn [[day span]]
         (cons (get-time-window-limit-id user-id window day)
               (span->pair span)))
       limits))

(defn set-daily-time-limits
  "Turn on and off the device daily usage or set the limit for each
  day. The `limits` can be either `:enable` (the limits are enforced),
  `:disable` (no limit ever) or a map.  The map should be from day of
  the week to limitation.  The limitation can be `:enable`, `:disable`
  or an integer (minutes).  Example: `{:monday 60 :tuesday
  90 :saturday :disable}`"
  [user-id limits]
  (cond (#{:enable :disable} limits)
        (update-time-limit user-id [nil user-id [nil [[(activation->int limits) nil nil nil]]] nil [1]])

        (map? limits)
        (update-time-limit user-id
                           [nil user-id
                            [nil [(apply vector (activation->int :enable) nil
                                         (encode-daily-limits user-id limits))]]
                            nil [1]])

        :else
        (throw (ex-info "limits needs to be either :enable, :disable or a map"
                        {:user-id user-id :limits limits}))))

(comment
  (set-daily-time-limits user-id {:tuesday 105 :saturday 210 :friday 210})
  (set-daily-time-limits user-id {:tuesday :enable}))

(defn set-schedule-windows
  "Turn on and off or configure the device daily windows.  The window
  can be `:downtime` or `:schooltime` (perhaps others?).  During such
  windows the device will be blocked to avoid distractions.  The
  `limits` can be either `:enable` (the limits are enforced),
  `:disable` (no limit) or a map.  The map should be from day of the
  week to time span.  The time span is of the form `{:from [hh
  mm] :to [hh mm]}`.  Example: `{:monday {:from [21 0] :to [7
  0]} :saturday {:from [22 30] :to [9 30]}}`"
  [user-id window limits]
  (cond (#{:enable :disable} limits)
        (update-time-limit user-id
                           [nil user-id
                            [[nil nil nil nil] nil nil nil
                             [nil [[(get-supervision-id user-id window)
                                    (activation->int limits)]]]]
                            nil [1]])

        (map? limits)
        (let []
          (vector 'update-time-limit user-id
                             [nil user-id
                              [[nil nil nil
                                (encode-window-spans user-id window limits)]
                               nil nil nil []]
                              nil [1]]))

        :else
        (throw (ex-info "limits needs to be either :enable, :disable or a map"
                        {:user-id user-id :limits limits}))))

(comment
  (set-schedule-windows user-id :bedtime {:friday {:from [22 15] :to [9 45]} :tuesday :disable})
  (set-schedule-windows user-id :schooltime :enable))

(defn make-day-limit-override
  "Apply a temporary override to a day time limit.  `limit` can be
  `:enable`, `:disable` or an integer number of minutes. `weekday` is
  a lowercase keyword."
  [user-id device-id weekday limit]
  (let [device-id device-id
        day-id (get-time-usage-limit-id user-id weekday)
        delta (if (keyword? limit)
                [(activation->int limit) nil day-id]
                [(activation->int :enable) limit day-id])
        body [nil user-id [[nil nil 8 device-id nil nil nil nil nil nil nil delta]] [1]]]
    (-> (http/http-post (str (api-base-url) "/people/" user-id "/timeLimitOverrides:batchCreate")
                        {:body (json/generate-string body)
                         :headers {:content-type "application/json+protobuf"}})
        :json)))
