(ns ttg.sheet.views
  "The character sheet, with the prototype's markup and classes (css/game.css). Vitals, notes and portrait are
   edited here and save themselves; everything bought goes through the builder, but the choices a purchase needs
   (which limb, which characteristic) are made here. The campaign's GM can edit too, and adds adjustments. On a desktop Skills is its own panel
   beside the tabs; on a tablet or phone it's the first tab."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as reagent]
            [ttg.content :as content]
            [ttg.genesys.mods :as mods]
            [ttg.genesys.rules :as r]
            [ttg.roller.drawer :as roller]
            [ttg.roller.events :as roller-ev]
            [ttg.routes :as routes]
            [ttg.sheet.events :as ev]
            [ttg.sheet.subscriptions :as subs]
            [ttg.theme :as theme]
            [ttg.ui.portrait :as portrait]
            [ttg.ui.text :as text]))

(defn- change! [& args] (rf/dispatch (into [::ev/change] args)))
(defn- ui! [k v] (rf/dispatch [::ev/set-ui k v]))
(defn- roll! [check] (rf/dispatch [::roller-ev/open-check check]))
(defn- value [e] (.. e -target -value))
(defn- kind-of [item] (some-> (:kind item) name))
(defn- raise [n] (fn [v] (max 0 (+ (or v 0) n))))
(defn- day [at] (when at (.toLocaleDateString (js/Date. at))))
(defn- whole [v] (let [n (js/parseInt v 10)] (if (js/isNaN n) 0 n)))

;; The window's width, which decides whether Skills is a panel or a tab. Same breakpoint as .main in css/game.css.
(defonce ^:private desk?
  (let [query (js/window.matchMedia "(min-width: 1000px)")
        desk (reagent/atom (.-matches query))]
    (.addEventListener query "change" #(reset! desk (.-matches %)))
    desk))

;; ---------- Header ----------

(defn- portrait-control [c read-only? uploading?]
  [:div.portrait
   [portrait/face c]
   (when-not read-only?
     [:label.linkbtn (if uploading? "Uploading…" (if (:portrait-key c) "Change" "Add portrait"))
      [:input.sr {:type "file" :accept "image/*" :disabled uploading?
                  :on-change #(when-let [file (aget (.. % -target -files) 0)] (rf/dispatch [::ev/upload file]))}]])])

(defn- xp-box [c ix read-only?]
  (let [available (+ (r/xp-left c ix) (:earned-xp c 0))]
    [:div.xp [:b available " XP"] [:span "available of " (+ (r/budget c ix) (:earned-xp c 0))]
     (when-not read-only?
       [:div [:a.linkbtn {:href (routes/href {:page :builder :character-id (:id c)})} "Edit character"]])]))

(defn- save-note [state]
  [:span.savestate {:role "status"}
   (case state :saving "Saving…" :saved "Saved" :error "Not saved" :conflict "Out of date")])

(defn- header [c ix read-only? gm? uploading? state]
  [:section.ident {:aria-label "Character"}
   [portrait-control c read-only? uploading?]
   [:div
    [:h1 (:name c)]
    [:div.sub (str/join " · " (remove str/blank? [(:name (r/archetype c ix)) (:name (r/career c ix)) (:tagline c)]))]
    (cond read-only? [:span.savestate "Read only"]
          gm? [:<> [:span.tag.gm "Editing as GM"] " " [save-note state]]
          :else [save-note state])]
   [xp-box c ix read-only?]])

(defn- stats [c ix]
  (let [{:keys [chars]} (r/bonuses c ix)]
    (into [:section.stats {:aria-label "Characteristics"}]
          (for [k r/characteristics]
            (let [n (r/characteristic-total c ix k) stat (r/characteristic-names k) bonus (get chars k)]
              ^{:key k}
              [:button.stat {:type "button" :aria-label (str "Roll " stat " " n)
                             :title (when bonus (str "Includes +" bonus " from Dedication or cybernetics"))
                             :on-click #(roll! (r/characteristic-check c ix k))}
               [:b n] [:span stat (when bonus " ⁺")]])))))

;; ---------- Vitals ----------

(defn- pips [cur max-n kind]
  (let [n (max max-n cur)]
    (into [:div.pips {:class kind :style {"--n" n} :aria-hidden true}]
          (for [i (range n)] [:i {:class (cond (and (< i cur) (>= i max-n)) "over" (< i cur) "on")}]))))

(defn- hit-input [hit]
  [:span.hit
   [:label.sr {:for "hit"} "Incoming damage"]
   [:input {:id "hit" :type "number" :input-mode "numeric" :min 0 :placeholder "Dmg" :value hit
            :on-change #(ui! :hit (value %))}]
   [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/take-hit])} "Take hit"]])

(defn- track [title kind field cur max-n read-only? less more extra]
  (let [over? (> cur max-n)]
    [:div.card.track {:class (when over? "over")}
     [:div.hd [:span.label title] [:span.val [:b cur] " / " max-n]]
     [pips cur max-n kind]
     (when-not read-only?
       [:div.ctl
        [:button.btn.step {:type "button" :aria-label less :disabled (zero? cur) :on-click #(change! update field (raise -1))} "−"]
        [:button.btn.step {:type "button" :aria-label more :on-click #(change! update field (raise 1))} "+"]
        extra])
     (when over? [:div.alert "Incapacitated: " (str/lower-case title) " exceed threshold"])]))

(defn- vitals [c ix hit read-only?]
  (let [{:keys [wounds strain soak defense encumbrance encumbrance-max]} (r/derived c ix)
        over? (> encumbrance encumbrance-max)]
    [:section.vitals {:aria-label "Vitals"}
     [track "Wounds" "wound" :wounds-taken (:wounds-taken c 0) wounds read-only? "Heal 1 wound" "Take 1 wound" [hit-input hit]]
     [track "Strain" "strain" :strain-taken (:strain-taken c 0) strain read-only? "Recover 1 strain" "Suffer 1 strain" nil]
     [:div.card.small [:span.label "Soak"] [:b soak]]
     [:div.card.small [:span.label "Defense"]
      [:div.pair [:div [:b defense] [:small "Melee"]] [:div [:b defense] [:small "Ranged"]]]]
     [:div.card.small [:span.label "Encumbrance"]
      [:b encumbrance [:span {:style {:font-size 18 :color "var(--muted)"}} " / " encumbrance-max]]
      [:div.enc-bar [:i {:style (cond-> {:width (str (min 100 (* 100 (/ encumbrance (max 1 encumbrance-max)))) "%")}
                                  over? (assoc :background "var(--bad)"))}]]
      (when over? [:span.why "Encumbered: +" (- encumbrance encumbrance-max) " setback on Agility and Brawn checks"])]]))

;; ---------- Tabs ----------

(defn- skill-row [c ix skill]
  (let [rk (r/rank-total c ix skill) career? (r/career-skill? c ix skill)
        bonus (- rk (r/rank-of c ix skill))]
    [:button.srow {:type "button" :aria-label (str "Roll " (:name skill) ", rank " rk (when career? ", career skill"))
                   :on-click #(roll! (r/skill-check c ix skill))}
     [:span.nm [:span.dot {:class (when-not career? "off") :title (if career? "Career skill" "Non-career skill")}]
      (:name skill) [:i (r/characteristic-short (keyword (:characteristic skill)))]
      (when (pos? bonus) [:i {:title "Includes a rank from cybernetics"} " +" bonus " cyber"])]
     (into [:span.ranks {:aria-label (str "Rank " rk)}] (for [i (range 5)] [:i {:class (when (< i rk) "on")}]))
     [:span.mini [text/pool-dice (r/skill-pool c ix skill)]]]))

(defn- tab-skills [c ix q]
  (let [needle (str/lower-case (str/trim q))
        hit? #(str/includes? (str/lower-case (:name %)) needle)]
    [:<>
     [:label.sr {:for "q2"} "Filter skills"]
     [:input.search {:id "q2" :type "search" :placeholder "Filter skills…" :value q :auto-complete "off" :on-change #(ui! :q (value %))}]
     (doall
      (for [group r/skill-groups :let [rows (filter #(and (= group (:category %)) (hit? %)) (:skills ix))] :when (seq rows)]
        ^{:key group}
        [:div.group [:span.label group]
         (for [skill rows] ^{:key (:id skill)} [skill-row c ix skill])]))]))

(defn- quality-text [{:keys [qualities]}]
  (map #(str (:name %) (when (:rating %) (str " " (:rating %)))) qualities))

(defn- weapon-card [c ix w]
  (let [attack (r/weapon-check c ix w)]
    [:div.item.weap
     [:div
      [:div.row1 [:h3 (:name w)] [:span.meta (:skill w)] [text/cite (:source w)]]
      [:div.stats-row
       [:span "Dmg " [:b (r/weapon-damage c ix w)]
        (when (:adds-brawn w) (str " (Br+" (:damage-bonus w 0) ")"))]
       [:span "Crit " [:b (:crit w)]] [:span "Range " [:b (:range w)]]
       (for [q (quality-text w)] ^{:key q} [:span q])]]
     (when attack
       [:button.btn.primary {:type "button" :aria-label (str "Roll " (:label attack)) :on-click #(roll! attack)} "Attack"])]))

(defn- by-kind [c ix kind] (filter #(= kind (kind-of %)) (r/carried c ix)))

(defn- figured [rows]
  (str/join " · " (map (fn [[label n]] (str label " " (if (neg? n) (str "−" (- n)) n))) rows)))

(def ^:private adjustable [[:wounds "Wound threshold"] [:strain "Strain threshold"] [:soak "Soak"] [:defense "Defense"]])

(defn- adjustments
  "The GM's overrides, added after everything else. Players see them, and only the GM changes them."
  [c gm?]
  (let [set? (some #(not (zero? (get-in c [:adjust (first %)] 0))) adjustable)]
    (when (or gm? set?)
      [:div.sect [:h3 "GM adjustments"]
       [:p.note "Added after everything else: a g-mod mutation, a lasting injury, a ruling."]
       (if gm?
         [:div.adjust
          (for [[k label] adjustable]
            ^{:key k}
            [:label.field [:span label]
             [:input {:type "number" :value (get-in c [:adjust k] 0) :on-change #(change! assoc-in [:adjust k] (whole (value %)))}]])
          [:label.field.wide [:span "Why"]
           [:input {:value (:adjust-note c) :on-change #(change! assoc :adjust-note (value %))}]]]
         [:<> (into [:dl.kv] (mapcat (fn [[k label]] (let [n (get-in c [:adjust k] 0)]
                                                       (when-not (zero? n) [[:dt label] [:dd (if (pos? n) (str "+" n) n)]]))))
                    adjustable)
          (when (seq (:adjust-note c)) [:p (:adjust-note c)])])])))

(defn- tab-combat [c ix gm?]
  (let [{:keys [soak defense]} (r/derived c ix) weapons (by-kind c ix "weapon") p (r/derived-parts c ix)]
    [:<>
     [:div.sect [:h3 "Attacks"]
      (if (seq weapons)
        (into [:div.list] (map (fn [w] ^{:key (:id w)} [weapon-card c ix w])) weapons)
        [:p.note "No weapons."])]
     [:div.sect [:h3 "Defenses"]
      [:dl.kv [:dt "Soak"] [:dd soak] [:dt "Defense"] [:dd "Melee " defense ", ranged " defense]]]
     [:div.sect [:h3 "How it's figured"]
      [:dl.kv [:dt "Wounds"] [:dd (figured (:wounds p))] [:dt "Strain"] [:dd (figured (:strain p))]
       [:dt "Soak"] [:dd (figured (:soak p))]]]
     [adjustments c gm?]]))

(defn- dedication-picks
  "One characteristic per rank of Dedication."
  [c ix read-only?]
  (let [{:keys [rank picks]} (r/owned-talent c mods/dedication)]
    (into [:div.picks]
          (for [i (range rank)]
            ^{:key i}
            [:label.field [:span "Rank " (inc i) " raises"]
             (into [:select {:value (or (get (vec picks) i) "") :disabled read-only?
                             :on-change #(change! r/set-dedication i (not-empty (value %)))}
                    [:option {:value ""} "Choose a characteristic"]]
                   (for [k (r/dedication-options c ix i)] ^{:key k} [:option {:value (name k)} (r/characteristic-names k)]))]))))

(defn- tab-talents [c ix read-only?]
  (let [owned (sort-by (juxt :tier :name) (keep #(some-> (get-in ix [:talents (:id %)]) (assoc :rank (:rank %))) (:talents c)))]
    (if (seq owned)
      (into [:div.list]
            (for [{:keys [id name rank tier activation ranked source text]} owned]
              ^{:key id}
              [:div.item
               [:div.row1 [:h3 name (when ranked [:span.mono {:style {:font-weight 400}} " " rank])]
                [:span.tag "Tier " tier] [:span.tag activation] (when ranked [:span.tag "Ranked"]) [text/cite source]]
               [:p [text/rich text]]
               (when (= (keyword mods/dedication) (keyword id)) [dedication-picks c ix read-only?])]))
      [:p.note "No talents yet."])))

(defn- unit-pick [item options read-only? u]
  ^{:key u}
  [:label.field [:span (if (> (:qty item 1) 1) (str "Unit " (inc u)) "Which")]
   (into [:select {:value (or (get (:picks item) u) "") :disabled read-only?
                   :on-change #(change! r/set-pick item u (not-empty (value %)))}
          [:option {:value ""} "Choose…"]]
         (for [[id label] options] ^{:key id} [:option {:value id} label]))])

(defn- mod-detail
  "What a body modification does, and the choice it needs."
  [item read-only?]
  (when-let [{:keys [note options]} (mods/body-mod item)]
    (let [chosen (keep (fn [[id _ e]] (when (some #{id} (:picks item)) (:note e))) options)]
      [:<>
       (when note [:p [text/rich note]])
       (when options (into [:div.picks] (map #(unit-pick item options read-only? %)) (range (:qty item 1))))
       (for [n (distinct chosen)] ^{:key n} [:p n])])))

(defn- gear-section [title items line read-only?]
  (when (seq items)
    [:div.sect [:h3 title]
     (into [:div.list]
           (for [g items]
             ^{:key (str (:id g) (when (:free g) "-free"))}
             [:div.item [:div.row1 [:h3 (:name g)] [:span.meta (line g)] [text/cite (:source g)]]
              [mod-detail g read-only?]]))]))

(defn- tab-gear [c ix read-only?]
  (let [{:keys [encumbrance encumbrance-max]} (r/derived c ix)
        worn (:id (r/best-armor c ix))
        enc #(str "Enc " (* (:encumbrance % 0) (:qty %)))]
    [:<>
     [:div.sect [:dl.kv [:dt "Credits"] [:dd.mono (+ (r/credits-left c ix) (or (:pocket c) 0))]
                 [:dt "Encumbrance"] [:dd encumbrance " of " encumbrance-max]]]
     [gear-section "Weapons" (by-kind c ix "weapon") enc read-only?]
     [gear-section "Armor" (by-kind c ix "armor")
      #(str (when (= (:id %) worn) "Worn · ") "Soak +" (:soak % 0) " · Defense " (:defense % 0) " · " (enc %)) read-only?]
     [gear-section "Gear" (by-kind c ix "gear") #(str (when (:free %) "Free · ") "×" (:qty %) " · " (enc %)) read-only?]]))

(def ^:private heal-difficulty ["Can't be healed" "Easy to heal" "Average to heal" "Hard to heal" "Daunting to heal"])

(defn- crit-card [ix i injury read-only?]
  (let [{:keys [name severity roll at source text]} (r/crit-entry ix injury)]
    [:div.item
     [:div.row1 [:h3 name] (when severity [:span.tag (heal-difficulty severity)])
      [:span.meta "rolled " roll (when at (str " · " (day at)))] (when source [text/cite source])]
     (when text [:p [text/rich text]])
     (when-not read-only?
       [:div.acts [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/heal-crit i])} "Mark healed"]])]))

(defn- crit-roller [c {:keys [raw bonus total name]}]
  [:div.critroll
   [:button.btn.danger {:type "button" :on-click #(rf/dispatch [::ev/roll-crit (inc (rand-int 100))])} "Roll critical injury"]
   [:span.meta "d100 + " (r/crit-bonus c) " for " (count (:crits c)) " active"]
   (when name [:span {:role "status"} "Rolled " [:b raw] " + " bonus " = " [:b total] ": " [:strong name]])])

(defn- tab-crits [c ix last-crit read-only?]
  [:<>
   (when-not read-only? [crit-roller c last-crit])
   (if (seq (:crits c))
     (into [:div.list] (map-indexed (fn [i injury] ^{:key i} [crit-card ix i injury read-only?])) (:crits c))
     [:p.note "No active critical injuries."])])

(defn- obligation-box [c ix]
  (let [ob (r/obligation c ix) {:keys [magnitude attachment why]} (:ob c)
        title (if (= "secret" (some-> ob :kind name)) "Secret" "Obligation")]
    (when ob
      [:div.sect [:h3 title]
       [:div.item
        [:div.debt-big [:b magnitude]
         [:span (:name ob) (when (seq attachment) [:<> ", tied to " [:span.corp attachment]])]]
        (when (seq why) [:p why])]])))

(defn- motives-box [c]
  [:div.sect [:h3 "Motivations"]
   (into [:div.list]
         (for [t r/motive-types :let [{:keys [name text]} (get-in c [:motives (r/motive-key t)])] :when (seq name)]
           ^{:key t} [:div.item [:div.row1 [:span.label t] [:h3 name]] [:p text]]))])

(defn- tab-story [c ix]
  [:<>
   [obligation-box c ix]
   [motives-box c]
   (when (seq (:s0 c)) [:div.sect [:h3 "Session 0"] [:div.item [:p (:s0 c)]]])])

(defn- tab-notes [c read-only?]
  [:<>
   [:label.label {:for "notes"} "Notes only you and the GM can see"]
   [:textarea {:id "notes" :value (:notes c) :read-only read-only? :on-change #(change! assoc :notes (value %))}]
   (when-not read-only? [:p.note "Saves as you type."])])

(def ^:private tabs [[:skills "Skills"] [:combat "Combat"] [:talents "Talents"] [:gear "Gear"] [:crits "Crits"]
                     [:story "Obligation & story"] [:notes "Notes"]])

(defn- shown-tab
  "Skills is the phone's first tab. A desktop has it in its own panel, so it opens on Combat."
  [tab desk]
  (if (and desk (= :skills tab)) :combat tab))

(defn- tab-count [c ix id]
  (let [n (case id :talents (count (:talents c)) :gear (count (r/carried c ix)) :crits (count (:crits c)) 0)]
    (when (pos? n) [:span.count n])))

(defn- tab-body [c ix {:keys [q last-crit]} tab read-only?]
  (case tab
    :combat [tab-combat c ix @(rf/subscribe [::subs/gm?])]
    :talents [tab-talents c ix read-only?]
    :gear [tab-gear c ix read-only?]
    :crits [tab-crits c ix last-crit read-only?]
    :story [tab-story c ix]
    :notes [tab-notes c read-only?]
    [tab-skills c ix q]))

(defn- tabbed [c ix sheet desk read-only?]
  (let [tab (shown-tab (:tab sheet) desk)]
    [:section.card
     (into [:nav.tabs.sticky {:role "tablist" :aria-label "Sheet sections"}]
           (for [[id label] tabs :when (not (and desk (= :skills id)))]
             ^{:key id} [:button {:type "button" :role "tab" :aria-selected (= tab id) :on-click #(ui! :tab id)}
                         label [tab-count c ix id]]))
     [:div.tabbody {:role "tabpanel" :class (when (= :skills tab) "flush")} [tab-body c ix sheet tab read-only?]]]))

(defn- skills-panel [c ix q]
  [:section.card.panel {:aria-labelledby "h-skills"}
   [:header [:h2#h-skills "Skills"] [:span.label "Tap to roll"]]
   [tab-skills c ix q]])

(defn- panels [c ix sheet read-only?]
  (let [desk @desk?]
    [:main.main
     (when desk [skills-panel c ix (:q sheet)])
     [tabbed c ix sheet desk read-only?]]))

(defn- banners [{:keys [conflict? error character]}]
  [:<>
   (when conflict?
     [:p.banner {:role "alert"} "This character changed somewhere else, so this copy has stopped saving. "
      [:button.linkbtn {:type "button" :on-click #(rf/dispatch [::ev/open (:id character)])} "Reload the latest"]])
   (when error
     [:p.banner {:role "alert"} error " "
      [:button.linkbtn {:type "button" :on-click #(rf/dispatch [::ev/retry])} "Try again"]])])

(defn page []
  (let [{:keys [status character error uploading? hit] :as sheet} @(rf/subscribe [::subs/sheet])
        ix @(rf/subscribe [::content/ix])
        read-only? @(rf/subscribe [::subs/read-only?])
        gm? @(rf/subscribe [::subs/gm?])
        state @(rf/subscribe [::subs/save-state])]
    [:div.page-sheet {:data-palette (or (:theme character) theme/default-id)}
     [:div.wrap
      (cond (= :error status) [:p.banner {:role "alert"} error]
            (= :error @(rf/subscribe [::content/status])) [:p.banner {:role "alert"} @(rf/subscribe [::content/error])]
            (or (not= :ready status) (nil? ix)) [:p.note "Loading…"]
            :else
            [:<>
             [banners sheet]
             [header character ix read-only? gm? uploading? state]
             (when-let [names (and (not read-only?) (seq (r/unchosen character ix)))]
               [:p.banner {:role "status"} "Choose what your " (str/join " and " names)
                " improves, on the Talents or Gear tab, so it counts."])
             [stats character ix]
             [vitals character ix hit read-only?]
             [panels character ix sheet read-only?]
             [roller/drawer]])]]))
