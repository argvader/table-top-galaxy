(ns ttg.sheet.events
  "The sheet: load, change, autosave. Each change bumps :dirty and schedules a ::flush; a flush only sends if it's the
   latest, so typing makes one save rather than one per key. A save that finds newer edits sends again."
  (:require [clojure.spec.alpha :as s]
            [re-frame.core :as rf]
            [ttg.content :as content]
            [ttg.genesys.rules :as rules]
            [ttg.http :as http]
            [ttg.sheet.db :as db]
            [ttg.roller.events :as roller-ev]
            [ttg.story.events :as story-ev]
            [ttg.ui.drawer-state :as drawer]
            [ttg.ui.portrait :as portrait]))

(def ^:private save-delay-ms 800)

(defn- check-and-throw [spec db]
  (when-not (s/valid? spec db)
    (throw (ex-info (str "app-db failed spec: " (s/explain-str spec db)) {:db db}))))

(def validate-spec
  (if goog.DEBUG
    (rf/after (partial check-and-throw ::db/app-db))
    []))

(rf/reg-cofx ::now (fn [cofx _] (assoc cofx :now (.toISOString (js/Date.)))))

(rf/reg-fx ::prepare-portrait
  (fn [file]
    (-> (portrait/prepare file)
        (.then #(rf/dispatch [::portrait-ready %]))
        (.catch #(rf/dispatch [::upload-failed {:error (.-message %)}])))))

(defn open [{:keys [db]} [_ id]]
  {:db (assoc db :sheet (assoc db/default-sheet :status :loading))
   ::http/request {:path (str "/characters/" id) :on-ok [::loaded] :on-fail [::load-failed]}})

(defn loaded [{:keys [db]} [_ {:keys [character]}]]
  {:db (update db :sheet assoc :status :ready :character (merge (rules/blank-character) character))
   :fx [[:dispatch [::content/load (:campaign-id character)]]
        [:dispatch [::story-ev/load (:campaign-id character)]]]})

(defn load-failed [db [_ {:keys [error]}]]
  (update db :sheet assoc :status :error :error (or error "That character didn't load.")))

(defn change
  "Applies f to the character and schedules a save."
  [{:keys [db]} [_ f & args]]
  (let [db (-> db
               (update-in [:sheet :character] #(apply f % args))
               (update-in [:sheet :dirty] inc))]
    {:db db
     :dispatch-later {:ms save-delay-ms :dispatch [::flush (get-in db [:sheet :dirty])]}}))

(defn- unsaved? [{:keys [dirty sent]}] (> dirty sent))

(defn- save-request [{:keys [character]}]
  {:method "PUT" :path (str "/characters/" (:id character)) :body {:character character}
   :on-ok [::saved] :on-fail [::save-failed]})

(defn- send-now
  "Nothing to send, a save already out (its reply sends again) or a known conflict all mean wait."
  [db]
  (let [{:keys [saving? conflict? dirty] :as sheet} (:sheet db)]
    (when (and (unsaved? sheet) (not saving?) (not conflict?))
      {:db (update db :sheet assoc :saving? true :sent dirty :error nil)
       ::http/request (save-request sheet)})))

(defn flush-edits [{:keys [db]} [_ n]]
  (when (= n (get-in db [:sheet :dirty])) (send-now db)))

(defn flush-now
  "Leaving the page doesn't wait for the debounce."
  [{:keys [db]} _]
  (send-now db))

(defn saved
  "Only the version comes back into the sheet: anything typed while this save was out stays. A reply for a character
   that's no longer open is ignored."
  [{:keys [db]} [_ {:keys [character]}]]
  (when (= (:id character) (get-in db [:sheet :character :id]))
    (let [db (update db :sheet (fn [sheet] (-> sheet
                                               (assoc :saving? false)
                                               (update :character assoc :version (:version character)
                                                       :updated-at (:updated-at character)))))]
      (or (send-now db) {:db db}))))

(defn save-failed [db [_ {:keys [status error]}]]
  (if (= 409 status)
    (update db :sheet assoc :saving? false :conflict? true)
    (update db :sheet assoc :saving? false :sent 0 :error (or error "That didn't save. Your changes are still here."))))

(defn retry [{:keys [db]} _] (send-now db))

(defn set-ui [db [_ k v]] (assoc-in db [:sheet k] v))

(defn take-hit
  "Damage less soak, as wounds. Anything that isn't a positive number does nothing."
  [{:keys [db]} _]
  (let [{:keys [character hit]} (:sheet db)
        n (js/parseInt hit 10)
        soak (:soak (rules/derived character (get-in db [:content :ix])))]
    (when (pos? n)
      (let [taken (max 0 (- n soak))]
        (change {:db (assoc-in db [:sheet :hit] "")} [::change update :wounds-taken + taken])))))

(defn apply-effect
  "A spend or recovery from the roll drawer changes the character: {:strain n}, negative for strain recovered.
   Strain stays at 0 or more. Someone who can only read this sheet changes nothing."
  [{:keys [db] :as cofx} [_ {:keys [strain]}]]
  (when (and strain (db/writable? db) (get-in db [:sheet :character]))
    (change cofx [::change update :strain-taken #(max 0 (+ (or % 0) strain))])))

(defn- discouraging-wound-fx
  "Discouraging Wound moves a player story point to the GM (CRB p.115), when the table has one."
  [db crit]
  (when (and (= "discouraging-wound" (name (:id crit))) (pos? (get-in db [:story :pool :player] 0)))
    [[:dispatch [::story-ev/spend {:side "player" :note "Discouraging Wound: a player story point moved to the GM."}]]]))

(defn roll-crit
  "raw is the d100. A library with no injury table for the total does nothing."
  [{:keys [db now]} [_ raw]]
  (let [c (get-in db [:sheet :character])
        bonus (rules/crit-bonus c)
        total (+ raw bonus)]
    (when-let [crit (rules/crit-for (get-in db [:content :ix]) total)]
      (let [title (:name crit)
            injury {:id (name (:id crit)) :name title :roll total :at now}]
        (-> (change {:db (assoc-in db [:sheet :last-crit] {:raw raw :bonus bonus :total total :name title})}
                    [::change update :crits (fnil conj []) injury])
            (assoc :fx (discouraging-wound-fx db crit)))))))

(defn- without [v i] (vec (concat (subvec v 0 i) (subvec v (inc i)))))

(defn heal-crit
  "i is the injury's place on the sheet: a character can carry the same injury twice."
  [{:keys [db]} [_ i]]
  (when (get-in db [:sheet :character :crits i])
    (change {:db db} [::change update :crits without i])))

(defn upload [{:keys [db]} [_ file]]
  {:db (update db :sheet assoc :uploading? true :error nil)
   ::prepare-portrait file})

(defn portrait-ready [{:keys [db]} [_ image]]
  {::http/request {:method "PUT" :path (str "/characters/" (get-in db [:sheet :character :id]) "/portrait")
                   :body {:image image} :on-ok [::portrait-saved] :on-fail [::upload-failed]}})

(defn portrait-saved
  "The portrait isn't part of the versioned save, so nothing else about the sheet changes."
  [db [_ {:keys [character]}]]
  (update db :sheet (fn [sheet] (-> sheet
                                    (assoc :uploading? false)
                                    (assoc-in [:character :portrait-key] (:portrait-key character))))))

(defn upload-failed [db [_ {:keys [error]}]]
  (update db :sheet assoc :uploading? false :error (or error "That picture didn't upload.")))
;; ---------- The info drawer ----------

(defn open-info
  "Opens the info drawer on a carried item or owned talent: kind is :gear or :talent, id its library id."
  [db [_ kind id]]
  (drawer/open db [:sheet :info] kind id))

(defn dismiss-info [{:keys [db]} _] (drawer/dismiss db [:sheet :info] ::close-info))

(defn close-info [db _] (drawer/close db [:sheet :info]))

(defn attack-from-info
  "The drawer's Attack button: the info drawer slides away and the roll drawer opens on the attack."
  [{:keys [db] :as cofx} [_ check]]
  (-> (or (dismiss-info cofx nil) {:db db})
      (update :fx (fnil conj []) [:dispatch [::roller-ev/open-check check]])))

(rf/reg-event-db ::initialize (fn [db _] (assoc db :sheet db/default-sheet)))
(rf/reg-event-fx ::open [validate-spec] open)
(rf/reg-event-fx ::loaded [validate-spec] loaded)
(rf/reg-event-db ::load-failed [validate-spec] load-failed)
(rf/reg-event-fx ::change [validate-spec] change)
(rf/reg-event-fx ::flush [validate-spec] flush-edits)
(rf/reg-event-fx ::flush-now [validate-spec] flush-now)
(rf/reg-event-fx ::saved [validate-spec] saved)
(rf/reg-event-db ::save-failed [validate-spec] save-failed)
(rf/reg-event-fx ::retry [validate-spec] retry)
(rf/reg-event-db ::set-ui [validate-spec] set-ui)
(rf/reg-event-fx ::take-hit [validate-spec] take-hit)
(rf/reg-event-fx ::roll-crit [(rf/inject-cofx ::now) validate-spec] roll-crit)
(rf/reg-event-fx ::apply-effect [validate-spec] apply-effect)
(rf/reg-event-fx ::heal-crit [validate-spec] heal-crit)
(rf/reg-event-fx ::upload [validate-spec] upload)
(rf/reg-event-fx ::portrait-ready [validate-spec] portrait-ready)
(rf/reg-event-db ::portrait-saved [validate-spec] portrait-saved)
(rf/reg-event-db ::upload-failed [validate-spec] upload-failed)
(rf/reg-event-db ::open-info [validate-spec] open-info)
(rf/reg-event-fx ::dismiss-info [validate-spec] dismiss-info)
(rf/reg-event-db ::close-info [validate-spec] close-info)
(rf/reg-event-fx ::attack-from-info [validate-spec] attack-from-info)
