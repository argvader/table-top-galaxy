(ns ttg.chronicle.events
  "Loads the Chronicle: the list of pages, or one page."
  (:require [clojure.spec.alpha :as s]
            [re-frame.core :as rf]
            [ttg.chronicle.db :as db]
            [ttg.http :as http]))

(defn- check-and-throw [spec db]
  (when-not (s/valid? spec db)
    (throw (ex-info (str "app-db failed spec: " (s/explain-str spec db)) {:db db}))))

(def validate-spec
  (if goog.DEBUG
    (rf/after (partial check-and-throw ::db/app-db))
    []))

(defn open [{:keys [db]} [_ id kind slug]]
  (let [base (str "/campaigns/" id "/chronicle")]
    {:db (assoc db :chronicle (assoc db/default-chronicle :status :loading :campaign-id id :kind kind :slug slug))
     ::http/request (if slug
                      {:path (str base "/" kind "/" slug) :on-ok [::entry-loaded] :on-fail [::failed]}
                      {:path base :on-ok [::index-loaded] :on-fail [::failed]})}))

(defn index-loaded [db [_ {:keys [entries]}]]
  (if (get-in db [:chronicle :slug])
    db
    (update db :chronicle assoc :status :ready :entries (vec entries))))

(defn entry-loaded [db [_ {:keys [entry]}]]
  (let [{:keys [kind slug]} (:chronicle db)]
    (if (and (= kind (:kind entry)) (= slug (:slug entry)))
      (update db :chronicle assoc :status :ready :entry entry)
      db)))

(defn failed [db [_ {:keys [error]}]]
  (update db :chronicle assoc :status :error :error (or error "The Chronicle didn't load.")))

(rf/reg-event-db ::initialize (fn [db _] (assoc db :chronicle db/default-chronicle)))
(rf/reg-event-fx ::open [validate-spec] open)
(rf/reg-event-db ::index-loaded [validate-spec] index-loaded)
(rf/reg-event-db ::entry-loaded [validate-spec] entry-loaded)
(rf/reg-event-db ::failed [validate-spec] failed)
