(ns ttg.story.views
  "The story point pool as tokens: light for the players' points, dark for the GM's. The sheet shows it with a spend
   for a story detail (upgrading a check is in the roll drawer); the campaign page adds the GM's controls."
  (:require [re-frame.core :as rf]
            [ttg.story.db :as db]
            [ttg.story.events :as ev]
            [ttg.story.subscriptions :as subs]))

(defn- words [{:keys [player gm]}]
  (str player " player · " gm " GM"))

(defn tokens [{:keys [player gm] :as pool}]
  (into [:span.sp-tokens {:role "img" :aria-label (str "Story points: " (words pool))}]
        (concat (repeat player [:i.tok.player]) (repeat gm [:i.tok.gm]))))

(defn- refresh-button [campaign-id busy?]
  [:button.linkbtn {:type "button" :disabled busy? :on-click #(rf/dispatch [::ev/load campaign-id])} "Refresh"])

(defn- note-line [note]
  (when note [:span.note {:role "status" :style {:margin 0}} note]))

(defn sheet-strip
  "The pool on a character sheet. Nothing for a character in no campaign."
  [read-only?]
  (let [{:keys [campaign-id pool busy? note]} @(rf/subscribe [::subs/story])]
    (when campaign-id
      [:section.card.story-strip {:aria-label "Story points"}
       [:span.label "Story points"]
       (if pool
         [:<> [tokens pool] [:span.meta (words pool)]
          (when-not read-only?
            [:button.btn {:type "button" :disabled (or busy? (zero? (:player pool)))
                          :on-click #(rf/dispatch [::ev/spend {:side "player"
                                                               :note "Spent a story point on a story detail. Tell your GM what it is."}])}
             "Add a story detail"])]
         [:span.meta "Your GM hasn't started a session yet."])
       [refresh-button campaign-id busy?]
       [note-line note]])))

(defn- step [label pool k by busy?]
  (let [n (+ (get pool k) by)]
    [:button.btn.step {:type "button" :aria-label label :disabled (or busy? (neg? n) (> n db/max-points))
                       :on-click #(rf/dispatch [::ev/set-pool (assoc pool k n)])}
     (if (pos? by) "+" "−")]))

(defn- side-steps [pool k title busy?]
  (let [who (if (= :gm k) "GM" "player")]
    [:span.side [:span.meta title]
     [step (str "One fewer " who " point") pool k -1 busy?] [step (str "One more " who " point") pool k 1 busy?]]))

(defn- gm-controls [pool players busy?]
  [:div.story-controls
   [:button.btn.primary {:type "button" :disabled busy? :on-click #(rf/dispatch [::ev/set-pool (db/new-session players)])}
    "New session"]
   (when pool [:<> [side-steps pool :player "Players" busy?] [side-steps pool :gm "GM" busy?]])])

(defn campaign-box
  "The pool on the campaign page. players is how many players have joined, for a new session's pool."
  [gm? players]
  (let [{:keys [campaign-id pool busy? note]} @(rf/subscribe [::subs/story])]
    [:section.box
     [:h2 "Story points"]
     (if pool
       [:p [tokens pool] " " [:span.meta (words pool)]]
       [:p.note (if gm? "Start a session to give the table its story points: one per player, and 1 for you."
                    "Your GM hasn't started a session yet.")])
     (when gm? [gm-controls pool players busy?])
     [:p [refresh-button campaign-id busy?] " " [note-line note]]]))
