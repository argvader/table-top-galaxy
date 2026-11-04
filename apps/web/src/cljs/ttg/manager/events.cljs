(ns ttg.manager.events
  "Opening a campaign's manager and saving it. Each edit goes to this browser straight away, marked unsent, and to the
   server after a pause. A save that doesn't arrive stays marked, and the next visit sends it instead of loading over it."
  (:require [clojure.spec.alpha :as s]
            [re-frame.core :as rf]
            [ttg.dice.rng :as rng]
            [ttg.http :as http]
            [ttg.manager.db :as db]
            [ttg.manager.doc :as doc]
            [ttg.manager.party :as party]))

(def ^:private save-delay-ms 1000)

(defn- check-and-throw [spec db]
  (when-not (s/valid? spec (:manager db))
    (throw (ex-info (str "manager failed spec: " (s/explain-str spec (:manager db))) {}))))

(def validate-spec
  (if goog.DEBUG
    (rf/after (partial check-and-throw ::db/manager))
    []))

(defn- doc-key [id] (str "ttg-campaign-" id "-v1"))
(defn- unsent-key [id] (str "ttg-campaign-unsent-" id))
(defn- manager-path [id] (str "/campaigns/" id "/manager"))

;; ---------- Browser copy ----------

(defn- stored-unsent
  "The document this browser hasn't managed to send, if there is one."
  [id]
  (when (.getItem js/localStorage (unsent-key id))
    (some-> (.getItem js/localStorage (doc-key id)) http/parse-json)))

(rf/reg-cofx ::unsent
  (fn [{:keys [event] :as cofx} _] (assoc cofx :unsent (stored-unsent (second event)))))

(rf/reg-fx ::keep-copy
  (fn [{:keys [id doc]}]
    (.setItem js/localStorage (doc-key id) (js/JSON.stringify (clj->js doc)))
    (.setItem js/localStorage (unsent-key id) "1")))

(rf/reg-fx ::sent-all (fn [id] (.removeItem js/localStorage (unsent-key id))))

;; ---------- Opening ----------

(defn- load-request [id path k]
  [::http/request (cond-> {:path (str (manager-path id) path) :on-ok [::got k] :on-fail [::load-failed]}
                    (not= k :party) (assoc :json? true))])

(defn open
  "Three requests: the document, the written prep and the party. ::got starts the page when all are in."
  [{:keys [db unsent]} [_ id]]
  {:db (assoc db :manager (assoc db/default-manager :status :loading :campaign-id id :pending {:unsent unsent}))
   :fx [(load-request id "" :saved)
        (load-request id "/content" :content)
        [::http/request {:path (str "/campaigns/" id "/party") :on-ok [::got :party] :on-fail [::load-failed]}]]})

(defn- send-now
  "Sends the document unless a save is already on its way; its reply sends anything newer."
  [{:keys [manager] :as db}]
  (let [{:keys [campaign-id doc dirty sending?]} manager]
    (if sending?
      {:db db}
      {:db (update db :manager assoc :sending? true)
       :fx [[::http/request {:json? true :method "PUT" :path (manager-path campaign-id) :body doc
                             :on-ok [::saved dirty] :on-fail [::save-failed]}]]})))

(defn- ready
  "Everything's in. Work this browser couldn't send takes priority over the server's copy, and goes out now."
  [{:keys [manager] :as db}]
  (let [{{:keys [saved content members unsent]} :pending} manager
        document (doc/fill-in (or unsent saved) content)
        db (update db :manager #(-> % (dissoc :pending) (assoc :status :ready :doc document :content content :members members)))]
    (if unsent (send-now db) {:db db})))

(defn got [{:keys [db]} [_ k value]]
  (let [db (assoc-in db [:manager :pending k] value)
        {:keys [saved content party]} (get-in db [:manager :pending])]
    (cond
      (not (and saved content party)) {:db db}
      (empty? content) {:db (assoc-in db [:manager :status] :no-prep)}
      :else (ready (assoc-in db [:manager :pending :members] (vec (:members party)))))))

(defn load-failed [db [_ {:keys [error]}]]
  (update db :manager assoc :status :failed :error error))

;; ---------- Editing ----------

(defn- edited
  "Stores the new document: in this browser now, on the server after a pause."
  [{:keys [manager] :as db} document]
  (let [n (inc (:dirty manager))]
    {:db (update db :manager assoc :doc document :dirty n)
     :fx [[::keep-copy {:id (:campaign-id manager) :doc document}]
          [:dispatch-later {:ms save-delay-ms :dispatch [::flush n]}]]}))

(defn change
  "One value in the document."
  [{:keys [db]} [_ path value]]
  (edited db (assoc-in (get-in db [:manager :doc]) path value)))

(defn apply-op
  "A change from ttg.manager.doc: [::op doc/add-line topic kind]."
  [{:keys [db]} [_ f & args]]
  (edited db (apply f (get-in db [:manager :doc]) args)))

(defn roll
  "Random, so the d20 comes in as the ::ints coeffect."
  [{:keys [db ints]} [_ pc]]
  (let [{:keys [doc content]} (:manager db)]
    (edited db (doc/roll doc (:questions content) pc ints))))

(defn move-seat [{:keys [db]} [_ pc by]]
  (let [{:keys [doc members content]} (:manager db)]
    (edited db (assoc doc :seats (party/move (party/party members (:corps content) (:seats doc)) pc by)))))

;; ---------- Saving ----------

(defn flush-edits
  "Only the latest pending timer sends."
  [{:keys [db]} [_ n]]
  (if (= n (get-in db [:manager :dirty])) (send-now db) {:db db}))

(defn flush-now
  "Leaving the page: send what's waiting without the pause."
  [{:keys [db]} _]
  (let [{:keys [dirty sent status]} (:manager db)]
    (if (and (= :ready status) (> dirty sent)) (send-now db) {:db db})))

(defn saved [{:keys [db]} [_ n]]
  (let [{:keys [dirty campaign-id]} (:manager db)
        db (update db :manager assoc :sending? false :sent n :problem nil)]
    (if (> dirty n) (send-now db) {:db db :fx [[::sent-all campaign-id]]})))

(defn save-failed [db [_ {:keys [status]}]]
  (update db :manager assoc :sending? false :problem (if (= 401 status) :signin :failed)))

(defn retry [{:keys [db]} _] (send-now db))

(defn set-ui [db [_ k v]] (assoc-in db [:manager :ui k] v))

(defn open-drawer [db [_ drawer]] (assoc-in db [:manager :ui :drawer] drawer))

(rf/reg-cofx ::ints (fn [cofx _] (assoc cofx :ints (take 1 (rng/secure)))))

(rf/reg-event-db ::initialize (fn [db _] (assoc db :manager db/default-manager)))
(rf/reg-event-fx ::open [(rf/inject-cofx ::unsent) validate-spec] open)
(rf/reg-event-fx ::got [validate-spec] got)
(rf/reg-event-db ::load-failed [validate-spec] load-failed)
(rf/reg-event-fx ::change [validate-spec] change)
(rf/reg-event-fx ::op [validate-spec] apply-op)
(rf/reg-event-fx ::roll [(rf/inject-cofx ::ints) validate-spec] roll)
(rf/reg-event-fx ::move-seat [validate-spec] move-seat)
(rf/reg-event-fx ::flush [validate-spec] flush-edits)
(rf/reg-event-fx ::flush-now [validate-spec] flush-now)
(rf/reg-event-fx ::saved [validate-spec] saved)
(rf/reg-event-db ::save-failed [validate-spec] save-failed)
(rf/reg-event-fx ::retry [validate-spec] retry)
(rf/reg-event-db ::set-ui [validate-spec] set-ui)
(rf/reg-event-db ::open-drawer [validate-spec] open-drawer)
