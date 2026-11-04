(ns ttg.config
  "The app's settings, from resources/config.edn via env.config (aero, read at build time for a profile). bb dev
   builds :local and talks to the dev API with no sign-in; bb web:deploy builds :dev or :prod with the
   ttg-api-<stage> stack's outputs. A release with no API stack has none, and the library says so."
  (:require [env.config :as env]))

(def api-url (:api-url env/config ""))

;; Until Cognito is configured (dev) the API takes the user from this header. Deployed builds send none.
(def dev-owner (:dev-owner env/config ""))

;; Cognito hosted sign-in and the web client id.
(def auth-domain (:auth-domain env/config ""))
(def client-id (:client-id env/config ""))
