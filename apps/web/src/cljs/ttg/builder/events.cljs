(ns ttg.builder.events
  "Builder events. Handlers are plain named functions (tested directly). The rules live in ttg.genesys.rules, so most
   changes are one event: ::change applies a function to the character, and ::change-with-library also hands it the
   library. Dice arrive as the ::d100 coeffect, so rolling handlers stay pure."
  (:require [clojure.spec.alpha :as s]
            [re-frame.core :as rf]
            [ttg.builder.db :as db]
            [ttg.content :as content]
            [ttg.genesys.rules :as rules]
            [ttg.http :as http]
            [ttg.routes :as routes]))

(defn- check-and-throw [spec db]
  (when-not (s/valid? spec db)
    (throw (ex-info (str "app-db failed spec: " (s/explain-str spec db)) {:db db}))))

(def validate-spec
  (if goog.DEBUG
    (rf/after (partial check-and-throw ::db/app-db))
    []))

(defn draft-key [campaign-id] (str "ttg.draft." (or campaign-id "none")))

;; The draft for the campaign named in the event being handled.
(rf/reg-cofx ::stored-draft
  (fn [{[_ campaign-id] :event :as cofx} _]
    (assoc cofx :stored-draft
           (try (some-> (.getItem js/localStorage (draft-key campaign-id)) js/JSON.parse (js->clj :keywordize-keys true))
                (catch :default _ nil)))))

(rf/reg-cofx ::d100
  (fn [cofx _]
    (assoc cofx :d100 (inc (mod (aget (js/crypto.getRandomValues (js/Uint32Array. 1)) 0) 100)))))

(rf/reg-fx ::store-draft
  (fn [[campaign-id character]]
    (try (if character
           (.setItem js/localStorage (draft-key campaign-id) (js/JSON.stringify (clj->js character)))
           (.removeItem js/localStorage (draft-key campaign-id)))
         (catch :default _ nil))))

(rf/reg-fx ::scroll-top (fn [_] (js/window.scrollTo 0 0)))

(defn open-new
  "Picks up the draft for this campaign, if there is one, and loads its library."
  [{:keys [db stored-draft]} [_ campaign-id]]
  {:db (assoc db :builder (cond-> (assoc (db/default-builder) :campaign-id campaign-id)
                            stored-draft (assoc :character (merge (rules/blank-character) stored-draft))))
   :dispatch [::content/load campaign-id]})

(defn open-existing [{:keys [db]} [_ id]]
  {:db (assoc db :builder (assoc (db/default-builder) :editing? true :visited (set (range (count rules/steps)))))
   ::http/request {:path (str "/characters/" id) :on-ok [::existing-loaded] :on-fail [::load-failed]}})

(defn existing-loaded [{:keys [db]} [_ {:keys [character]}]]
  {:db (update db :builder assoc :character (merge (rules/blank-character) character) :campaign-id (:campaign-id character))
   :dispatch [::content/load (:campaign-id character)]})

(defn- changed
  "A new character for the draft: remembered in the browser unless this one is already saved."
  [db character]
  (let [{:keys [campaign-id editing?]} (:builder db)]
    (cond-> {:db (assoc-in db [:builder :character] character)}
      (not editing?) (assoc ::store-draft [campaign-id character]))))

(defn change [{:keys [db]} [_ f & args]]
  (changed db (apply f (get-in db [:builder :character]) args)))

(defn change-with-library [{:keys [db]} [_ f & args]]
  (changed db (apply f (get-in db [:builder :character]) (get-in db [:content :ix]) args)))

(defn roll-obligation [{:keys [db d100]} _]
  (let [{:keys [id]} (first (filter #(rules/in-roll? d100 (:roll %)) (get-in db [:content :ix :obligations])))]
    (changed db (update (get-in db [:builder :character]) :ob assoc :type (name id) :rolled d100))))

(defn roll-pocket [{:keys [db d100]} _]
  (changed db (assoc (get-in db [:builder :character]) :pocket d100)))

(defn roll-motive
  "A roll that lands outside the type's table takes its first entry, as the prototype does."
  [{:keys [db d100]} [_ type]]
  (let [table (filter #(= type (:type %)) (get-in db [:content :ix :motivations]))
        {:keys [name text]} (or (first (filter #(rules/in-roll? d100 (:roll %)) table)) (first table))]
    (changed db (assoc-in (get-in db [:builder :character]) [:motives (rules/motive-key type)] {:name name :text text}))))

(defn go [{:keys [db]} [_ step tab]]
  (let [{:keys [step] :as builder} (update (:builder db) :step (constantly (max 0 (min (dec (count rules/steps)) step))))]
    {:db (assoc db :builder (cond-> (-> builder (update :visited conj (:step (:builder db)) step) (assoc :preview? false))
                              tab (assoc :xp-tab tab)))
     ::scroll-top true}))

(defn set-ui [db [_ k v]] (assoc-in db [:builder k] v))

(defn restart [{:keys [db]} _]
  (let [campaign-id (get-in db [:builder :campaign-id])]
    {:db (assoc db :builder (assoc (db/default-builder) :campaign-id campaign-id))
     ::store-draft [campaign-id nil]}))

(defn- link-request [campaign-id id]
  {:method "PUT" :path (str "/characters/" id "/campaign") :body {:campaign-id campaign-id}
   :on-ok [::saved] :on-fail [::save-failed]})

(defn- save-request
  "A character that exists but hasn't reached its campaign only needs linking: its first link failed, and creating it
   again would make a twin."
  [{:keys [character editing? campaign-id]}]
  (let [{:keys [id]} character]
    (cond (and id campaign-id (not= campaign-id (:campaign-id character))) (link-request campaign-id id)
          editing? {:method "PUT" :path (str "/characters/" id) :body {:character character}
                    :on-ok [::saved] :on-fail [::save-failed]}
          :else {:method "POST" :path "/characters" :body {:character character}
                 :on-ok [::created] :on-fail [::save-failed]})))

(defn save
  "Creates the character, or updates it when editing. Nothing goes while a block remains."
  [{:keys [db]} _]
  (let [{:keys [character saving?] :as builder} (:builder db)
        ix (get-in db [:content :ix])]
    (when-not (or saving? (seq (rules/blocks (rules/issues character ix))))
      {:db (update db :builder assoc :saving? true :error nil)
       ::http/request (save-request builder)})))

(defn created
  "From here it's a saved character. The draft goes, and it joins the campaign it was built for."
  [{:keys [db]} [_ {:keys [character]}]]
  (let [campaign-id (get-in db [:builder :campaign-id])]
    (cond-> {:db (update db :builder assoc :character character :editing? true)
             ::store-draft [campaign-id nil]}
      campaign-id (assoc ::http/request (link-request campaign-id (:id character)))
      (nil? campaign-id) (assoc :dispatch [::saved {:character character}]))))

(defn saved [{:keys [db]} [_ {:keys [character]}]]
  {:db (assoc-in db [:builder :saving?] false)
   ::routes/navigate {:page :sheet :id (:id character)}})

(defn save-failed [db [_ {:keys [error]}]]
  (update db :builder assoc :saving? false :error (or error "That didn't save. Try again.")))

(defn load-failed [db [_ {:keys [error]}]]
  (assoc-in db [:builder :load-error] (or error "That character didn't load.")))

(rf/reg-event-db ::initialize (fn [db _] (assoc db :builder (db/default-builder))))
(rf/reg-event-fx ::open-new [(rf/inject-cofx ::stored-draft) validate-spec] open-new)
(rf/reg-event-fx ::open-existing [validate-spec] open-existing)
(rf/reg-event-fx ::existing-loaded [validate-spec] existing-loaded)
(rf/reg-event-fx ::change [validate-spec] change)
(rf/reg-event-fx ::change-with-library [validate-spec] change-with-library)
(rf/reg-event-fx ::roll-obligation [(rf/inject-cofx ::d100) validate-spec] roll-obligation)
(rf/reg-event-fx ::roll-pocket [(rf/inject-cofx ::d100) validate-spec] roll-pocket)
(rf/reg-event-fx ::roll-motive [(rf/inject-cofx ::d100) validate-spec] roll-motive)
(rf/reg-event-fx ::go [validate-spec] go)
(rf/reg-event-db ::set-ui [validate-spec] set-ui)
(rf/reg-event-fx ::restart [validate-spec] restart)
(rf/reg-event-fx ::save [validate-spec] save)
(rf/reg-event-fx ::created [validate-spec] created)
(rf/reg-event-fx ::saved [validate-spec] saved)
(rf/reg-event-db ::save-failed [validate-spec] save-failed)
(rf/reg-event-db ::load-failed [validate-spec] load-failed)
