(ns ttg.roller.drawer
  "The roll drawer on the character sheet: a side panel on desktop, a bottom sheet on a phone. It uses the Dice roller's pool,
   spending and log, so a check is built, rolled and spent on without leaving the sheet."
  (:require [re-frame.core :as rf]
            [ttg.roller.db :as db]
            [ttg.roller.events :as ev]
            [ttg.roller.subscriptions :as sub]
            [ttg.roller.views :as views]
            [ttg.ui.dice :as ui]))

(defn- focus! [el] (when el (.focus el)))

(defn- add-chip [t]
  (let [{:keys [name]} (ui/die-info t)]
    [:button.chip {:type "button" :on-click #(rf/dispatch [::ev/add-die t])} [ui/die t nil name] "+ " name]))

(defn- pool-body [weapon]
  [:<>
   [views/tray]
   [views/ladder-chips (db/ranged? weapon)]
   [:div.chips [add-chip :boost] [add-chip :setback]]
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

(defn- result-body [weapon roll]
  [:<>
   [views/narrative-result roll [damage-line weapon (:result roll)]]
   [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/roll-again])} "Roll again"]])

(defn drawer []
  (when-let [{:keys [label detail weapon]} @(rf/subscribe [::sub/check])]
    (let [close #(rf/dispatch [::ev/close-check])
          roll @(rf/subscribe [::sub/shown])]
      [:<>
       [:div.scrim {:on-click close}]
       [:aside.drawer {:aria-modal "true" :role "dialog" :aria-labelledby "d-title"}
        [:div.grab {:aria-hidden true}]
        [:header [:div [:h2#d-title label] [:div.meta detail]]
         [:button.x {:type "button" :aria-label "Close roll" :on-click close} "×"]]
        [:div.body (if roll [result-body weapon roll] [pool-body weapon])]]
       [views/live-region]])))
