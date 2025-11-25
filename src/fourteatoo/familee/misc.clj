(ns fourteatoo.familee.misc
  (:require [clojure.java.io :as io])
  (:import [java.io PushbackReader]))

(defn index-by [k l]
  (->> l
       (map (juxt k #(dissoc % k)))
       (into {})))

(defn sleep [secs]
  (Thread/sleep (* secs 1000)))

(defn pushback-reader [file]
  (PushbackReader. (io/reader (io/file file))))
