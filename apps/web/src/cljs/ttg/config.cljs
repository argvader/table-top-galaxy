(ns ttg.config
  "Build-time settings (shadow-cljs :closure-defines). Dev talks to bb dev's API with no sign-in; bb web:deploy
   fills these from the ttg-api-<stage> stack. A release build with no API stack has none, and the library says so.")

(goog-define api-url "http://localhost:3000")

;; Until Cognito is configured (dev) the API takes the user from this header. Deployed builds send none.
(goog-define dev-owner "local-gm")

;; Cognito hosted sign-in, e.g. https://ttg-dev-123.auth.us-east-1.amazoncognito.com, and the web client id.
(goog-define auth-domain "")
(goog-define client-id "")
