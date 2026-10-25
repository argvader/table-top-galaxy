(ns ttg.roller.events
  "Roller events. Handlers are plain named functions (tested directly); randomness arrives as the ::seed coeffect,
   so a roll handler is pure and every logged roll can be replayed from its seed."
  (:require [clojure.spec.alpha :as s]
            [re-frame.core :as rf]
            [ttg.dice :as dice]
            [ttg.dice.rng :as rng]
            [ttg.roller.db :as db]
            [ttg.roller.spend :as spend]))

(defn- check-and-throw [spec db]
  (when-not (s/valid? spec db)
    (throw (ex-info (str "app-db failed spec: " (s/explain-str spec db)) {:db db}))))

(def validate-spec
  (if goog.DEBUG
    (rf/after (partial check-and-throw ::db/app-db))
    []))

(rf/reg-cofx ::seed (fn [cofx _] (assoc cofx :seed (first (rng/secure)))))

;; ---------- Building the pool ----------

(defn- edited
  "Any change to the pool hides the last result and the reset note."
  [db]
  (assoc db :shown nil :reset-note? false))

(defn add-die [db [_ type]]
  (-> db (update-in [:pool type] #(min db/max-per-type (inc %))) edited))

(defn remove-die [db [_ type]]
  (-> db (update-in [:pool type] #(max 0 (dec %))) edited))

(defn- apply-ladder [db i]
  (-> db (assoc :ladder i) (update :pool assoc :difficulty (:n (db/ladder i)) :challenge 0)))

(defn set-ladder [db [_ i]]
  (-> db (apply-ladder i) edited))

(defn upgrade [db [_ side]]
  (-> db (update :pool dice/upgrade side 1) edited))

(defn downgrade [db [_ side]]
  (-> db (update :pool dice/downgrade side 1) edited))

(defn clear-pool [db _]
  (-> db (assoc :pool dice/empty-pool) (apply-ladder db/default-ladder) edited))

;; ---------- Rolling ----------

(defn- pool-size [pool] (reduce + (vals pool)))

(defn- reset-difficulty
  "After a roll the next check starts fresh: Average, with no situational Boost or Setback."
  [db]
  (-> db (update :pool assoc :boost 0 :setback 0) (apply-ladder db/default-ladder) (assoc :reset-note? true)))

(defn- log-roll [db entry]
  (update db :rolls #(vec (take db/log-size (cons entry %)))))

(defn roll
  "Decides the roll now, but holds the result back (:rolling) until the 3D dice have acted it out.
   Ignored while the last roll is still on the table."
  [{:keys [db seed]} _]
  (if (or (zero? (pool-size (:pool db))) (:rolling db))
    {:db db}
    (let [id (:next-id db)
          entry (merge (dice/roll (:pool db) (rng/seeded seed)) {:id id :seed seed :label "Custom pool" :spent {}})]
      {:db (-> db (log-roll entry) (assoc :shown nil :rolling id :next-id (inc id)) reset-difficulty)
       :fx [[:ttg.roller.effects/act-out {:dice (mapv #(select-keys % [:type :face]) (:dice entry)) :on-done [::landed id]}]]})))

;; :ttg.roller.effects/act-out is registered by ttg.roller.effects, which app requires; named by keyword here so
;; these handlers (and their tests) don't pull in the lazily loaded 3D module.
(defn landed
  "The dice have come to rest: show the roll. Repeats, and stale rolls, are ignored."
  [{:keys [db]} [_ id]]
  (if (= id (:rolling db))
    {:db (assoc db :shown id :rolling nil) :fx [[::scroll-into-view "result"]]}
    {:db db}))

;; ---------- Spending ----------

(defn- update-shown [db f & args]
  (update db :rolls (fn [rolls] (mapv #(if (= (:id %) (:shown db)) (apply f % args) %) rolls))))

(defn spend-option [db [_ side i]] (update-shown db spend/spend side i))
(defn undo-spend [db [_ side]] (update-shown db spend/undo side))

(rf/reg-fx ::scroll-into-view
  (fn [id]
    (js/requestAnimationFrame
     #(some-> (js/document.getElementById id) (.scrollIntoView #js {:block "nearest" :behavior "smooth"})))))

(rf/reg-event-db ::initialize (fn [_ _] db/default-db))
(rf/reg-event-db ::add-die [validate-spec] add-die)
(rf/reg-event-db ::remove-die [validate-spec] remove-die)
(rf/reg-event-db ::set-ladder [validate-spec] set-ladder)
(rf/reg-event-db ::upgrade [validate-spec] upgrade)
(rf/reg-event-db ::downgrade [validate-spec] downgrade)
(rf/reg-event-db ::clear-pool [validate-spec] clear-pool)
(rf/reg-event-fx ::roll [(rf/inject-cofx ::seed) validate-spec] roll)
(rf/reg-event-fx ::landed [validate-spec] landed)
(rf/reg-event-db ::spend [validate-spec] spend-option)
(rf/reg-event-db ::undo-spend [validate-spec] undo-spend)
