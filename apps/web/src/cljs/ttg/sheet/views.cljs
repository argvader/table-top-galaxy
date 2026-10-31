(ns ttg.sheet.views
  "The character sheet, with the prototype's markup and classes (css/game.css). Vitals, notes and portrait are
   edited here and save themselves; everything bought goes through the builder."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [ttg.content :as content]
            [ttg.genesys.rules :as r]
            [ttg.routes :as routes]
            [ttg.sheet.events :as ev]
            [ttg.sheet.subscriptions :as subs]
            [ttg.theme :as theme]
            [ttg.ui.portrait :as portrait]
            [ttg.ui.text :as text]))

(defn- change! [& args] (rf/dispatch (into [::ev/change] args)))
(defn- ui! [k v] (rf/dispatch [::ev/set-ui k v]))
(defn- value [e] (.. e -target -value))
(defn- kind-of [item] (some-> (:kind item) name))
(defn- raise [n] (fn [v] (max 0 (+ (or v 0) n))))

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

(defn- header [c ix read-only? uploading? state]
  [:section.ident {:aria-label "Character"}
   [portrait-control c read-only? uploading?]
   [:div
    [:h1 (:name c)]
    [:div.sub (str/join " · " (remove str/blank? [(:name (r/archetype c ix)) (:name (r/career c ix)) (:tagline c)]))]
    (if read-only? [:span.savestate "Read only"] [save-note state])]
   [xp-box c ix read-only?]])

(defn- stats [c ix]
  (into [:section.stats {:aria-label "Characteristics"}]
        (for [k r/characteristics]
          ^{:key k} [:div.stat [:b (r/characteristic c ix k)] [:span (r/characteristic-names k)]])))

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
  (let [rk (r/rank-of c ix skill) career? (r/career-skill? c ix skill)]
    [:div.srow
     [:span.nm [:span.dot {:class (when-not career? "off") :title (if career? "Career skill" "Non-career skill")}]
      (:name skill) [:i (r/characteristic-short (keyword (:characteristic skill)))]]
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
  (let [brawn (r/characteristic c ix :brawn)]
    [:div.item.weap
     [:div
      [:div.row1 [:h3 (:name w)] [:span.meta (:skill w)] [text/cite (:source w)]]
      [:div.stats-row
       [:span "Dmg " [:b (if (:adds-brawn w) (+ brawn (:damage-bonus w 0)) (:damage w))]
        (when (:adds-brawn w) (str " (Br+" (:damage-bonus w 0) ")"))]
       [:span "Crit " [:b (:crit w)]] [:span "Range " [:b (:range w)]]
       (for [q (quality-text w)] ^{:key q} [:span q])]]]))

(defn- by-kind [c ix kind] (filter #(= kind (kind-of %)) (r/carried c ix)))

(defn- tab-combat [c ix]
  (let [{:keys [soak defense]} (r/derived c ix) weapons (by-kind c ix "weapon")]
    [:<>
     [:div.sect [:h3 "Attacks"]
      (if (seq weapons)
        (into [:div.list] (map (fn [w] ^{:key (:id w)} [weapon-card c ix w])) weapons)
        [:p.note "No weapons."])]
     [:div.sect [:h3 "Defenses"]
      [:dl.kv [:dt "Soak"] [:dd soak] [:dt "Defense"] [:dd "Melee " defense ", ranged " defense]]]]))

(defn- tab-talents [c ix]
  (let [owned (sort-by (juxt :tier :name) (keep #(some-> (get-in ix [:talents (:id %)]) (assoc :rank (:rank %))) (:talents c)))]
    (if (seq owned)
      (into [:div.list]
            (for [{:keys [id name rank tier activation ranked source text]} owned]
              ^{:key id}
              [:div.item
               [:div.row1 [:h3 name (when ranked [:span.mono {:style {:font-weight 400}} " " rank])]
                [:span.tag "Tier " tier] [:span.tag activation] (when ranked [:span.tag "Ranked"]) [text/cite source]]
               [:p [text/rich text]]]))
      [:p.note "No talents yet."])))

(defn- gear-section [title items line]
  (when (seq items)
    [:div.sect [:h3 title]
     (into [:div.list]
           (for [g items]
             ^{:key (:id g)}
             [:div.item [:div.row1 [:h3 (:name g)] [:span.meta (line g)] [text/cite (:source g)]]]))]))

(defn- tab-gear [c ix]
  (let [{:keys [encumbrance encumbrance-max]} (r/derived c ix)
        worn (:id (r/best-armor c ix))
        enc #(str "Enc " (* (:encumbrance % 0) (:qty %)))]
    [:<>
     [:div.sect [:dl.kv [:dt "Credits"] [:dd.mono (+ (r/credits-left c ix) (or (:pocket c) 0))]
                 [:dt "Encumbrance"] [:dd encumbrance " of " encumbrance-max]]]
     [gear-section "Weapons" (by-kind c ix "weapon") enc]
     [gear-section "Armor" (by-kind c ix "armor")
      #(str (when (= (:id %) worn) "Worn · ") "Soak +" (:soak % 0) " · Defense " (:defense % 0) " · " (enc %))]
     [gear-section "Gear" (by-kind c ix "gear") #(str "×" (:qty %) " · " (enc %))]]))

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

(def ^:private tabs [[:skills "Skills"] [:combat "Combat"] [:talents "Talents"] [:gear "Gear"]
                     [:story "Obligation & story"] [:notes "Notes"]])

(defn- tab-body [c ix {:keys [tab q]} read-only?]
  (case tab
    :combat [tab-combat c ix]
    :talents [tab-talents c ix]
    :gear [tab-gear c ix]
    :story [tab-story c ix]
    :notes [tab-notes c read-only?]
    [tab-skills c ix q]))

(defn- tabbed [c ix {:keys [tab] :as sheet} read-only?]
  [:section.card
   (into [:nav.tabs.sticky {:role "tablist" :aria-label "Sheet sections"}]
         (for [[id label] tabs]
           ^{:key id} [:button {:type "button" :role "tab" :aria-selected (= tab id) :on-click #(ui! :tab id)} label]))
   [:div.tabbody {:role "tabpanel"} [tab-body c ix sheet read-only?]]])

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
        state @(rf/subscribe [::subs/save-state])]
    [:div.page-sheet {:data-palette (or (:theme character) theme/default-id)}
     [:div.wrap
      (cond (= :error status) [:p.banner {:role "alert"} error]
            (= :error @(rf/subscribe [::content/status])) [:p.banner {:role "alert"} @(rf/subscribe [::content/error])]
            (or (not= :ready status) (nil? ix)) [:p.note "Loading…"]
            :else
            [:<>
             [banners sheet]
             [header character ix read-only? uploading? state]
             [stats character ix]
             [vitals character ix hit read-only?]
             [:main.main [tabbed character ix sheet read-only?]]])]]))
