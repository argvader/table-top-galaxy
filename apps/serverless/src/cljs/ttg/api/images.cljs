(ns ttg.api.images
  "Where portraits are stored. S3 here; bb dev writes to the web app's public folder (ttg.api.dev) and tests use an atom."
  (:require ["@aws-sdk/client-s3" :as s3]
            [ttg.api.async :refer [go-try <p?]]))

(defprotocol Images
  (put-image! [images key bytes] "Stores a JPEG (a Buffer) under key.")
  (delete-image! [images key] "Removes it. Missing keys are fine."))

(defrecord S3Images [^js client bucket]
  Images
  (put-image! [_ key bytes]
    (go-try (<p? (.send client (s3/PutObjectCommand. #js {:Bucket bucket :Key key :Body bytes :ContentType "image/jpeg"
                                                          :CacheControl "public, max-age=31536000, immutable"})))
            true))
  (delete-image! [_ key]
    (go-try (<p? (.send client (s3/DeleteObjectCommand. #js {:Bucket bucket :Key key})))
            true)))

(defn- env [k] (aget js/process.env k))

(defn s3-images
  "Reads PORTRAIT_BUCKET."
  []
  (->S3Images (s3/S3Client. #js {:region (or (env "AWS_REGION") "us-east-1")}) (env "PORTRAIT_BUCKET")))
