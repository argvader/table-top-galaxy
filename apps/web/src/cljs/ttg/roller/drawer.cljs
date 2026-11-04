(ns ttg.roller.drawer
  "The roll drawer on the character sheet: a side panel on desktop, a bottom sheet on a phone. It uses the Dice roller's pool,
   spending and log, so a check is built, rolled and spent on without leaving the sheet. A player story point can
   upgrade the check once (ttg.story), and a strain recovery check gives its successes back as strain."
  (:require [re-frame.core :as rf]
            [ttg.roller.db :as db]
            [ttg.roller.events :as ev]
            [ttg.roller.subscriptions :as sub]
            [ttg.roller.views :as views]
            [ttg.story.events :as story-ev]
            [ttg.story.subscriptions :as story-sub]
            [ttg.ui.dice :as ui]
            [ttg.ui.drawer :as frame]))

(defn- focus! [el] (when el (.focus el)))

(defn- add-chip [t]
  (let [{:keys [name]} (ui/die-info t)]
    [:button.chip {:type "button" :on-click #(rf/dispatch [::ev/add-die t])} [ui/die t nil name] "+ " name]))

(defn- story-chip
  "Spends a player story point to upgrade the check's ability once (CRB p.27). The upgrade happens when the server has
   moved the point, so two players can't spend the same one."
  [label story-spent?]
  (when-let [pool @(rf/subscribe [::story-sub/pool])]
    (let [left (:player pool)]
      [:div.chips
       [:button.chip.sp {:type "button" :disabled (or story-spent? (not @(rf/subscribe [::story-sub/can-spend?])))
                         :on-click #(rf/dispatch [::story-ev/spend {:side "player" :on-ok [::ev/story-upgrade]
                                                                    :note (str "Spent a story point: upgraded " label ". It goes to the GM.")}])}
        [:span.tok-mini {:aria-hidden true}]
        (if story-spent? "Story point spent on this check" (str "Story point: upgrade (" left " left)"))]])))

(defn- pool-body [{:keys [weapon label story-spent?]}]
  [:<>
   [views/tray]
   [views/ladder-chips (db/ranged? weapon)]
   [:div.chips [add-chip :boost] [add-chip :setback]]
   [story-chip label story-spent?]
   [views/upgrade-chips]
   [views/odds-strip]
   [:button.rollbtn {:type "button" :ref focus!
                     :disabled (or (zero? @(rf/subscribe [::sub/pool-size])) (some? @(rf/subscribe [::sub/rolling])))
                     :on-click #(rf/dispatch [::ev/roll])}
    "Roll" [:kbd "R"]]])

(defn- damage-line
  "A hit's damage, and whether what's left to spend can trigger a critical."
  [{:keys [crit damage]} {:keys [net-success success?]}]
  (when (and damage success?)
    (let [{:keys [small big]} @(rf/subscribe [::sub/remaining :advantage])]
      [:div.dmg [:span.label "Damage"] [:b (+ damage net-success)] [:span damage " base + " net-success " net success"]
       (when (and crit (or (>= small crit) (pos? big)))
         [:span.crit "Critical available (Crit " crit ")"])])))

(defn- recovery-line
  "After a strain recovery check: its successes, back as strain with one tap."
  [{:keys [recover? recovered? result]}]
  (when recover?
    (let [n (max 0 (:net-success result 0))]
      [:div.dmg [:span.label "Strain"]
       (cond recovered? [:span "Recovered " n " strain."]
             (pos? n) [:button.btn.primary {:type "button" :on-click #(rf/dispatch [::ev/recover-strain])} "Recover " n " strain"]
             :else [:span "No successes: no strain back this time."])])))

(defn- result-body [weapon roll]
  [:<>
   [views/narrative-result roll [:<> [damage-line weapon (:result roll)] [recovery-line roll]]]
   [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/roll-again])} "Roll again"]])

(defn drawer []
  (when-let [{:keys [label detail weapon closing?] :as check} @(rf/subscribe [::sub/check])]
    (let [roll @(rf/subscribe [::sub/shown])]
      [:<>
       [frame/panel {:title label :meta detail :closing? closing? :close-label "Close roll"
                     :on-close #(rf/dispatch [::ev/dismiss-check])}
        (if roll [result-body weapon roll] [pool-body check])]
       [views/live-region]])))
