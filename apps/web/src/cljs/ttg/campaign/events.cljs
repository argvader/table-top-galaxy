(ns ttg.campaign.events
  "Campaigns, invites and the list of your characters. Handlers are plain named functions (tested directly)."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [ttg.campaign.db :as db]
            [ttg.http :as http]
            [ttg.routes :as routes]
            [ttg.story.events :as story-ev]))

(defn- check-and-throw [spec db]
  (when-not (s/valid? spec db)
    (throw (ex-info (str "app-db failed spec: " (s/explain-str spec db)) {:db db}))))

(def validate-spec
  (if goog.DEBUG
    (rf/after (partial check-and-throw ::db/app-db))
    []))

(defn- failed [db [_ {:keys [error]}]]
  (update db :table assoc :status :error :error (or error "Something went wrong")))

(defn load-home [{:keys [db]} _]
  {:db (update db :table assoc :status :loading :error nil :current nil :invite-code nil)
   :fx [[::http/request {:path "/campaigns" :on-ok [::campaigns-loaded] :on-fail [::failed]}]
        [::http/request {:path "/characters" :on-ok [::characters-loaded] :on-fail [::failed]}]]})

(defn campaigns-loaded [db [_ {:keys [campaigns]}]]
  (update db :table assoc :status :ready :campaigns (vec campaigns)))

(defn load-roles
  "Only the campaign list, for the top bar on any page. It leaves the open page's status alone, and a failure changes nothing."
  [_ _]
  {::http/request {:path "/campaigns" :on-ok [::roles-loaded]}})

(defn roles-loaded [db [_ {:keys [campaigns]}]]
  (assoc-in db [:table :campaigns] (vec campaigns)))

(defn characters-loaded [db [_ {:keys [characters]}]]
  (update db :table assoc :characters (vec characters)))

(defn create
  "Blank names stay in the form."
  [{:keys [db]} [_ campaign-name]]
  (when-not (str/blank? campaign-name)
    {:db (update db :table assoc :error nil)
     ::http/request {:method "POST" :path "/campaigns" :body {:campaign {:name (str/trim campaign-name)}}
                     :on-ok [::created] :on-fail [::failed]}}))

(defn created [_ [_ {:keys [campaign]}]]
  {::routes/navigate {:page :campaign :id (:id campaign)}
   :dispatch [::load-roles]})

(defn load-campaign [{:keys [db]} [_ id]]
  {:db (update db :table assoc :status :loading :error nil :current nil :invite-code nil :copied? false)
   :fx [[::http/request {:path (str "/campaigns/" id) :on-ok [::campaign-loaded] :on-fail [::failed]}]
        [::http/request {:path "/characters" :on-ok [::characters-loaded] :on-fail [::failed]}]]})

(defn campaign-loaded [{:keys [db]} [_ campaign]]
  {:db (update db :table assoc :status :ready :current campaign)
   :fx [[:dispatch [::story-ev/load (get-in campaign [:campaign :id])]]]})

(defn make-invite [{:keys [db]} _]
  {::http/request {:method "POST" :path (str "/campaigns/" (get-in db [:table :current :campaign :id]) "/invites")
                   :on-ok [::invite-made] :on-fail [::failed]}})

(defn invite-made [db [_ {:keys [code]}]]
  (update db :table assoc :invite-code code :copied? false))

(defn remove-member [{:keys [db]} [_ member-id]]
  (let [id (get-in db [:table :current :campaign :id])]
    {::http/request {:method "DELETE" :path (str "/campaigns/" id "/members/" member-id)
                     :on-ok [::load-campaign id] :on-fail [::failed]}}))

(defn add-character [{:keys [db]} [_ character-id]]
  (let [id (get-in db [:table :current :campaign :id])]
    {::http/request {:method "PUT" :path (str "/characters/" character-id "/campaign") :body {:campaign-id id}
                     :on-ok [::load-campaign id] :on-fail [::failed]}}))

(defn load-invite [{:keys [db]} [_ code]]
  {:db (update db :table assoc :status :loading :error nil :join {:code code})
   ::http/request {:path (str "/invites/" code) :on-ok [::invite-loaded] :on-fail [::failed]}})

(defn invite-loaded [db [_ {:keys [campaign-name]}]]
  (-> db (assoc-in [:table :status] :ready) (assoc-in [:table :join :campaign-name] campaign-name)))

(defn join [{:keys [db]} _]
  {::http/request {:method "POST" :path (str "/invites/" (get-in db [:table :join :code]) "/join")
                   :on-ok [::joined] :on-fail [::failed]}})

(defn joined [_ [_ {:keys [campaign-id]}]]
  {::routes/navigate {:page :campaign :id campaign-id}})

(rf/reg-fx ::copy-text (fn [text] (some-> js/navigator.clipboard (.writeText text))))

(defn copy-invite [{:keys [db]} [_ url]]
  {:db (assoc-in db [:table :copied?] true) ::copy-text url})

(rf/reg-event-db ::initialize (fn [db _] (assoc db :table db/default-table)))
(rf/reg-event-db ::failed [validate-spec] failed)
(rf/reg-event-fx ::load-home [validate-spec] load-home)
(rf/reg-event-db ::campaigns-loaded [validate-spec] campaigns-loaded)
(rf/reg-event-fx ::load-roles load-roles)
(rf/reg-event-db ::roles-loaded [validate-spec] roles-loaded)
(rf/reg-event-db ::characters-loaded [validate-spec] characters-loaded)
(rf/reg-event-fx ::create [validate-spec] create)
(rf/reg-event-fx ::created created)
(rf/reg-event-fx ::load-campaign [validate-spec] load-campaign)
(rf/reg-event-fx ::campaign-loaded [validate-spec] campaign-loaded)
(rf/reg-event-fx ::make-invite make-invite)
(rf/reg-event-db ::invite-made [validate-spec] invite-made)
(rf/reg-event-fx ::remove-member remove-member)
(rf/reg-event-fx ::add-character add-character)
(rf/reg-event-fx ::load-invite [validate-spec] load-invite)
(rf/reg-event-db ::invite-loaded [validate-spec] invite-loaded)
(rf/reg-event-fx ::join join)
(rf/reg-event-fx ::joined joined)
(rf/reg-event-fx ::copy-invite [validate-spec] copy-invite)
