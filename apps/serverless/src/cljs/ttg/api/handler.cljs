(ns ttg.api.handler
  "The API Lambda (API Gateway HTTP API, payload v2): routes, request checks and responses. EDN in and out, so
   entries keep their keywords on the way to the browser; only the campaign manager document, which a plain JS page
   reads and writes, is JSON. Who is asking comes from Cognito's JWT (checked by API Gateway before the Lambda runs);
   the dev server alone may name a user in headers instead.

   stores is {:table :db :images}: the library's DynamoDB table, Postgres, and portrait storage."
  (:require [cljs.core.async :refer [go <!]]
            [cljs.reader :as reader]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [ttg.api.async :refer [go-try <?]]
            [ttg.api.aurora :as aurora]
            [ttg.api.campaigns :as campaigns]
            [ttg.api.characters :as characters]
            [ttg.api.gateway :as gateway]
            [ttg.api.images :as images]
            [ttg.api.model :as model]
            [ttg.api.router :as router]
            [ttg.api.service :as library]))

(defonce ^:private default-stores
  (delay {:table (gateway/dynamo-table) :db (aurora/aurora) :images (images/s3-images)}))

;; The dev server swaps in its own, because PGlite and the filesystem must stay out of the Lambda.
(defonce ^:private stores-override (atom nil))
(defn use-stores! [stores] (reset! stores-override stores))
(defn- current-stores [] (or @stores-override @default-stores))

(defn- env [k] (aget js/process.env k))

(defn user
  "{:id :name} of the caller: the JWT's sub, or (dev server only, ALLOW_DEV_OWNER) the x-ttg-owner header."
  [{:keys [claims headers]}]
  (if-let [sub (:sub claims)]
    {:id sub :name (or (:name claims) (:email claims) (get claims (keyword "cognito:username")) "Player")}
    (when (= "true" (env "ALLOW_DEV_OWNER"))
      (when-let [id (get headers "x-ttg-owner")]
        {:id id :name (get headers "x-ttg-name" id)}))))

(defn- check!
  ([spec x message] (check! spec x message 400))
  ([spec x message status]
   (when-not (s/valid? spec x)
     (throw (ex-info message (cond-> {:status status} (= 400 status) (assoc :explain (s/explain-str spec x))))))
   x))

(defn- body!
  "The request body, read as EDN and checked against spec."
  [{:keys [body]} spec message]
  (let [data (try (reader/read-string (or body ""))
                  (catch :default _ (throw (ex-info "The body isn't readable EDN" {:status 400}))))]
    (check! spec data message)))

(defn- entry-type! [{:keys [type id]}]
  (check! ::model/id id (str "Not a valid entry id: " id))
  (check! ::model/type (keyword type) (str "Unknown library type: " type)))

(defn- uuid! [id] (check! ::model/uuid id (str "Not a valid id: " id)))

(defn- json-safe! [doc] (check! ::model/json-safe doc "That has something the database can't keep" 422))

(defn- json-text!
  "The raw body of a JSON request, checked to be a JSON object."
  [{:keys [body]}]
  (when-not (<= (count body) model/max-manager-chars)
    (throw (ex-info "That campaign is too large to save" {:status 413})))
  (try (when-not (= "object" (goog/typeOf (js/JSON.parse body))) (throw (js/Error. "not an object")))
       (catch :default _ (throw (ex-info "Expected a JSON object" {:status 400}))))
  body)

;; ---------- Routes ----------

(def routes
  [["GET" "/me" :me]
   ["GET" "/library" :library/list]
   ["POST" "/library/import" :library/import]
   ["PUT" "/library/:type/:id" :library/put]
   ["DELETE" "/library/:type/:id" :library/delete]
   ["GET" "/characters" :characters/list]
   ["POST" "/characters" :characters/create]
   ["GET" "/characters/:id" :characters/get]
   ["PUT" "/characters/:id" :characters/put]
   ["DELETE" "/characters/:id" :characters/delete]
   ["PUT" "/characters/:id/campaign" :characters/campaign]
   ["PUT" "/characters/:id/portrait" :characters/portrait]
   ["GET" "/campaigns" :campaigns/list]
   ["POST" "/campaigns" :campaigns/create]
   ["GET" "/campaigns/:id" :campaigns/get]
   ["PUT" "/campaigns/:id" :campaigns/put]
   ["DELETE" "/campaigns/:id" :campaigns/delete]
   ["GET" "/campaigns/:id/library" :campaigns/library]
   ["GET" "/campaigns/:id/manager" :campaigns/manager]
   ["PUT" "/campaigns/:id/manager" :campaigns/save-manager]
   ["GET" "/campaigns/:id/manager/content" :campaigns/manager-content]
   ["PUT" "/campaigns/:id/manager/content" :campaigns/save-manager-content]
   ["GET" "/campaigns/:id/party" :campaigns/party]
   ["POST" "/campaigns/:id/invites" :campaigns/invite]
   ["DELETE" "/campaigns/:id/members/:member" :campaigns/remove-member]
   ["GET" "/invites/:code" :invites/get]
   ["POST" "/invites/:code/join" :invites/join]])

(defn- library-op [op {:keys [table]} {:keys [id]} params req]
  (case op
    :library/list (library/library table id)
    :library/import (library/import! table id (:entries (body! req ::model/import-body "Expected {:entries {type [entry ...]}}")))
    :library/put (library/save-entry! table id (entry-type! params) (:id params)
                                      (:entry (body! req ::model/put-body "Expected {:entry {...}}")))
    :library/delete (library/delete-entry! table id (entry-type! params) (:id params))))

(defn- portrait-bytes [req]
  (js/Buffer.from (:image (body! req ::model/portrait-body "Expected {:image base64}")) "base64"))

(defn- character-op [op {:keys [db images]} {:keys [id]} params req]
  (let [character #(json-safe! (:character (body! req ::model/character-body "Expected {:character {:name ...}}")))
        saved #(json-safe! (:character (body! req ::model/character-put "Expected {:character {:name ... :version n}}")))
        cid #(uuid! (:id params))]
    (case op
      :characters/list (characters/mine db id)
      :characters/create (characters/create! db id (character))
      :characters/get (characters/fetch db id (cid))
      :characters/put (characters/save! db id (cid) (saved))
      :characters/delete (characters/delete! db images id (cid))
      :characters/campaign (characters/set-campaign! db id (cid)
                                                     (:campaign-id (body! req ::model/link-body "Expected {:campaign-id id-or-nil}")))
      :characters/portrait (characters/set-portrait! db images id (cid) (portrait-bytes req)))))

(defn- campaign-library
  "A member builds from the GM's library."
  [{:keys [db table]} user-id id]
  (go-try (<? (library/library table (<? (campaigns/library-owner db user-id id))))))

(defn- campaign-op [op {:keys [db] :as stores} u params req]
  (let [campaign #(json-safe! (:campaign (body! req ::model/campaign-body "Expected {:campaign {:name ...}}")))
        id #(uuid! (:id params))]
    (case op
      :campaigns/list (campaigns/mine db u)
      :campaigns/create (campaigns/create! db u (campaign))
      :campaigns/get (campaigns/fetch db (:id u) (id))
      :campaigns/put (campaigns/save! db (:id u) (id) (campaign))
      :campaigns/delete (campaigns/delete! db (:id u) (id))
      :campaigns/invite (campaigns/invite! db (:id u) (id))
      :campaigns/library (campaign-library stores (:id u) (id))
      :campaigns/manager (campaigns/manager db (:id u) (id))
      :campaigns/save-manager (campaigns/save-manager! db (:id u) (id) (json-text! req))
      :campaigns/manager-content (campaigns/manager-content db (:id u) (id))
      :campaigns/save-manager-content (campaigns/save-manager-content! db (:id u) (id) (json-text! req))
      :campaigns/party (campaigns/party db (:id u) (id))
      :campaigns/remove-member (campaigns/remove-member! db (:id u) (id) (check! ::model/owner (:member params) "Not a valid user"))
      :invites/get (campaigns/invite-info db (check! ::model/code (:code params) "Not a valid invite"))
      :invites/join (campaigns/join! db u (check! ::model/code (:code params) "Not a valid invite")))))

(defn- dispatch [op stores u params req]
  (case (namespace op)
    "library" (library-op op stores u params req)
    "characters" (character-op op stores u params req)
    ("campaigns" "invites") (campaign-op op stores u params req)
    (go {:user-id (:id u) :name (:name u)})))

(defn- run
  "The service call for a parsed request; yields the response map."
  [stores {:keys [method path] :as req}]
  (go-try
    (let [{:keys [op params]} (or (router/match routes method path) (throw (ex-info "Not found" {:status 404})))
          u (user req)]
      (check! (s/keys :req-un [::model/owner]) {:owner (:id u)} "Sign in first" 401)
      {:status 200 :body (<? (dispatch op stores u params req))})))

;; ---------- Lambda plumbing ----------

(defn- error-response [e]
  (let [{:keys [status] :as data} (ex-data e)]
    (if status
      {:status status :body (merge {:error (ex-message e)} (dissoc data :status))}
      (do (js/console.error "API request failed" e)
          {:status 500 :body {:error "Something went wrong on our side"}}))))

(defn handle
  "Request map in, channel of response map out. Never yields an error: failures become error responses."
  [stores req]
  (go (let [res (<! (run stores req))]
        (if (instance? js/Error res) (error-response res) res))))

(defn- event->request [event]
  (let [e (js->clj event)]
    {:method (get-in e ["requestContext" "http" "method"])
     :path (get e "rawPath")
     :headers (update-keys (get e "headers") str/lower-case)
     :claims (some-> (get-in e ["requestContext" "authorizer" "jwt" "claims"]) (update-keys keyword))
     :body (if (get e "isBase64Encoded") (.toString (js/Buffer.from (get e "body") "base64")) (get e "body"))}))

(defn- ->lambda
  "A string body is JSON text from a service; everything else goes out as EDN."
  [{:keys [status body]}]
  #js {:statusCode status
       :headers #js {"content-type" (if (string? body) "application/json; charset=utf-8" "application/edn; charset=utf-8")}
       :body (if (string? body) body (pr-str body))})

(defn handler
  "The Lambda entry point (exported by the :api build)."
  [event _context]
  (js/Promise.
   (fn [resolve _]
     (go (resolve (->lambda (<! (handle (current-stores) (event->request event)))))))))
