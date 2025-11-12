(ns fourteatoo.familee.jsonlz4
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.java.io :as io])
  (:import
   [net.jpountz.lz4 LZ4Factory LZ4FrameInputStream]))

(defn- little-endian-int
  "Reads a 4-byte little-endian int from a byte array starting at offset."
  [bytes offset]
  (bit-or (bit-shift-left (bit-and (aget bytes (+ offset 3)) 0xFF) 24)
          (bit-shift-left (bit-and (aget bytes (+ offset 2)) 0xFF) 16)
          (bit-shift-left (bit-and (aget bytes (+ offset 1)) 0xFF) 8)
          (bit-and (aget bytes offset) 0xFF)))

(defn make-lz4-safe-decompressor []
  (.safeDecompressor (LZ4Factory/fastestInstance)))

(defn decompress-lz4-bytes [source destination]
  (.decompress (make-lz4-safe-decompressor) source 0
               (alength source)
               destination 0 (alength destination)))

(defn read-bytes [in & [length]]
  (let [a (byte-array (or length (.available in)))]
    (.read in a)
    a))

(def ^:private jsonlz4-header "mozLz40\0")

(defn decompress-jsonlz4 [file & [key-fn]]
  (with-open [in (io/input-stream (io/file file))]
    (let [header (read-bytes in (count jsonlz4-header))
          size (read-bytes in 4)]
      (when-not (= (String. header) jsonlz4-header)
        (throw (ex-info "not a jsonlz4 file" {:file file :header header})))
      (let [compressed-data (read-bytes in)
            expected-length (little-endian-int size 0)
            data (byte-array expected-length)
            data-length (decompress-lz4-bytes compressed-data data)]
        (-> (String. data 0 data-length "UTF-8")
            (json/parse-string (or key-fn csk/->kebab-case-keyword)))))))

(comment
  (:cookies (decompress-jsonlz4 "/home/wcp/.mozilla/firefox/tfnou77j.default-release/sessionstore-backups/recovery.jsonlz4")))

#_(defn decompress-jsonlz4 [file]
    (-> (sh/sh "lz4jsoncat" (str file))
        :out
        (json/parse-string csk/->kebab-case-keyword)))

