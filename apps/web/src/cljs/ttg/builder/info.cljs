(ns ttg.builder.info
  "The builder's info drawer: what a piece of gear or a talent is for, in plain words, with its buy and remove buttons
   so a player can decide without going back to the list. The explanations come from ttg.genesys.describe."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [ttg.builder.actions :as act]
            [ttg.builder.events :as ev]
            [ttg.builder.subscriptions :as subs]
            [ttg.content :as content]
            [ttg.genesys.describe :as d]
            [ttg.genesys.rules :as r]
            [ttg.ui.drawer :as frame]
            [ttg.ui.text :as text]))

(defn open!
  "kind is :gear or :talent."
  [kind id]
  (rf/dispatch [::ev/open-info kind id]))

(defn name-button
  "An entry's name that opens the drawer on it."
  [kind entry label]
  [:button.namebtn {:type "button" :aria-haspopup "dialog" :on-click #(open! kind (name (:id entry)))
                    :aria-label (str label ": what it does")}
   label])

(defn- facts [rows]
  (into [:dl.facts]
        (for [{:keys [label value explain]} rows]
          ^{:key label} [:div [:dt label] [:dd [:b value] (when explain [:span explain])]])))

(defn- body-mod [{:keys [kind effects note strain options]}]
  [:div.infobox
   [:span.label kind]
   (when (seq effects) [:p [:b (str/join ", " effects)]])
   (when note [:p [text/rich note]])
   (when (seq options) [:p "You choose one when you install it: " (str/join "; " options) ". Pick it on your character sheet."])
   (when strain [:p.note {:style {:margin 0}} strain])])

(defn- qualities [item ix]
  (when-let [qs (seq (d/qualities item ix))]
    [:div [:span.label "Qualities"]
     (into [:div.qualities]
           (for [{nm :name :keys [rating activation text]} qs]
             ^{:key nm}
             [:div.quality [:b nm (when rating (str " " rating))] (when activation [:span.tag activation])
              [:p (if text [text/rich text] "Your GM's library doesn't describe this one yet.")]]))]))

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

(defn- gear-body [c ix g]
  [:<>
   (if (str/blank? (:text g))
     [:p.note {:style {:margin 0}} "No description in your GM's library yet. The numbers below say what it does."]
     [:p.lede (:text g)])
   (when-let [m (d/body-mod-facts g)] [body-mod m])
   [facts (d/gear-facts c ix g)]
   [qualities g ix]
   [gear-acts c ix g]])

(defn- talent-body [c ix t]
  [:<>
   [:p.lede [text/rich (:text t)]]
   [facts (d/talent-facts c ix t)]
   [:div.infoacts [act/talent-actions c ix t]]])

(defn- gear-meta [g]
  (str/join " · " (remove str/blank? [(some-> (:kind g) name str/capitalize) (:group g)])))

(defn info-drawer []
  (when-let [{:keys [kind id closing?]} (:info @(rf/subscribe [::subs/builder]))]
    (let [c (:character @(rf/subscribe [::subs/builder])) ix @(rf/subscribe [::content/ix])
          entry (get-in ix [(if (= :talent kind) :talents :gear) id])]
      (when entry
        [frame/panel {:title (:name entry) :closing? closing? :close-label (str "Close " (:name entry))
                      :meta [:<> (if (= :talent kind) (str "Tier " (:tier entry) " talent") (gear-meta entry)) " " [text/cite (:source entry)]]
                      :on-close #(rf/dispatch [::ev/dismiss-info])}
         (if (= :talent kind) [talent-body c ix entry] [gear-body c ix entry])]))))
