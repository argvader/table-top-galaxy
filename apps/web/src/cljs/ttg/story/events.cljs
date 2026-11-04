(ns ttg.story.events
  "Loading, setting and spending the story point pool. A spend goes to the server first, and whatever it was for
   (an upgraded check, a Discouraging Wound) happens only once the server has moved the point: on-ok is dispatched
   then. A refused spend (none left) refreshes the count instead."
  (:require [clojure.spec.alpha :as s]
            [re-frame.core :as rf]
            [ttg.http :as http]
            [ttg.story.db :as db]))

(defn- check-and-throw [spec db]
  (when-not (s/valid? spec db)
    (throw (ex-info (str "app-db failed spec: " (s/explain-str spec db)) {:db db}))))

(def validate-spec
  (if goog.DEBUG
    (rf/after (partial check-and-throw ::db/app-db))
    []))

(defn- path [id & more] (apply str "/campaigns/" id "/story" more))

(defn load-pool
  "Fetches the pool for a campaign; nil (a character in no campaign) clears it."
  [{:keys [db]} [_ campaign-id]]
  (cond-> {:db (assoc db :story (assoc db/default-story :campaign-id campaign-id))}
    campaign-id (assoc ::http/request {:path (path campaign-id) :on-ok [::loaded campaign-id] :on-fail [::failed campaign-id]})))

(defn loaded
  "A reply for a campaign that's no longer the one shown is ignored."
  [db [_ campaign-id {:keys [story]}]]
  (cond-> db
    (= campaign-id (get-in db [:story :campaign-id])) (update :story assoc :pool story :busy? false)))

(defn failed [db [_ campaign-id {:keys [error]}]]
  (cond-> db
    (= campaign-id (get-in db [:story :campaign-id])) (update :story assoc :busy? false :note error)))

(defn set-pool
  "The GM sets the pool: a new session, or a correction."
  [{:keys [db]} [_ pool]]
  (let [id (get-in db [:story :campaign-id])]
    (when (and id (not (get-in db [:story :busy?])))
      {:db (update db :story assoc :busy? true :note nil)
       ::http/request {:method "PUT" :path (path id) :body {:story pool}
                       :on-ok [::loaded id] :on-fail [::failed id]}})))

(defn spend
  "Spends one point from side (\"player\" or \"gm\"). note says what for; on-ok follows once it's spent."
  [{:keys [db]} [_ {:keys [side note on-ok]}]]
  (let [id (get-in db [:story :campaign-id])]
    (when (and id (get-in db [:story :pool]) (not (get-in db [:story :busy?])))
      {:db (update db :story assoc :busy? true :note nil)
       ::http/request {:method "POST" :path (path id "/spend") :body {:side side}
                       :on-ok [::spent id note on-ok] :on-fail [::spend-failed id]}})))

(defn spent [{:keys [db]} [_ id note on-ok reply]]
  (when (= id (get-in db [:story :campaign-id]))
    (cond-> {:db (-> db (loaded [nil id reply]) (assoc-in [:story :note] note))}
      on-ok (assoc :fx [[:dispatch on-ok]]))))

(defn spend-failed
  "Someone else spent the last one: say so, and fetch the count as it is now."
  [{:keys [db]} [_ id {:keys [error]}]]
  (when (= id (get-in db [:story :campaign-id]))
    {:db (update db :story assoc :busy? false :note error)
     ::http/request {:path (path id) :on-ok [::loaded id] :on-fail [::failed id]}}))

(rf/reg-event-db ::initialize (fn [db _] (assoc db :story db/default-story)))
(rf/reg-event-fx ::load [validate-spec] load-pool)
(rf/reg-event-db ::loaded [validate-spec] loaded)
(rf/reg-event-db ::failed [validate-spec] failed)
(rf/reg-event-fx ::set-pool [validate-spec] set-pool)
(rf/reg-event-fx ::spend [validate-spec] spend)
(rf/reg-event-fx ::spent [validate-spec] spent)
(rf/reg-event-fx ::spend-failed [validate-spec] spend-failed)
