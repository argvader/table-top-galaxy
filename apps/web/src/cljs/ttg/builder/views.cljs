(ns ttg.builder.views
  "The character builder: the seven approved steps, with the prototype's markup and classes (css/game.css)."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [ttg.builder.db :as db]
            [ttg.builder.events :as ev]
            [ttg.builder.subscriptions :as subs]
            [ttg.content :as content]
            [ttg.genesys.rules :as r]
            [ttg.ui.text :as text]))

(defn- change! [& args] (rf/dispatch (into [::ev/change] args)))
(defn- change-lib! [& args] (rf/dispatch (into [::ev/change-with-library] args)))
(defn- ui! [k v] (rf/dispatch [::ev/set-ui k v]))
(defn- go! ([step] (go! step nil)) ([step tab] (rf/dispatch [::ev/go step tab])))
(defn- value [e] (.. e -target -value))
(defn- id-of [entry] (name (:id entry)))

(defn- head [title & body]
  [:div.stephead [:h2 title] (into [:p] body)])

(defn- statline [chars hi-from]
  (into [:div.statline]
        (for [k r/characteristics]
          ^{:key k} [:div {:class (when (> (chars k) hi-from) "hi")} [:b (chars k)] [:span (r/characteristic-short k)]])))

(defn- free-text [{:keys [ranks choose skills non-career] :as free}]
  (let [amount (if (= 2 ranks) "Two ranks" "One rank")]
    (cond (nil? free) "No free skill ranks"
          choose (str amount " in each of " choose " " (when non-career "non-career ") "skills you choose")
          :else (str amount " in " (str/join ", " skills)))))

;; ---------- Archetype ----------

(defn- archetype-card [c {:keys [name source wounds strain xp abilities characteristics free-skills free-item] :as a}]
  [:button.choice {:type "button" :aria-pressed (= (:archetype c) (id-of a))
                   :on-click #(change-lib! r/choose-archetype (id-of a))}
   [:h3 name " " [text/cite source]]
   [statline characteristics 2]
   [:div.kvline [:span "Wounds " [:b wounds] " + Br"] [:span "Strain " [:b strain] " + Will"] [:span "Starting XP " [:b xp]]]
   (for [{ability :name t :text} abilities]
     ^{:key ability} [:div.ability [:b ability "."] " " [text/rich t]])
   [:div.meta (free-text free-skills)
    (when free-item (str " · a free " (r/free-item-label a) " up to " (.toLocaleString (:max-price free-item)) " credits"))]])

(defn- step-archetype [c ix]
  [:<>
   [head "Choose an archetype"
    "Your archetype sets your starting characteristics, thresholds and XP. You can switch later; anything you've bought stays and is repriced."]
   (into [:div.choices] (map (fn [a] ^{:key (:id a)} [archetype-card c a])) (sort-by :name (vals (:archetypes ix))))])

;; ---------- Career ----------

(defn- pick [{:keys [on? block on-click short]} label]
  [:button.pick {:type "button" :aria-pressed on? :disabled (boolean (and block (not on?))) :title block :on-click on-click}
   [:span.box {:aria-hidden true}] [:span.nm label [:i short]]])

(defn- career-card [c {:keys [name source skills useful-talents] :as career}]
  [:button.choice {:type "button" :aria-pressed (= (:career c) (id-of career))
                   :on-click #(change-lib! r/choose-career (id-of career))}
   [:h3 name " " [text/cite source]]
   (into [:div.skillchips] (map (fn [s] ^{:key s} [:span.tag s])) skills)
   (when (seq useful-talents)
     [:div.meta "Suggested talents: " (str/join ", " (map :name useful-talents))])])

(defn- skill-short [skill] (r/characteristic-short (keyword (:characteristic skill))))

(defn- career-picks [c ix]
  (let [n (count (:career-picks c)) by-name (into {} (map (juxt :name identity)) (:skills ix))]
    [:div.sect
     [:h3 "Free career ranks " [:span.count {:class (when (= 4 n) "done")} n " of 4"]]
     (into [:div.picks]
           (for [nm (:skills (r/career c ix)) :let [skill (or (by-name nm) {:name nm})]]
             ^{:key nm}
             [pick {:on? (boolean (some #{nm} (:career-picks c))) :short (when (:characteristic skill) (skill-short skill))
                    :block (if (>= n 4) "Four picked" (r/free-pick-block c ix skill 1))
                    :on-click #(change! update :career-picks r/toggle nm)} nm]))]))

(defn- arch-picks [c ix]
  (let [a (r/archetype c ix) {:keys [choose ranks non-career] :as free} (:free-skills a)
        n (count (:arch-picks c)) pool (remove #(and non-career (r/career-skill? c ix %)) (:skills ix))]
    [:div.sect
     [:h3 (:name a) ": " (str/lower-case (free-text free)) " " [:span.count {:class (when (= n choose) "done")} n " of " choose]]
     (if (or (r/career c ix) (not non-career))
       (into [:div.picks]
             (for [skill pool :let [nm (:name skill)]]
               ^{:key nm}
               [pick {:on? (boolean (some #{nm} (:arch-picks c))) :short (skill-short skill)
                      :block (if (>= n choose) (str choose " picked") (r/free-pick-block c ix skill ranks))
                      :on-click #(change! update :arch-picks r/toggle nm)} nm]))
       [:p.note "Choose a career first. These must be skills outside it."])]))

(defn- step-career [c ix]
  (let [a (r/archetype c ix) free (:free-skills a)]
    [:<>
     [head "Choose a career" "Career skills cost 5 XP less per rank. Pick four of them to start with a free rank (CRB p.40)."]
     (into [:div.choices] (map (fn [x] ^{:key (:id x)} [career-card c x])) (sort-by :name (vals (:careers ix))))
     (when (r/career c ix) [career-picks c ix])
     (cond (:choose free) [arch-picks c ix]
           free [:p.note (:name a) " also gets " (str/lower-case (free-text free)) "."])
     [:p.note "No skill can be above rank " r/max-rank " when creation ends (CRB p.44)."]]))

;; ---------- Obligation ----------

(defn- obligation-card [c {:keys [name kind roll text] :as o}]
  (let [secret? (= "secret" (some-> kind clojure.core/name))]
    [:button.choice {:type "button" :aria-pressed (= (get-in c [:ob :type]) (id-of o))
                     :on-click #(change! update :ob assoc :type (id-of o) :rolled nil)}
     [:h3 name]
     [:div.kvline [:span.tag {:class (when secret? "gm")} (if secret? "Secret" "Obligation")]
      [:span "d100 " [:b (first roll) "–" (if (= 100 (second roll)) "00" (second roll))]]]
     [:p text]]))

(defn- magnitude-picker [c]
  (into [:div.seg {:role "radiogroup" :aria-label "Magnitude"}]
        (for [m r/magnitudes]
          ^{:key m}
          [:button {:type "button" :role "radio" :aria-checked (= m (get-in c [:ob :magnitude]))
                    :aria-selected (= m (get-in c [:ob :magnitude]))
                    :on-click #(change! assoc-in [:ob :magnitude] m)}
           m [:span.c "+" m " XP"]])))

(defn- step-obligation [c ix]
  (let [{:keys [attachment why rolled]} (:ob c)]
    [:<>
     [head "Secret or Obligation"
      "Everyone on the Concord carries something. Pick one or roll for it, choose how heavy it is, then say who or what it ties you to."]
     [:div.toolbar [:button.btn.primary {:type "button" :on-click #(rf/dispatch [::ev/roll-obligation])} "Roll d100"]
      (when rolled [:span.note {:style {:margin 0}} "Rolled " rolled])]
     (into [:div.choices] (map (fn [o] ^{:key (:id o)} [obligation-card c o])) (:obligations ix))
     [:div.card.debt
      [:div [:span.label "Magnitude"] [magnitude-picker c]
       [:p.note "A heavier one gives more starting XP, and comes due more often."]]
      [:div [:label.label {:for "ob-attach"} "Who or what it ties you to"]
       [:input {:type "text" :id "ob-attach" :value attachment :placeholder "A person, a corp, a thing" :auto-complete "off"
                :on-change #(change! assoc-in [:ob :attachment] (value %))}]
       (into [:div.sugg {:style {:margin-top 6}}]
             (for [corp db/corps]
               ^{:key corp} [:button.chip {:type "button" :aria-pressed (= attachment corp)
                                           :on-click #(change! assoc-in [:ob :attachment] corp)} corp]))]
      [:div [:label.label {:for "ob-why"} "Why it matters to them"]
       [:textarea {:id "ob-why" :value why :placeholder "One sentence the GM can build a scene on"
                   :on-change #(change! assoc-in [:ob :why] (value %))}]]]]))

;; ---------- XP ----------

(defn- char-card [c ix k]
  (let [v (r/characteristic c ix k) up (r/characteristic-up-block c ix k) bought (get-in c [:char-buy k] 0)
        line (or up (str "Next: " (* 10 (inc v)) " XP")) nm (r/characteristic-names k)]
    [:div.char {:class (when (pos? bought) "raised")}
     [:span.label nm] [:b v]
     [:span.base "Base " (r/base-characteristic c ix k) (when (pos? bought) (str " · +" bought " bought"))]
     [:div.ctl
      [:button.btn.step {:type "button" :disabled (zero? bought) :aria-label (str "Lower " nm)
                         :on-click #(change! update-in [:char-buy k] dec)} "−"]
      [:button.btn.step {:type "button" :disabled (boolean up) :aria-label (str "Raise " nm ". " line)
                         :on-click #(change! update-in [:char-buy k] (fnil inc 0))} "+"]]
     [:span.cost line]]))

(defn- chars-tab [c ix]
  [:<>
   (into [:div.chars] (map (fn [k] ^{:key k} [char-card c ix k])) r/characteristics)
   [:p.note "A raise costs ten times the new rating, and this is the only time characteristics can be bought with XP (CRB p.44)."]])

(defn- skill-row [c ix skill]
  (let [nm (:name skill) k (keyword (id-of skill)) free (r/free-ranks c ix skill) rk (r/rank-of c ix skill)
        bought (r/bought-ranks c skill) career? (r/career-skill? c ix skill) up (r/skill-up-block c ix skill)]
    [:div.srow
     [:span.nm [:span.dot {:class (when-not career? "off") :aria-hidden true}] nm [:i (skill-short skill)]
      [:span.why (str/join " · " (remove nil? [(if career? "Career" "Non-career") (when (pos? free) (str free " free"))
                                               (when (< rk r/max-rank) (or up (str "next rank " (r/rank-price c ix skill (inc rk)) " XP")))]))]]
     (into [:span.ranks {:aria-label (str "Rank " rk)}]
           (for [i (range 5)]
             [:i {:class (cond (< i free) "free" (< i rk) "on" (>= i r/max-rank) "cap")}]))
     [:span.mini [text/pool-dice (r/skill-pool c ix skill)]]
     [:span.sctl
      [:button.btn.step {:type "button" :disabled (zero? bought) :aria-label (str "Remove a rank of " nm)
                         :on-click #(change! update-in [:skill-buy k] dec)} "−"]
      [:button.btn.step {:type "button" :disabled (boolean up) :aria-label (str "Buy a rank of " nm)
                         :on-click #(change! update-in [:skill-buy k] (fnil inc 0))} "+"]]]))

(defn- skills-tab [c ix {:keys [q career-only?]}]
  (let [q (str/lower-case (str/trim q))
        hit? #(and (str/includes? (str/lower-case (:name %)) q) (or (not career-only?) (r/career-skill? c ix %)))]
    [:<>
     [:div.toolbar
      [:label.sr {:for "q"} "Filter skills"]
      [:input {:id "q" :type "search" :placeholder "Filter skills…" :auto-complete "off" :value (:q @(rf/subscribe [::subs/builder]))
               :on-change #(ui! :q (value %))}]
      [:label.toggle [:input {:type "checkbox" :checked career-only? :on-change #(ui! :career-only? (.. % -target -checked))}] " Career skills only"]]
     (into [:div]
           (for [group r/skill-groups :let [hits (filter #(and (= group (:category %)) (hit? %)) (:skills ix))] :when (seq hits)]
             ^{:key group}
             [:div.sgroup [:span.label group]
              (for [skill hits] ^{:key (:id skill)} [skill-row c ix skill])]))]))

(defn- pyramid [c ix]
  (let [counts (r/tier-counts ix (:talents c)) ok? (r/pyramid-ok? counts)]
    [:div.pyramid
     (for [t [5 4 3 2 1]]
       (with-meta (into [:div.tier [:span "Tier " t]]
                        (for [i (range (max (counts t) (- 6 t)))] [:i.slot {:class (when (< i (counts t)) "on")}]))
         {:key t}))
     [:p.pyr-msg {:class (if ok? "ok" "no")}
      (if ok? "Valid. " "Broken. ") "Each tier needs more talents than the tier above it."]]))

(defn- talent-actions [c ix {:keys [ranked] :as talent}]
  (let [id (id-of talent) owned (r/owned-talent c id) buy (r/talent-buy-block c ix talent)
        tier (r/next-tier talent owned) drop-block (when owned (r/talent-drop-block c ix id)) show-buy? (or (not owned) ranked)]
    [:<>
     [:div.acts
      (when owned
        [:button.btn {:type "button" :disabled (boolean drop-block) :title drop-block :on-click #(change! r/remove-talent id)}
         (if (and ranked (> (:rank owned) 1)) "Remove a rank" "Remove")])
      (when show-buy?
        [:button.btn {:type "button" :class (when-not owned "primary") :disabled (boolean buy) :on-click #(change! r/add-talent id)}
         (if owned (str "+ Rank " (inc (:rank owned)) " · " (* 5 tier) " XP") (str "Buy · " (* 5 tier) " XP"))])]
     (when-let [why (if show-buy? (or buy drop-block) drop-block)] [:div.block why])]))

(defn- talent-card [c ix useful {:keys [name tier activation ranked source] :as talent}]
  (let [owned (r/owned-talent c (id-of talent))]
    [:div.talent {:class (when owned "owned")}
     [:div
      [:h4 name (when (and owned ranked) [:span.rk " rank " (:rank owned)])
       " " [:span.tag "Tier " tier] [:span.tag activation] (when ranked [:span.tag "Ranked"])
       (when (useful (id-of talent)) [:span.tag.sugg-tag "Suggested for your career"]) [text/cite source]]
      [:p [text/rich (:text talent)]]]
     [talent-actions c ix talent]]))

(defn- talents-tab [c ix {:keys [tq tier]}]
  (let [q (str/lower-case (str/trim tq))
        useful (set (map id-of (:useful-talents (r/career c ix))))
        match? #(and (or (zero? tier) (= tier (:tier %)))
                     (or (str/includes? (str/lower-case (:name %)) q) (str/includes? (str/lower-case (or (:text %) "")) q)))
        rank-key (fn [t] [(if (r/owned-talent c (id-of t)) 0 1) (if (useful (id-of t)) 0 1) (:tier t) (:name t)])
        hits (sort-by rank-key (filter match? (vals (:talents ix))))]
    [:<>
     [pyramid c ix]
     [:div.toolbar [:label.sr {:for "tq"} "Search talents"]
      [:input {:id "tq" :type "search" :placeholder (str "Search " (count (:talents ix)) " talents…") :value tq
               :auto-complete "off" :on-change #(ui! :tq (value %))}]]
     (into [:div.filters {:role "group" :aria-label "Filter by tier"}]
           (for [t (range 6)]
             ^{:key t} [:button.chip {:type "button" :aria-pressed (= tier t) :on-click #(ui! :tier t)} (if (pos? t) (str "Tier " t) "All tiers")]))
     (into [:div.tlist] (map (fn [t] ^{:key (:id t)} [talent-card c ix useful t])) (take 40 hits))
     (when (> (count hits) 40)
       [:p.note "Showing 40 of " (count hits) ". Search or pick a tier to narrow it down."])]))

(defn- step-xp [c ix {:keys [xp-tab] :as b}]
  (let [a (r/archetype c ix)]
    (if-not a
      [:<> [head "Spend XP" "Choose an archetype first. It sets your XP budget."]
       [:button.btn.primary {:type "button" :on-click #(go! 0)} "Go to Archetype"]]
      [:<>
       [head "Spend XP" "You have " [:b.mono (r/xp-left c ix)] " of " (r/budget c ix) " XP left. Prices update as you go, and you can undo any purchase."]
       (into [:div.seg {:role "tablist" :aria-label "What to spend XP on"}]
             (for [[id label n] [[:chars "Characteristics" (r/characteristics-xp c ix)] [:skills "Skills" (r/skills-xp c ix)] [:talents "Talents" (r/talents-xp c ix)]]]
               ^{:key id} [:button {:type "button" :role "tab" :aria-selected (= xp-tab id) :on-click #(ui! :xp-tab id)} label [:span.c n]]))
       [:div {:role "tabpanel"}
        (case xp-tab :chars [chars-tab c ix] :skills [skills-tab c ix b] [talents-tab c ix b])]])))

;; ---------- Gear ----------

(defn- kind-of [item] (some-> (:kind item) name))

(defn- gear-detail [{:keys [skill damage damage-bonus adds-brawn crit range qualities soak defense encumbrance-bonus] :as g}]
  (case (kind-of g)
    "weapon" (str skill " · Dmg " (if adds-brawn (str "+" damage-bonus) damage) " · Crit " crit " · " range
                  (when (seq qualities) (str " · " (str/join ", " (map #(str (:name %) (when (:rating %) (str " " (:rating %)))) qualities)))))
    "armor" (str "Soak +" soak " · Defense " defense)
    (when encumbrance-bonus (str "Carry +" encumbrance-bonus))))

(defn- catalog-item [c ix g]
  (let [block (r/gear-buy-block c ix g) bought? (= "Bought" block) detail (gear-detail g)]
    [:div.gitem
     [:div [:h4 (:name g) (when (:restricted g) [:span.tag " Restricted"])]
      [:div.meta detail (when detail " · ") "Enc " (or (:encumbrance g) 0) " · Rarity " (or (:rarity g) "–") " " [text/cite (:source g)]]]
     [:div.acts [:span.price (.toLocaleString (or (:price g) 0))]
      [:button.btn {:type "button" :disabled (boolean block) :on-click #(change! r/add-gear (id-of g))} (if bought? "Bought" "Buy")]]]))

(defn- catalog [c ix {:keys [gear-tab gq]}]
  (let [q (str/lower-case (str/trim gq))
        items (filter #(and (= (name gear-tab) (kind-of %))
                            (or (str/includes? (str/lower-case (:name %)) q) (str/includes? (str/lower-case (str (:group %))) q)))
                      (sort-by :name (vals (:gear ix))))]
    (if (seq items)
      (into [:<>]
            (for [[group entries] (sort-by (comp str key) (group-by :group items))]
              ^{:key (str group)}
              [:div.sect [:h3 group]
               (into [:div.glist] (map (fn [g] ^{:key (:id g)} [catalog-item c ix g])) entries)]))
      [:p.note "Nothing matches."])))

(defn- free-item-card [c ix]
  (let [a (r/archetype c ix) {:keys [group max-price]} (:free-item a)]
    (when group
      [:div.card.s0
       [:div [:span.tag (:name a)]]
       [:div.q "Your free " (r/free-item-label a) ", worth up to " (.toLocaleString max-price) " credits"]
       (into [:div.sugg]
             (for [g (sort-by :name (vals (:gear ix))) :when (and (= group (:group g)) (<= (or (:price g) 0) max-price))]
               ^{:key (:id g)}
               [:button.chip {:type "button" :aria-pressed (= (:free-item c) (id-of g))
                              :on-click #(change! update :free-item (fn [cur] (when (not= cur (id-of g)) (id-of g))))}
                (:name g) " · " (.toLocaleString (:price g))]))
       [:p.note {:style {:margin 0}} "It doesn't come out of your 500 credits."]])))

(defn- owned-item [c ix g]
  (let [id (id-of g) worn? (= (:id g) (:id (r/best-armor c ix)))]
    [:div.gitem
     [:div [:h4 (:name g) (when (> (:qty g) 1) [:span.mono {:style {:font-weight 400}} " ×" (:qty g)])]
      [:div.meta (when (:free g) (str "Free (" (:name (r/archetype c ix)) ") · "))
       (when (= "armor" (kind-of g)) (if worn? "Worn · " "Carried, not worn · "))
       "Enc " (* (or (:encumbrance g) 0) (:qty g))]]
     (when-not (:free g)
       [:div.acts
        (when-not (r/single-item? g)
          [:button.btn.step {:type "button" :disabled (boolean (r/gear-buy-block c ix g)) :aria-label (str "Add one more " (:name g))
                             :on-click #(change! r/add-gear id)} "+"])
        [:button.btn {:type "button" :on-click #(change! r/remove-gear id)} (if (> (:qty g) 1) "−1" "Remove")]])]))

(defn- money [c ix]
  (let [left (r/credits-left c ix) {:keys [encumbrance encumbrance-max encumbrance-bonus]} (r/derived c ix)]
    [:div.money
     [:div [:span.label "Credits left"] [:br] [:b {:class (when (neg? left) "neg")} left] " " [:span.note "of " r/start-credits]]
     [:div.enc [:span.label "Encumbrance " encumbrance " / " encumbrance-max]
      [:div.enc-bar {:class (when (> encumbrance encumbrance-max) "over")}
       [:i {:style {:width (str (min 100 (* 100 (/ encumbrance encumbrance-max))) "%")}}]]
      [:span.note {:style {:margin 0}} "Threshold 5 + Brawn " (r/characteristic c ix :brawn)
       (when (pos? encumbrance-bonus) (str " + " encumbrance-bonus " carried")) " · worn armor counts 3 less"]]
     [:div [:span.label "Pocket money"] [:br]
      (if (:pocket c)
        [:<> [:b "+" (:pocket c)] " " [:span.note "can't buy starting gear"]]
        [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/roll-pocket])} "Roll d100"])]]))

(defn- gear-tabs [ix gear-tab]
  (into [:div.seg {:role "tablist" :aria-label "Catalog"}]
        (for [[id label] [[:weapon "Weapons"] [:armor "Armor"] [:gear "Gear"]]]
          ^{:key id}
          [:button {:type "button" :role "tab" :aria-selected (= gear-tab id) :on-click #(ui! :gear-tab id)}
           label [:span.c (count (filter #(= (name id) (kind-of %)) (vals (:gear ix))))]])))

(defn- step-gear [c ix {:keys [gear-tab gq] :as b}]
  (let [mine (r/carried c ix)]
    [:<>
     [head "Buy starting gear" "You start with " r/start-credits " credits (CRB p.51). Leftover credits stay on your sheet. After shopping, roll d100 for pocket money."]
     [money c ix]
     [free-item-card c ix]
     [:div.gearcols
      [:div
       [:div.toolbar [gear-tabs ix gear-tab]
        [:label.sr {:for "gq"} "Search the catalog"]
        [:input {:id "gq" :type "search" :placeholder "Search…" :value gq :auto-complete "off" :on-change #(ui! :gq (value %))}]]
       [catalog c ix b]]
      [:div.sect
       [:h3 "Your gear " [:span.count (reduce + (map :qty mine)) " items"]]
       (if (seq mine)
         (into [:div.glist] (map (fn [g] ^{:key (:id g)} [owned-item c ix g])) mine)
         [:div.empty "Nothing yet. Buy from the catalog. Credits you don't spend stay on your sheet."])
       [:p.note "You wear one armor at a time. The best one counts for soak and defense."]]]]))

;; ---------- Story ----------

(defn- motive-card [c ix type]
  (let [k (r/motive-key type) m (get-in c [:motives k]) table (filter #(= type (:type %)) (:motivations ix))]
    [:div.card.motive
     [:div.hd [:span.label type] [:span.q (db/motive-hints type)]
      [:button.linkbtn {:type "button" :on-click #(rf/dispatch [::ev/roll-motive type])} "Roll d100"]]
     [:label.sr {:for (str "m-" (name k))} type " in a word or two"]
     [:input {:type "text" :id (str "m-" (name k)) :value (:name m) :placeholder (str type " in a word or two") :auto-complete "off"
              :on-change #(change! assoc-in [:motives k :name] (value %))}]
     (into [:div.sugg {:aria-label "From the Core Rulebook"}]
           (for [x table]
             ^{:key (:id x)}
             [:button.chip {:type "button" :aria-pressed (= (:name m) (:name x)) :title (:text x)
                            :on-click #(change! update-in [:motives k] (fn [old] {:name (:name x) :text (if (str/blank? (:text old)) (:text x) (:text old))}))}
              (:name x)]))
     [:label.sr {:for (str "mt-" (name k))} type ": what it means for them"]
     [:textarea {:id (str "mt-" (name k)) :value (:text m) :placeholder "What it means for them, in a sentence"
                 :on-change #(change! assoc-in [:motives k :text] (value %))}]]))

(defn- step-story [c ix]
  [:<>
   [head "Motivations and Session 0" "Motivations tell the GM what drives your character. Pick from the book's tables, roll, or write your own (CRB p.46–50)."]
   [:div [:label.label {:for "tagline"} "One-line description"]
    [:input {:type "text" :id "tagline" :value (:tagline c) :placeholder "e.g. Dock mechanic, Concord berth 9" :auto-complete "off"
             :on-change #(change! assoc :tagline (value %))}]]
   (into [:div.motives] (map (fn [t] ^{:key t} [motive-card c ix t])) r/motive-types)
   [:div.card.s0
    [:div [:span.tag.gm "Session 0 · from your GM"]]
    [:div.q db/session-0-question]
    [:label.sr {:for "s0"} "Your answer"]
    [:textarea {:id "s0" :value (:s0 c) :placeholder "Your answer" :on-change #(change! assoc :s0 (value %))}]
    [:p.note {:style {:margin 0}} "Only you and the GM see this answer."]]])

;; ---------- Review ----------

(defn- review-card [title step & body]
  (into [:div.card.rev [:h3 title [:button.linkbtn {:type "button" :on-click #(go! step)} "Edit"]]] body))

(defn- review-cards [c ix]
  (let [a (r/archetype c ix) ob (r/obligation c ix) d (r/derived c ix)
        ranked (for [s (:skills ix) :let [rk (r/rank-of c ix s)] :when (pos? rk)] (str (:name s) " " rk))]
    [:div.revgrid
     [review-card "Identity" 0 [:p [:b (if (str/blank? (:name c)) "Unnamed" (:name c))] (when (seq (:tagline c)) (str ", " (:tagline c)))]
      [:p (or (:name a) "No archetype") " · " (or (:name (r/career c ix)) "No career")]]
     [review-card "Secret or Obligation" 2
      (if ob
        [:p [:b (:name ob)] ", magnitude " [:b.mono (get-in c [:ob :magnitude])] ". Tied to " (let [x (get-in c [:ob :attachment])] (if (str/blank? x) "nobody yet" x))]
        [:p "Not chosen."])]
     [review-card "Characteristics" 3 [statline (r/characteristics-of c ix) 99]
      [:p.note {:style {:margin 0}} "Wounds " (:wounds d) " · Strain " (:strain d) " · Soak " (:soak d) " · Defense " (:defense d)]]
     [review-card "Skills" 3 [:p (if (seq ranked) (str/join ", " ranked) "No ranks yet.")]]
     [review-card "Talents" 3
      (if (seq (:talents c))
        (into [:ul] (for [t (:talents c)] ^{:key (:id t)} [:li (get-in ix [:talents (:id t) :name]) " " (:rank t)]))
        [:p "None yet."])]
     [review-card "Gear" 4
      (if-let [items (seq (r/carried c ix))]
        (into [:ul] (for [g items] ^{:key (:id g)} [:li (:name g) (when (> (:qty g) 1) (str " ×" (:qty g))) (when (:free g) " (free)")]))
        [:p "Nothing bought."])
      [:p.note {:style {:margin 0}} (+ (r/credits-left c ix) (or (:pocket c) 0)) " credits"]]
     [review-card "Motivations" 5
      (into [:ul] (for [t r/motive-types]
                    ^{:key t} [:li [:b t ":"] " " (let [x (get-in c [:motives (r/motive-key t) :name])] (if (str/blank? x) "blank" x))]))]]))

(defn- check-row [{:keys [step level text tab]}]
  ^{:key text}
  [:div.check {:class (name level)}
   [:span.i {:aria-hidden true} (if (= :block level) "!" "i")]
   [:span text [:span.sr (if (= :block level) " (must fix)" " (optional)")]]
   [:button.linkbtn {:type "button" :on-click #(go! step tab)} (:label (r/steps step))]])

(defn- step-review [c ix issues {:keys [editing? saving? error]}]
  (let [blocked (count (r/blocks issues))]
    [:<>
     [head "Review" "Fix anything marked red, then " (if editing? "save your changes" "create your character") ". Yellow items are fine to leave for now."]
     (if (seq issues)
       (into [:div.checks] (map check-row) issues)
       [:div.checks [:div.check.ok [:span.i {:aria-hidden true} "✓"] [:span "Everything checks out."] [:span]]])
     [review-cards c ix]
     (when error [:p.banner {:role "alert"} error])
     [:div.stepnav {:style {:display "flex"}}
      [:button.btn.big {:type "button" :on-click #(go! 5)} "Back"]
      [:span {:style {:display "flex" :gap 10 :align-items "center" :flex-wrap "wrap" :justify-content "flex-end"}}
       (when (pos? blocked) [:span.note {:style {:margin 0}} blocked " to fix first"])
       [:button.btn.primary.big {:type "button" :disabled (or saving? (pos? blocked)) :on-click #(rf/dispatch [::ev/save])}
        (cond saving? "Saving…" editing? "Save changes" :else "Create character")]]]]))

;; ---------- Frame ----------

(defn- step-summary [c ix issues i]
  (let [a (r/archetype c ix) career (r/career c ix) ob (r/obligation c ix)
        motives (count (remove #(str/blank? (:name %)) (vals (:motives c))))]
    (case i
      0 (or (:name a) "Not chosen")
      1 (if career (str (:name career) " · " (count (:career-picks c)) "/4 picks") "Not chosen")
      2 (if ob (str (:name ob) " · magnitude " (get-in c [:ob :magnitude])) "Not chosen")
      3 (if a (str (r/spent-xp c ix) " of " (r/budget c ix) " XP") "Needs an archetype")
      4 (str (r/credits-left c ix) " credits left")
      5 (str motives " of 4 motivations")
      (str (count (r/blocks issues)) " to fix"))))

(defn- step-rail [c ix issues {:keys [step visited]}]
  (into [:ol.steps]
        (map-indexed
         (fn [i {:keys [label]}]
           (let [blocked? (some #(and (= i (:step %)) (= :block (:level %))) issues)
                 seen? (visited i)
                 state (cond (= 6 i) nil (and blocked? seen?) "issue" (and seen? (not blocked?)) "ok")]
             ^{:key i}
             [:li [:button {:type "button" :class state :aria-current (when (= step i) "step") :on-click #(go! i)}
                   [:span.n {:aria-hidden true} (case state "ok" "✓" "issue" "!" (inc i))]
                   [:span [:span.t label] [:span.s (step-summary c ix issues i)]]]]))
         r/steps)))

(defn- budget-panel [c ix]
  (let [b (r/budget c ix) s (r/spent-xp c ix) left (- b s) most (max b s 1)
        seg (fn [cls n] [:i {:class cls :style {:width (str (* 100 (/ n most)) "%")}}])]
    [:div.budget
     [:div.row [:span.label "XP left"] [:span.of s " of " b " spent"]]
     [:b {:class (when (neg? left) "neg")} left]
     [:div.bar {:class (when (neg? left) "over") :aria-hidden true}
      [seg "c" (r/characteristics-xp c ix)] [seg "s" (r/skills-xp c ix)] [seg "t" (r/talents-xp c ix)]]]))

(defn- preview-body [c ix issues]
  (let [d (r/derived c ix) blocked (count (r/blocks issues)) credits (r/credits-left c ix)]
    [:<>
     [budget-panel c ix]
     (if (r/archetype c ix)
       [statline (r/characteristics-of c ix) 99]
       [:p.note {:style {:margin 0}} "Choose an archetype to see characteristics."])
     [:div.derived
      [:div [:b (:wounds d)] [:small "Wound threshold"]] [:div [:b (:strain d)] [:small "Strain threshold"]]
      [:div [:b (:soak d)] [:small "Soak"]] [:div [:b (:defense d) " / " (:defense d)] [:small "Defense melee / ranged"]]
      [:div [:b (:encumbrance d) [:span {:style {:font-size 15 :color "var(--muted)"}} " / " (:encumbrance-max d)]] [:small "Encumbrance"]]
      [:div [:b {:class (when (neg? credits) "neg")} (+ credits (or (:pocket c) 0))] [:small "Credits"]]]
     [:div [:span.label (if (pos? blocked) (str blocked " to fix") "Checks")]
      (if (seq issues)
        (into [:div.issues]
              (for [{:keys [text level step tab]} issues]
                ^{:key text} [:button.issue-row {:type "button" :class (name level) :on-click #(go! step tab)} [:span text]]))
        [:p.allgood "Everything checks out."])]]))

(defn- dock [c ix issues {:keys [step]}]
  (let [left (r/xp-left c ix) last? (= step (dec (count r/steps)))]
    [:div.dock
     [:button.xpchip {:type "button" :on-click #(ui! :preview? true) :aria-label (str "Open preview. " left " XP left.")}
      [:b {:class (when (neg? left) "neg")} (if (r/archetype c ix) left "–") " XP"]
      [:small "Preview · " (count (r/blocks issues)) " to fix"]]
     [:div.spacer]
     [:button.btn {:type "button" :disabled (zero? step) :on-click #(go! (dec step))} "Back"]
     (when-not last?
       [:button.btn.primary {:type "button" :on-click #(go! (inc step))} "Next: " (:label (r/steps (inc step)))])]))

(defn- step-body [c ix issues {:keys [step] :as b}]
  (case step
    0 [step-archetype c ix]
    1 [step-career c ix]
    2 [step-obligation c ix]
    3 [step-xp c ix b]
    4 [step-gear c ix b]
    5 [step-story c ix]
    [step-review c ix issues b]))

(defn- step-nav [{:keys [step]}]
  (when (< step (dec (count r/steps)))
    [:div.stepnav
     [:button.btn.big {:type "button" :disabled (zero? step) :on-click #(go! (dec step))} "Back"]
     [:button.btn.primary.big {:type "button" :on-click #(go! (inc step))} "Next: " (:label (r/steps (inc step)))]]))

(defn- ready [c ix issues {:keys [preview? editing?] :as b}]
  [:div.wrap
   [:section.namebar {:aria-label "Character name"}
    [:div.portrait {:aria-hidden true} (r/initials (:name c))]
    [:div
     [:label.sr {:for "cname"} "Character name"]
     [:input {:type "text" :id "cname" :auto-complete "off" :placeholder "Name your character" :value (:name c)
              :on-change #(change! assoc :name (value %))}]
     [:div.sub (or (seq (str/join " · " (remove str/blank? [(:name (r/archetype c ix)) (:name (r/career c ix)) (:tagline c)]))) "New character")]]
    (when-not editing?
      [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/restart])} "Start over"])]
   [:div.shell
    [:nav.rail {:aria-label "Builder steps"} [step-rail c ix issues b]]
    [:main.card.stepcard [step-body c ix issues b] [step-nav b]]
    [:aside.side.card {:data-desk-only true :aria-label "Character preview"} [:div.preview [:h2 "Preview"] [preview-body c ix issues]]]]
   [dock c ix issues b]
   (when preview?
     [:<>
      [:div.scrim {:on-click #(ui! :preview? false)}]
      [:div.sheet {:role "dialog" :aria-modal true :aria-label "Character preview"}
       [:div.grab {:aria-hidden true}]
       [:div.preview
        [:div {:style {:display "flex" :justify-content "space-between" :align-items "center"}}
         [:h2 "Preview"] [:button.x {:type "button" :aria-label "Close preview" :on-click #(ui! :preview? false)} "×"]]
        [preview-body c ix issues]]]])])

(defn- no-content [campaign-id]
  [:div.wrap
   [:p.banner
    (if campaign-id
      "This campaign's library is empty. Ask your GM to load it, then come back."
      "Build a character from a campaign, so it can use your GM's library. Open the invite link, or start from a campaign you run.")]])

(defn page []
  (let [{:keys [character load-error editing? campaign-id] :as b} @(rf/subscribe [::subs/builder])
        ix @(rf/subscribe [::content/ix])
        status @(rf/subscribe [::content/status])
        issues @(rf/subscribe [::subs/issues])]
    [:div.page-builder
     (cond (= :error status) [:div.wrap [:p.banner {:role "alert"} @(rf/subscribe [::content/error])]]
           load-error [:div.wrap [:p.banner {:role "alert"} load-error]]
           (or (nil? ix) (and editing? (nil? (:id character)))) [:div.wrap [:p.note "Loading…"]]
           (empty? (:archetypes ix)) [no-content campaign-id]
           :else [ready character ix issues b])]))
