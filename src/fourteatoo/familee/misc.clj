(ns fourteatoo.familee.misc
  (:require [clojure.java.io :as io]
            [java-time.api :as jt])
  (:import [java.io PushbackReader]))

(defn index-by [k l]
  (->> l
       (map (juxt k #(dissoc % k)))
       (into {})))

(defn sleep [secs]
  (Thread/sleep (* secs 1000)))

(defn pushback-reader [file]
  (PushbackReader. (io/reader (io/file file))))

(defn cached [f ttl]
  (let [cache (atom {})]
    (fn [& args]
      (let [now (jt/instant)
            last (get @cache args)]
        (if (or (not last)
                (jt/before? (jt/plus (:epoch last)
                                     (jt/seconds ttl))
                            now))
          (let [return-value (apply f args)]
            (swap! cache assoc args {:epoch now
                                     :value return-value})
            return-value)
          (:value last))))))
