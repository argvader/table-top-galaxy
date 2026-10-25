(ns ttg.roller.views
  "The standalone dice roller (M2): build a pool, see the exact chances, roll, spend the results, keep a log."
  (:require [re-frame.core :as rf]
            [ttg.dice :as dice]
            [ttg.roller.db :as db]
            [ttg.roller.events :as ev]
            [ttg.roller.spend :as spend]
            [ttg.roller.subscriptions :as sub]
            [ttg.ui.dice :as ui]))

(def ^:private positive? #{:proficiency :ability :boost})

(defn verdict
  "\"Success, with Advantage\", \"Failure, but with Advantage\", ..."
  [{:keys [success? net-advantage]}]
  (str (if success? "Success" "Failure")
       (cond (pos? net-advantage) (if success? ", with Advantage" ", but with Advantage")
             (neg? net-advantage) (if success? ", but with Threat" ", with Threat")
             :else "")))

(defn- flavor [{:keys [success? net-advantage]}]
  (cond (and success? (pos? net-advantage)) "You do it, and something extra goes your way."
        (and success? (neg? net-advantage)) "You do it, but it costs you."
        (pos? net-advantage) "It doesn't work, but you get something out of it."
        (neg? net-advantage) "It doesn't work, and things get worse."
        success? "You do it, cleanly."
        :else "It doesn't work."))

(defn- pct [p]
  (cond (zero? p) "0%" (< p 0.005) "<1%" (> p 0.995) ">99%" :else (str (js/Math.round (* 100 p)) "%")))

;; ---------- Pool ----------

(defn- tray []
  (let [pool @(rf/subscribe [::sub/pool])
        side (fn [pred] (filter pred (dice/dice-list pool)))
        btn (fn [i t] ^{:key (str (name t) i)}
              [:button.die {:type "button" :title (str "Remove a " (:name (ui/die-info t)) " die")
                            :on-click #(rf/dispatch [::ev/remove-die t])}
               [ui/die t nil (str "Remove " (:name (ui/die-info t)) " die")]])
        pos (side positive?) neg (side (complement positive?))]
    [:div.tray {:aria-label "Current pool. Tap a die to remove it."}
     (if (empty? (concat pos neg))
       [:span.empty "Your pool is empty. Add dice below."]
       [:<> (map-indexed btn pos) (when (and (seq pos) (seq neg)) [:span.vs "VS"]) (map-indexed btn neg)])]))

(defn- stepper [pool t]
  (let [{:keys [name sides]} (ui/die-info t)]
    [:div.step [ui/die t nil name]
     [:span.nm name [:small sides]]
     [:span.ctl
      [:button {:type "button" :aria-label (str "Remove " name) :on-click #(rf/dispatch [::ev/remove-die t])} "−"]
      [:output.mono {:aria-label (str name " count")} (pool t)]
      [:button {:type "button" :aria-label (str "Add " name) :on-click #(rf/dispatch [::ev/add-die t])} "+"]]]))

(defn- steppers []
  (let [pool @(rf/subscribe [::sub/pool])]
    (into [:div.steps] (for [t dice/die-types] ^{:key t} [stepper pool t]))))

(defn- ladder-chips []
  (let [current @(rf/subscribe [::sub/ladder])
        pool @(rf/subscribe [::sub/pool])]
    [:div.ladder [:span#ladder-label.label "Difficulty"]
     (into [:div.chips {:role "group" :aria-labelledby "ladder-label"}]
           (map-indexed (fn [i {:keys [name n]}]
                          [:button.chip {:type "button" :on-click #(rf/dispatch [::ev/set-ladder i])
                                         :aria-pressed (and (= i current) (= n (+ (:difficulty pool) (:challenge pool))))}
                           name (into [:span.pips] (repeat n [:i]))])
                        db/ladder))]))

(defn- upgrade-chips []
  [:div.upg
   [:button.chip {:type "button" :on-click #(rf/dispatch [::ev/upgrade :ability])} "Upgrade ability"]
   [:button.chip {:type "button" :on-click #(rf/dispatch [::ev/upgrade :difficulty])} "Upgrade difficulty"]
   [:button.chip {:type "button" :on-click #(rf/dispatch [::ev/downgrade :ability])} "Downgrade ability"]
   [:button.chip {:type "button" :on-click #(rf/dispatch [::ev/downgrade :difficulty])} "Downgrade difficulty"]])

(defn- odds-strip []
  (when (pos? @(rf/subscribe [::sub/pool-size]))
    (let [{:keys [success advantage threat triumph despair]} @(rf/subscribe [::sub/odds])]
      [:div.odds {:aria-label "Chances for this pool"}
       [:span [:b (pct success)] " success"]
       [:span [:b (pct advantage)] " net advantage"]
       [:span [:b (pct threat)] " net threat"]
       (when (pos? triumph) [:span [:b (pct triumph)] " triumph"])
       (when (pos? despair) [:span [:b (pct despair)] " despair"])
       [:span.bar [:i {:style {:width (pct success)}}]]])))

;; ---------- Result ----------

(defn- net-chip [s n label]
  (when (pos? n)
    [:span.n [ui/sym s] [:b n] " " label (when (and (> n 1) (not= label "Despair")) (if (= label "Success") "es" "s"))]))

(defn- net-line [{:keys [net-success net-advantage triumph despair]}]
  [:div.net {:aria-label "Net result"}
   (if (pos? net-success) [net-chip :success net-success "Success"] [net-chip :failure (- net-success) "Failure"])
   (if (pos? net-advantage) [net-chip :advantage net-advantage "Advantage"] [net-chip :threat (- net-advantage) "Threat"])
   [net-chip :triumph triumph "Triumph"]
   [net-chip :despair despair "Despair"]
   (when (every? zero? [net-success net-advantage triumph despair]) [:span.n "Everything cancelled"])])

(defn- spend-option [side left i opt]
  (let [{:keys [cost label]} opt]
    [:button.opt {:type "button" :disabled (nil? (spend/payment opt left))
                  :on-click #(rf/dispatch [::ev/spend side i])}
     [:span.cost (if (= cost :big) [ui/sym (spend/big-symbol side)] [ui/sym side cost])]
     [:span label]]))

(defn- spend-block [side spent]
  (let [left @(rf/subscribe [::sub/remaining side])
        title (if (= side :advantage) "Spend Advantage & Triumph" "Threat & Despair")]
    [:div [:h4 title [:span.left (:small left) " " [ui/sym side] " " (:big left) " " [ui/sym (spend/big-symbol side)] " left"]]
     (into [:<>] (map-indexed (fn [i o] ^{:key i} [spend-option side left i o]) (spend/options side)))
     (when (seq spent)
       [:div.spent (for [[i s] (map-indexed vector spent)] ^{:key i} [:span (:label s)])
        [:button.linkbtn {:type "button" :on-click #(rf/dispatch [::ev/undo-spend side])} "Undo last"]])]))

(defn- spends [{:keys [result spent]}]
  (let [{:keys [net-advantage triumph despair]} result
        adv? (or (pos? net-advantage) (pos? triumph))
        thr? (or (neg? net-advantage) (pos? despair))]
    (when (or adv? thr?)
      [:<> [:div.spend {:class (when (and adv? thr?) "two")}
            (when adv? [spend-block :advantage (:advantage spent)])
            (when thr? [spend-block :threat (:threat spent)])]
       [:p.note "The roller spends Advantage; the GM spends Threat. Options are short samples until the content library lands."]])))

(defn- result-card []
  (when-let [{:keys [id seed dice result] :as roll} @(rf/subscribe [::sub/shown])]
    (let [raw (frequencies (mapcat :symbols dice)) c #(get raw % 0)]
      [:div#result.result
       [:div.verdict [:h3 {:class (if (:success? result) "ok" "no")} (verdict result)] [:p (flavor result)]]
       [net-line result]
       ^{:key id} [:div.rolled.tumble {:aria-label "Dice rolled"}
                   (for [[i d] (map-indexed vector dice)] ^{:key i} [ui/die (:type d) (:symbols d)])]
       [:p.note (+ (c :success) (c :triumph)) " success symbols vs " (+ (c :failure) (c :despair)) " failure, "
        (c :advantage) " advantage vs " (c :threat) " threat. Triumph counts as a success; Despair counts as a failure. "
        [:span.seed "Seed " seed]]
       [spends roll]])))

;; ---------- Log ----------

(defn- log-symbols [{:keys [net-success net-advantage triumph despair]}]
  [:<>
   (when-not (zero? net-success) [:span.sym [ui/sym (if (pos? net-success) :success :failure) (min 6 (abs net-success))]])
   (when-not (zero? net-advantage) [:span.sym [ui/sym (if (pos? net-advantage) :advantage :threat) (min 6 (abs net-advantage))]])
   (when (pos? triumph) [:span.sym [ui/sym :triumph triumph]])
   (when (pos? despair) [:span.sym [ui/sym :despair despair]])])

(defn- log-item [{:keys [label seed result spent]}]
  [:div
   [:div.t [:b label] [:span.seed "#" seed]]
   [:div.r [:span {:class (if (:success? result) "ok" "no")} (verdict result)] [log-symbols result]]
   (for [[i s] (map-indexed vector (concat (map #(str "Spent: " (:label %)) (:advantage spent))
                                            (map #(str "GM spent: " (:label %)) (:threat spent))))]
     ^{:key i} [:div.note s])])

(defn- roll-log []
  (let [rolls @(rf/subscribe [::sub/rolls])]
    [:section.panel.log-panel {:aria-labelledby "h-log"}
     [:header [:h2#h-log "Roll log"] [:span.label "This device"]]
     (if (empty? rolls)
       [:ul.feed [:li.empty "Rolls show up here."]]
       [:ul.feed (for [r rolls] ^{:key (:id r)} [:li {:class (when (= r (first rolls)) "new")} [log-item r]])])]))

;; ---------- Page ----------

(defn- go-bar []
  [:div.go
   [:button.rollbtn {:type "button" :disabled (or (zero? @(rf/subscribe [::sub/pool-size])) (some? @(rf/subscribe [::sub/rolling])))
                     :on-click #(rf/dispatch [::ev/roll])}
    "Roll" [:kbd "R"]]])

(defn- live-region []
  (let [roll @(rf/subscribe [::sub/shown])]
    [:div.sr {:aria-live "polite"}
     (when-let [{:keys [triumph despair] :as r} (:result roll)]
       (str (verdict r) "." (when (pos? triumph) (str " " triumph " Triumph.")) (when (pos? despair) (str " " despair " Despair."))))]))

(defn- roll-panel []
  [:section.panel {:aria-labelledby "h-roll"}
   [:header [:h2#h-roll "Dice pool"]
    [:button.linkbtn {:type "button" :on-click #(rf/dispatch [::ev/clear-pool])} "Clear pool"]]
   [tray] [steppers] [ladder-chips] [upgrade-chips] [odds-strip]
   (when @(rf/subscribe [::sub/reset-note?])
     [:div.reset-note "Difficulty reset to Average for your next check."])
   [go-bar] [result-card] [live-region]])

(defn page []
  [:<>
   [ui/symbol-defs]
   [:header.top [:div.brand "Table Top Galaxy" [:small "Dice roller"]] [:div.spacer]]
   [:main.app [roll-panel] [roll-log]]])
