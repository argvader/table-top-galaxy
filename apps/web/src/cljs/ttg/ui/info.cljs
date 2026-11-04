(ns ttg.ui.info
  "The info drawer's contents: what a piece of gear or a talent is for, in plain words, from ttg.genesys.describe.
   The builder and the character sheet each keep their own open/close state and add their own actions (buying in the
   builder, attacking on the sheet) after the body."
  (:require [clojure.string :as str]
            [ttg.genesys.describe :as d]
            [ttg.ui.drawer :as frame]
            [ttg.ui.text :as text]))

(defn name-button
  "An entry's name that opens the drawer on it. on-open gets no arguments."
  [on-open label]
  [:button.namebtn {:type "button" :aria-haspopup "dialog" :aria-label (str label ": what it does") :on-click on-open}
   label])

(defn facts [rows]
  (into [:dl.facts]
        (for [{:keys [label value explain]} rows]
          ^{:key label} [:div [:dt label] [:dd [:b value] (when explain [:span explain])]])))

(defn- body-mod [{:keys [kind effects note strain options]}]
  [:div.infobox
   [:span.label kind]
   (when (seq effects) [:p [:b (str/join ", " effects)]])
   (when note [:p [text/rich note]])
   (when (seq options) [:p "Each one is fitted as one of: " (str/join "; " options) "."])
   (when strain [:p.note {:style {:margin 0}} strain])])

(defn- qualities [item ix]
  (when-let [qs (seq (d/qualities item ix))]
    [:div [:span.label "Qualities"]
     (into [:div.qualities]
           (for [{nm :name :keys [rating activation text]} qs]
             ^{:key nm}
             [:div.quality [:b nm (when rating (str " " rating))] (when activation [:span.tag activation])
              [:p (if text [text/rich text] "Your GM's library doesn't describe this one yet.")]]))]))

(defn gear-body
  "Description, what a body modification does, the item's numbers and its qualities. lead goes straight after the
   description (the sheet's attack pool); extra-facts follow the numbers (the builder's price)."
  [c ix g {:keys [lead extra-facts]}]
  [:<>
   (if (str/blank? (:text g))
     [:p.note {:style {:margin 0}} "No description in your GM's library yet. The numbers below say what it does."]
     [:p.lede (:text g)])
   lead
   (when-let [m (d/body-mod-facts g)] [body-mod m])
   [facts (into (d/gear-facts c ix g) extra-facts)]
   [qualities g ix]])

(defn talent-body [c ix t]
  [:<>
   [:p.lede [text/rich (:text t)]]
   [facts (d/talent-facts c ix t)]])

(defn- gear-meta [g]
  (str/join " · " (remove str/blank? [(some-> (:kind g) name str/capitalize) (:group g)])))

(defn panel
  "The drawer around an entry. kind is :gear or :talent."
  [{:keys [kind entry closing? on-close]} & body]
  (into [frame/panel {:title (:name entry) :closing? closing? :close-label (str "Close " (:name entry)) :on-close on-close
                      :meta [:<> (if (= :talent kind) (str "Tier " (:tier entry) " talent") (gear-meta entry)) " "
                             [text/cite (:source entry)]]}]
        body))
