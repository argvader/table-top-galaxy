(ns ttg.content
  "The library the builder and sheet read: the campaign GM's, or your own outside a campaign. Book content reaches the
   browser only this way, after sign-in, so none of it ships in the bundle. Kept under :content as a rules index."
  (:require [re-frame.core :as rf]
            [ttg.genesys.rules :as rules]
            [ttg.http :as http]))

(def default-content {:status :idle :campaign-id nil :ix nil :error nil})

(defn load-content
  "Does nothing when that library is already here or on its way."
  [{:keys [db]} [_ campaign-id]]
  (let [{:keys [status] loaded :campaign-id} (:content db)]
    (when-not (and (= loaded campaign-id) (#{:loading :ready} status))
      {:db (assoc db :content (assoc default-content :status :loading :campaign-id campaign-id))
       ::http/request {:path (if campaign-id (str "/campaigns/" campaign-id "/library") "/library")
                       :on-ok [::loaded] :on-fail [::failed]}})))

(defn loaded [db [_ {:keys [entries]}]]
  (update db :content assoc :status :ready :ix (rules/index entries)))

(defn failed [db [_ {:keys [error]}]]
  (update db :content assoc :status :error :error error))

(rf/reg-event-db ::initialize (fn [db _] (assoc db :content default-content)))
(rf/reg-event-fx ::load load-content)
(rf/reg-event-db ::loaded loaded)
(rf/reg-event-db ::failed failed)

(rf/reg-sub ::ix (fn [db _] (get-in db [:content :ix])))
(rf/reg-sub ::status (fn [db _] (get-in db [:content :status])))
(rf/reg-sub ::error (fn [db _] (get-in db [:content :error])))
