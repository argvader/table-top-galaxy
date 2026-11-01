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

(defn set-mode [db [_ mode]]
  (-> db (assoc :mode mode) edited))

(defn- pool-key
  "Which pool a die belongs to."
  [type]
  (if (dice/plain-sides type) :plain-pool :pool))

(defn add-die [db [_ type]]
  (-> db (update-in [(pool-key type) type] #(min db/max-per-type (inc %))) edited))

(defn remove-die [db [_ type]]
  (-> db (update-in [(pool-key type) type] #(max 0 (dec %))) edited))

(defn- apply-ladder [db i]
  (-> db (assoc :ladder i) (update :pool assoc :difficulty (:n (db/ladder i)) :challenge 0)))

(defn set-ladder [db [_ i]]
  (-> db (apply-ladder i) edited))

(defn upgrade [db [_ side]]
  (-> db (update :pool dice/upgrade side 1) edited))

(defn downgrade [db [_ side]]
  (-> db (update :pool dice/downgrade side 1) edited))

(defn clear-pool [db _]
  (edited (if (= :plain (:mode db))
            (assoc db :plain-pool dice/empty-plain-pool)
            (-> db (assoc :pool dice/empty-pool) (apply-ladder db/default-ladder)))))

;; ---------- The roll drawer on a sheet ----------

(defn open-check
  "A check tapped on a sheet: {:label :detail :pool :weapon}. Its pool goes in at Average. Ignored while the last drawer
   is still leaving: that check's close is on its way and would take this one with it."
  [db [_ check]]
  (if (get-in db [:check :closing?])
    db
    (let [check (-> check (update :pool dice/pool) (assoc :before (select-keys db [:mode :pool :ladder])))]
      (-> db (assoc :mode :campaign :check check :pool (:pool check)) (apply-ladder db/default-ladder) edited))))

(defn dismiss-check
  "Slides the drawer away, and closes the check once it's gone so its contents hold still on the way out."
  [{:keys [db]} _]
  (when-let [{:keys [closing?]} (:check db)]
    (when-not closing?
      {:db (assoc-in db [:check :closing?] true)
       :fx [[:dispatch-later {:ms db/drawer-out-ms :dispatch [::close-check]}]]})))

(defn close-check
  "Gives the Dice roller page its pool back. A roll still on the table stays in the log; replaying dice clears any others."
  [db _]
  (if-let [{:keys [before]} (:check db)]
    (-> db (merge before) (assoc :check nil :rolling nil) edited)
    db))

(defn roll-again [db _] (edited db))

;; ---------- Rolling ----------

(defn- pool-size [pool] (reduce + (vals pool)))

(defn- reset-difficulty
  "After a roll the next check starts fresh: Average, with no situational Boost or Setback. A check from a sheet goes back
   to the pool it came with, which keeps any Setback from being encumbered."
  [db]
  (-> (if-let [check (:check db)]
        (assoc db :pool (:pool check))
        (update db :pool assoc :boost 0 :setback 0))
      (apply-ladder db/default-ladder)
      (assoc :reset-note? true)))

(defn- log-roll [db entry]
  (update db :rolls #(vec (take db/log-size (cons entry %)))))

(defn- on-table
  "The dice to act out. A bigger roll sends an even spread of its dice; every die still counts in the result."
  [thrown]
  (let [n (count thrown)]
    (if (<= n db/max-on-table)
      thrown
      (mapv #(thrown (quot (* % n) db/max-on-table)) (range db/max-on-table)))))

(defn- throw-dice
  "Logs the roll and sends its physical dice to the table."
  [db seed entry thrown]
  (let [id (:next-id db)]
    {:db (-> db (log-roll (assoc entry :id id :seed seed)) (assoc :shown nil :rolling id :next-id (inc id)))
     :fx [[:ttg.roller.effects/act-out {:dice (on-table thrown) :on-done [::landed id]}]]}))

(defn- roll-campaign [db seed]
  (let [{:keys [dice] :as rolled} (dice/roll (:pool db) (rng/seeded seed))]
    (-> (throw-dice db seed (merge rolled {:label (get-in db [:check :label] "Custom pool") :spent {}}) (mapv #(select-keys % [:type :face]) dice))
        (update :db reset-difficulty))))

;; Standard dice stay in the pool, so the same roll is one tap away.
(defn- roll-plain [db seed]
  (let [{:keys [plain-pool]} db
        {:keys [rolled] :as r} (dice/roll-plain plain-pool (rng/seeded seed))]
    (throw-dice db seed (assoc r :label (dice/plain-label plain-pool)) (vec (mapcat dice/thrown rolled)))))

(defn roll
  "Decides the roll now, but holds the result back (:rolling) until the 3D dice have acted it out.
   Ignored while the last roll is still on the table."
  [{:keys [db seed]} _]
  (let [plain? (= :plain (:mode db))]
    (if (or (zero? (pool-size (if plain? (:plain-pool db) (:pool db)))) (:rolling db))
      {:db db}
      (if plain? (roll-plain db seed) (roll-campaign db seed)))))

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

;; The roller's keys sit at the top of app-db beside :route and :library, so initializing keeps the rest.
(rf/reg-event-db ::initialize (fn [db _] (merge db db/default-db)))
(rf/reg-event-db ::set-mode [validate-spec] set-mode)
(rf/reg-event-db ::add-die [validate-spec] add-die)
(rf/reg-event-db ::remove-die [validate-spec] remove-die)
(rf/reg-event-db ::set-ladder [validate-spec] set-ladder)
(rf/reg-event-db ::upgrade [validate-spec] upgrade)
(rf/reg-event-db ::downgrade [validate-spec] downgrade)
(rf/reg-event-db ::clear-pool [validate-spec] clear-pool)
(rf/reg-event-db ::open-check [validate-spec] open-check)
(rf/reg-event-fx ::dismiss-check [validate-spec] dismiss-check)
(rf/reg-event-db ::close-check [validate-spec] close-check)
(rf/reg-event-db ::roll-again [validate-spec] roll-again)
(rf/reg-event-fx ::roll [(rf/inject-cofx ::seed) validate-spec] roll)
(rf/reg-event-fx ::landed [validate-spec] landed)
(rf/reg-event-db ::spend [validate-spec] spend-option)
(rf/reg-event-db ::undo-spend [validate-spec] undo-spend)
