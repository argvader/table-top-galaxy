(ns ttg.builder.info
  "The builder's info drawer: ttg.ui.info's explanation of a gear item or talent, plus its price and its buy and
   remove buttons, so a player can decide without going back to the list."
  (:require [re-frame.core :as rf]
            [ttg.builder.actions :as act]
            [ttg.builder.events :as ev]
            [ttg.builder.subscriptions :as subs]
            [ttg.content :as content]
            [ttg.genesys.describe :as d]
            [ttg.genesys.rules :as r]
            [ttg.ui.info :as info]))

(defn open!
  "kind is :gear or :talent."
  [kind id]
  (rf/dispatch [::ev/open-info kind id]))

(defn name-button
  "An entry's name that opens the drawer on it."
  [kind entry label]
  [info/name-button #(open! kind (name (:id entry))) label])

(defn- owned-qty [c ix g]
  (some #(when (and (= (:id %) (:id g)) (not (:free %))) (:qty %)) (r/carried c ix)))

(defn- gear-acts [c ix g]
  (let [qty (owned-qty c ix g) block (r/gear-buy-block c ix g)]
    [:div.infoacts
     [:div.acts (when qty [act/gear-remove g qty])
      [act/gear-buy c ix g {:label (if qty "Buy another" (str "Buy · " (.toLocaleString (or (:price g) 0)) " credits"))
                            :cls ["primary"]}]]
     (when (and qty (> qty 1)) [:span.note {:style {:margin 0}} "You have " qty "."])
     (when (not= "Bought" block) (if (r/gm? c) [act/override-note c block] (when block [:div.block block])))]))

(defn info-drawer []
  (when-let [{:keys [kind id closing?]} (:info @(rf/subscribe [::subs/builder]))]
    (let [c (:character @(rf/subscribe [::subs/builder])) ix @(rf/subscribe [::content/ix])
          entry (get-in ix [(if (= :talent kind) :talents :gear) id])]
      (when entry
        [info/panel {:kind kind :entry entry :closing? closing? :on-close #(rf/dispatch [::ev/dismiss-info])}
         (if (= :talent kind)
           [:<> [info/talent-body c ix entry] [:div.infoacts [act/talent-actions c ix entry]]]
           [:<> [info/gear-body c ix entry {:extra-facts (d/buy-facts c ix entry)}] [gear-acts c ix entry]])]))))
