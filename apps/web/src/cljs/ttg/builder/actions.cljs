(ns ttg.builder.actions
  "Buy and remove buttons shared by the builder's lists and its info drawer. The rules say why a purchase is blocked
   (ttg.genesys.rules *-block); a player can't click past that, but the campaign's GM can, and the button says so."
  (:require [re-frame.core :as rf]
            [ttg.builder.events :as ev]
            [ttg.genesys.rules :as r]))

(defn change! [& args] (rf/dispatch (into [::ev/change] args)))

(defn gate
  "How a button reacts to a block: disabled for a player, allowed but flagged for the GM. :title is the reason either way."
  [c block]
  (let [gm? (r/gm? c)]
    {:disabled (boolean (and block (not gm?)))
     :override (when (and block gm?) block)
     :title block}))

(defn button-attrs
  "Attributes for a gated button. Extra classes go in cls."
  [c block cls]
  (let [{:keys [disabled override title]} (gate c block)]
    {:type "button" :disabled disabled :title title
     :class (cond-> (vec cls) override (conj "override"))
     :aria-description (when override (str "GM override: " override))}))

(defn override-note
  "The GM's flag under a button they're allowed to click past."
  [c block]
  (when-let [why (:override (gate c block))]
    [:div.override-note [:span.tag.gm "GM override"] " " why]))

;; ---------- Gear ----------

(defn gear-buy
  "Buys one more. A weapon or armor already owned can't be bought twice, even by the GM; anything else they may
   override. cls are extra button classes; aria-label is for icon-only buttons."
  [c ix g {:keys [label cls aria-label]}]
  (let [block (r/gear-buy-block c ix g)]
    (if (= "Bought" block)
      [:button.btn {:type "button" :class cls :disabled true} "Bought"]
      [:button.btn (merge (button-attrs c block cls)
                          {:aria-label aria-label :on-click #(change! r/add-gear (name (:id g)))})
       label])))

(defn gear-remove [g qty]
  [:button.btn {:type "button" :on-click #(change! r/remove-gear (name (:id g)))} (if (> qty 1) "−1" "Remove")])

(defn free-item-over-note
  "The GM's flag on a free item that costs more than the archetype allows."
  [c ix g]
  (let [cap (get-in (r/archetype c ix) [:free-item :max-price])]
    (when (and cap (> (or (:price g) 0) cap))
      (str "Over the " (.toLocaleString cap) "-credit cap for a free " (r/free-item-label (r/archetype c ix))))))

;; ---------- Talents ----------

(defn- talent-buy [c talent owned block]
  (let [tier (r/next-tier talent owned)]
    [:button.btn (merge (button-attrs c block (when-not owned ["primary"]))
                        {:on-click #(change! r/add-talent (name (:id talent)))})
     (if owned (str "+ Rank " (inc (:rank owned)) " · " (* 5 tier) " XP") (str "Buy · " (* 5 tier) " XP"))]))

(defn- talent-remove [c talent owned block]
  [:button.btn (merge (button-attrs c block nil) {:on-click #(change! r/remove-talent (name (:id talent)))})
   (if (and (:ranked talent) (> (:rank owned) 1)) "Remove a rank" "Remove")])

(defn talent-actions [c ix {:keys [ranked] :as talent}]
  (let [id (name (:id talent)) owned (r/owned-talent c id)
        buy (r/talent-buy-block c ix talent) drop-block (when owned (r/talent-drop-block c ix id))
        show-buy? (or (not owned) ranked) shown (if show-buy? (or buy drop-block) drop-block)]
    [:<>
     [:div.acts
      (when owned [talent-remove c talent owned drop-block])
      (when show-buy? [talent-buy c talent owned (when-not (= "Already owned" buy) buy)])]
     (when shown
       (if (r/gm? c) [override-note c shown] [:div.block shown]))]))
