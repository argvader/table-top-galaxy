(ns ttg.api.dev
  "Runs the API Lambda on http://localhost:3000 against DynamoDB Local (the library) and PGlite in .local/pg
   (characters and campaigns), without the Serverless Framework. Each request becomes an API Gateway v2 event, so the
   code under test is the deployed handler. Portraits land in the web app's public folder, where its dev server finds them.
   The web app (localhost:8280) is let through CORS; shadow-cljs hot-reloads the handler while it runs."
  (:require ["fs" :as fs]
            ["http" :as http]
            ["path" :as path]
            [cljs.core.async :refer [go <!]]
            [ttg.api.async :refer [go-try]]
            [ttg.api.gateway :as gateway]
            [ttg.api.handler :as handler]
            [ttg.api.images :as images]
            [ttg.api.pglite :as pglite]))

(def port 3000)
(def allowed-origin "http://localhost:8280")
(def public-dir "apps/web/public")

(defrecord DiskImages []
  images/Images
  (put-image! [_ key bytes]
    (go-try
      (let [file (path/join public-dir key)]
        (fs/mkdirSync (path/dirname file) #js {:recursive true})
        (fs/writeFileSync file bytes)
        true)))
  (delete-image! [_ key]
    (go-try (fs/rmSync (path/join public-dir key) #js {:force true}) true)))

(defn- set-env-defaults! []
  (doseq [[k v] {"TABLE_NAME" "ttg-local" "DYNAMODB_ENDPOINT" "http://localhost:8000" "AWS_REGION" "us-east-1"
                 "AWS_ACCESS_KEY_ID" "local" "AWS_SECRET_ACCESS_KEY" "local" "ALLOW_DEV_OWNER" "true"}]
    (when-not (aget js/process.env k) (aset js/process.env k v))))

(def ^:private cors
  #js {"access-control-allow-origin" allowed-origin
       "access-control-allow-methods" "GET,PUT,POST,DELETE,OPTIONS"
       "access-control-allow-headers" "content-type,x-ttg-owner,x-ttg-name"
       "access-control-max-age" "600"})

(defn- event [^js req body]
  (let [url (js/URL. (.-url req) "http://localhost")]
    #js {:version "2.0"
         :rawPath (.-pathname url)
         :rawQueryString (subs (.-search url) (min 1 (count (.-search url))))
         :headers (.-headers req)
         :body body
         :isBase64Encoded false
         :requestContext #js {:http #js {:method (.-method req) :path (.-pathname url)}}}))

(defn- send! [^js res status headers body]
  (.writeHead res status (js/Object.assign #js {} cors headers))
  (.end res body))

(defn- on-request [^js req ^js res]
  (if (= "OPTIONS" (.-method req))
    (send! res 204 #js {} "")
    (let [chunks #js []]
      (.on req "data" #(.push chunks %))
      (.on req "end"
           (fn []
             (-> (handler/handler (event req (.toString (js/Buffer.concat chunks) "utf8")) nil)
                 (.then (fn [^js r] (send! res (.-statusCode r) (.-headers r) (.-body r))))
                 (.catch (fn [e] (js/console.error e) (send! res 500 #js {} "Dev server error")))))))))

(defonce ^:private server (atom nil))

(defn main [& _]
  (set-env-defaults!)
  (go
    (let [db (<! (pglite/pglite {:migrations-dir "apps/serverless/migrations" :data-dir ".local/pg"}))]
      (if (instance? js/Error db)
        (do (js/console.error "Can't open the dev database" db) (js/process.exit 1))
        (do (handler/use-stores! {:table (gateway/dynamo-table) :db db :images (->DiskImages)})
            (reset! server (doto (http/createServer #(on-request %1 %2)) (.listen port)))
            (println (str "API on http://localhost:" port " (DynamoDB " (aget js/process.env "DYNAMODB_ENDPOINT")
                          ", Postgres in .local/pg)")))))))

(defn reloaded! [] (println "API reloaded"))
