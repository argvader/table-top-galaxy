(ns ttg.api.keys
  "Random ids and invite codes."
  (:require ["crypto" :as crypto]))

(defn new-id [] (crypto/randomUUID))

(defn new-code
  "A 12-character invite code: 72 random bits, URL-safe."
  []
  (.toString (crypto/randomBytes 9) "base64url"))
