(ns ttg.auth
  "Sign-in through Cognito's hosted page: authorization code flow with PKCE (no client secret in the browser).
   The session (id token, refresh token, expiry) lives in localStorage; token refreshes it when it's near expiry.
   With no auth-domain configured (bb dev) everything here is off and the dev API trusts the dev-owner header."
  (:require [clojure.string :as str]
            [ttg.config :as config]))

(def ^:private session-key "ttg.session")
(def ^:private pkce-key "ttg.pkce")

(defn enabled? [] (and (seq config/auth-domain) (seq config/client-id)))

(defn- read-json [storage k] (some-> (.getItem storage k) js/JSON.parse (js->clj :keywordize-keys true)))
(defn- write-json! [storage k v] (.setItem storage k (js/JSON.stringify (clj->js v))))

(defn- session [] (read-json js/localStorage session-key))

(defn signed-in? [] (boolean (:refresh-token (session))))

(defn- b64url [^js bytes]
  (-> (js/btoa (apply str (map #(js/String.fromCharCode %) (array-seq bytes))))
      (str/replace "+" "-") (str/replace "/" "_") (str/replace "=" "")))

(defn- random-string [] (b64url (js/crypto.getRandomValues (js/Uint8Array. 32))))

(defn- challenge [verifier]
  (-> (js/crypto.subtle.digest "SHA-256" (.encode (js/TextEncoder.) verifier))
      (.then #(b64url (js/Uint8Array. %)))))

;; Cognito only returns to addresses it lists (infra/api.yml CallbackURLs), so sign-in always comes back to the root
;; and the page the user left from rides along in the PKCE record.
(defn- redirect-uri [] (str js/location.origin "/"))

(defn claims
  "The id token's payload: {:sub :email :name ...}."
  []
  (when-let [t (:id-token (session))]
    (let [part (-> (second (str/split t #"\.")) (str/replace "-" "+") (str/replace "_" "/"))]
      (js->clj (js/JSON.parse (js/decodeURIComponent (js/escape (js/atob part)))) :keywordize-keys true))))

(defn sign-in!
  "Off to the hosted sign-in page; it comes back to / with ?code=... and complete! finishes on the page left from."
  []
  (let [verifier (random-string) state (random-string)]
    (write-json! js/sessionStorage pkce-key {:verifier verifier :state state :path (str js/location.pathname js/location.hash)})
    (.then (challenge verifier)
           (fn [c]
             (js/location.assign
              (str config/auth-domain "/oauth2/authorize?"
                   (js/URLSearchParams. #js {:response_type "code" :client_id config/client-id :redirect_uri (redirect-uri)
                                             :scope "openid email profile" :state state
                                             :code_challenge_method "S256" :code_challenge c})))))))

(defn- token-request [params]
  (-> (js/fetch (str config/auth-domain "/oauth2/token")
                #js {:method "POST" :headers #js {"content-type" "application/x-www-form-urlencoded"}
                     :body (js/URLSearchParams. (clj->js (assoc params :client_id config/client-id)))})
      (.then (fn [^js r] (if (.-ok r) (.json r) (throw (ex-info "Sign-in failed" {:status (.-status r)})))))))

(defn- save-tokens! [^js t]
  (let [old (session)]
    (write-json! js/localStorage session-key
                 {:id-token (.-id_token t)
                  :refresh-token (or (.-refresh_token t) (:refresh-token old))
                  :expires-at (+ (js/Date.now) (* 1000 (.-expires_in t)))})))

(defn complete!
  "If this page load is the return from sign-in, trades the code for tokens. Resolves true when it signed in."
  []
  (let [params (js/URLSearchParams. js/location.search)
        code (.get params "code")
        pkce (read-json js/sessionStorage pkce-key)]
    (if-not (and (enabled?) code)
      (js/Promise.resolve false)
      (do (.removeItem js/sessionStorage pkce-key)
          (js/history.replaceState nil "" (or (:path pkce) "/"))
          (if (not= (.get params "state") (:state pkce))
            (js/Promise.resolve false)
            (-> (token-request {:grant_type "authorization_code" :code code :redirect_uri (redirect-uri)
                                :code_verifier (:verifier pkce)})
                (.then #(do (save-tokens! %) true))))))))

(defn sign-out! []
  (.removeItem js/localStorage session-key)
  (js/location.assign (str config/auth-domain "/logout?"
                           (js/URLSearchParams. #js {:client_id config/client-id :logout_uri (redirect-uri)}))))

(defn token
  "Promise of a fresh id token, refreshing it if it expires within a minute; nil when signed out."
  []
  (let [{:keys [id-token refresh-token expires-at]} (session)]
    (cond (and id-token (> expires-at (+ (js/Date.now) 60000))) (js/Promise.resolve id-token)
          refresh-token (-> (token-request {:grant_type "refresh_token" :refresh_token refresh-token})
                            (.then #(do (save-tokens! %) (:id-token (session))))
                            (.catch #(do (.removeItem js/localStorage session-key) nil)))
          :else (js/Promise.resolve nil))))
