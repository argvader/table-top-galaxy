(ns ttg.http
  "The ::request effect: fetch against the API, EDN both ways (entries keep their keywords).
   {:method \"PUT\" :path \"/library/talents/x\" :body {...} :on-ok [event] :on-fail [event]}
   on-ok gets the decoded body appended; on-fail gets {:status n :error \"message\" ...}.
   :json? true sends and reads JSON instead, for the manager's documents."
  (:require [cljs.reader :as reader]
            [re-frame.core :as rf]
            [ttg.auth :as auth]
            [ttg.config :as config]))

(defn configured? [] (seq config/api-url))

(defn- headers [token json?]
  (cond-> {"content-type" (if json? "application/json" "application/edn") "accept" "application/edn"}
    token (assoc "authorization" (str "Bearer " token))
    (and (not token) (seq config/dev-owner)) (assoc "x-ttg-owner" config/dev-owner)))

(defn- decode [text]
  (try (reader/read-string text) (catch :default _ {:error "The server sent something unreadable"})))

(defn parse-json [text] (js->clj (js/JSON.parse text) :keywordize-keys true))

(defn- decode-json [text]
  (try (parse-json text) (catch :default _ (decode text))))

(defn- fail [on-fail data] (when on-fail (rf/dispatch (conj on-fail data))))

(defn- fetch! [method path body token json?]
  (js/fetch (str config/api-url path)
            (clj->js (cond-> {:method (or method "GET") :headers (headers token json?)}
                       (some? body) (assoc :body (if json? (js/JSON.stringify (clj->js body)) (pr-str body)))))))

(defn- request! [{:keys [method path body json? on-ok on-fail]}]
  (-> (if (auth/enabled?) (auth/token) (js/Promise.resolve nil))
      (.then (fn [token]
               (if (and (auth/enabled?) (not token))
                 (throw (ex-info "Sign in first" {:status 401}))
                 (fetch! method path body token json?))))
      (.then (fn [^js res]
               (.then (.text res)
                      (fn [text]
                        (let [data ((if (and json? (.-ok res)) decode-json decode) text)]
                          (if (.-ok res)
                            (when on-ok (rf/dispatch (conj on-ok data)))
                            (fail on-fail (assoc data :status (.-status res)))))))))
      (.catch (fn [e] (fail on-fail (if (= 401 (:status (ex-data e)))
                                      {:status 401 :error "Sign in to continue."}
                                      {:status 0 :error "Can't reach the server"}))))))

(rf/reg-fx ::request request!)
