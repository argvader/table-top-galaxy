(ns ttg.sheet.info
  "The character sheet's info drawer: what a carried item or owned talent does (ttg.ui.info). A weapon also shows how
   this character's attack pool is built, so it can be checked at the table, with an Attack button that hands the check
   to the roll drawer."
  (:require [re-frame.core :as rf]
            [ttg.content :as content]
            [ttg.genesys.describe :as d]
            [ttg.sheet.events :as ev]
            [ttg.sheet.subscriptions :as subs]
            [ttg.ui.dice :as dice]
            [ttg.ui.info :as info]))

(defn name-button
  "An entry's name that opens the drawer on it. kind is :gear or :talent."
  [kind entry label]
  [info/name-button #(rf/dispatch [::ev/open-info kind (name (:id entry))]) label])

(defn- pool-dice
  "Every die in the pool, Setback included, as the roller will start it."
  [pool]
  (into [:span.attack-dice {:aria-label (d/pool-words pool)}]
        (for [k [:proficiency :ability :boost :setback] _ (range (get pool k 0))]
          [dice/die k nil (d/die-names k)])))

(defn- attack-section [{:keys [check facts]}]
  [:div.attack
   [:span.label "Your attack"]
   [:div.attack-pool [pool-dice (:pool check)] [:span.note {:style {:margin 0}} (:detail check)]]
   [info/facts facts]
   [:p.note {:style {:margin 0}} "The roller adds the difficulty on top: two Difficulty dice for Average, until your GM says otherwise."]
   [:div.infoacts
    [:div.acts [:button.btn.primary {:type "button" :on-click #(rf/dispatch [::ev/attack-from-info check])}
                "Attack" [:span.sr (str " with " (:label check))]]]]])

(defn- attack-lead
  "For a weapon, the attack pool comes first: it's what a player opens the drawer for at the table."
  [c ix g]
  (when (= "weapon" (some-> (:kind g) name))
    (if-let [attack (d/attack c ix g)]
      [attack-section attack]
      [:p.note "Your GM's library has no " (:skill g) " skill, so this attack can't be rolled from here."])))

(defn- gear-body [c ix g]
  [info/gear-body c ix g {:lead [attack-lead c ix g]}])

(defn info-drawer []
  (when-let [{:keys [kind id closing?]} (:info @(rf/subscribe [::subs/sheet]))]
    (let [c @(rf/subscribe [::subs/character]) ix @(rf/subscribe [::content/ix])
          entry (get-in ix [(if (= :talent kind) :talents :gear) id])]
      (when entry
        [info/panel {:kind kind :entry entry :closing? closing? :on-close #(rf/dispatch [::ev/dismiss-info])}
         (if (= :talent kind) [info/talent-body c ix entry] [gear-body c ix entry])]))))
