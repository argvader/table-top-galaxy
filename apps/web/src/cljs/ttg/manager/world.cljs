(ns ttg.manager.world
  "The campaign's world: its cast, factions, secrets and arc."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [ttg.manager.doc :as doc]
            [ttg.manager.drawers :as drawers]
            [ttg.manager.party :as party]
            [ttg.manager.subscriptions :as sub]
            [ttg.manager.ui :as ui]
            [ttg.ui.text :as text]))

(defn- owner-name
  "Whoever a secret belongs to: a corp, a power or a person."
  [{:keys [corps powers]} npcs id]
  (or (:short (first (filter #(= id (:id %)) corps)))
      (:name (first (filter #(= id (:id %)) powers)))
      (:name (first (filter #(= id (:id %)) npcs)))
      (get {"gate" "The gate" "consortium" "The consortium"} id)
      id))

(defn- secret-link [{:keys [id title state]}]
  ^{:key id}
  [:div.seed [ui/state-tag state]
   [:button.linkbtn {:type "button" :on-click #(ui/open! {:kind :secret :id id})} title]])

;; ---------- Cast ----------

(defn- npc-card [content ledger {:keys [id name role faction pub gm met]}]
  ^{:key id}
  [:div.item.npc
   [:div.row1 [:h3 [:button {:type "button" :on-click #(ui/open! {:kind :npc :id id})} name]]
    (if (some? met) [:span.tag "met " (ui/session-label met)] [:span.tag.gm "not met"])]
   [:span.sub role " · " (if faction (owner-name content [] faction) "Consortium")]
   (when (seq pub) [:p pub])
   [ui/gm-block [:p gm] (doall (map secret-link (filter #(= id (:owner %)) ledger)))]])

(defn- matches? [query group {n :name :keys [role] g :group}]
  (and (or (= "all" group) (= group g))
       (str/includes? (str/lower-case (str n " " role)) (str/lower-case query))))

(defn cast-tab []
  (let [{:keys [groups] :as content} @(rf/subscribe [::sub/content])
        {:keys [npcs ledger]} @(rf/subscribe [::sub/doc])
        {:keys [group query]} @(rf/subscribe [::sub/ui])
        shown (filter #(matches? query group %) npcs)]
    [:<>
     [ui/intro "Cast" "Everyone aboard who matters."
      [:input.search {:placeholder "Search the cast" :aria-label "Search the cast" :value query
                      :on-change #(ui/set-ui! :query (.. % -target -value))}]]
     (when (< (count (filter #(= "town" (:group %)) npcs)) 5)
       [:p.warnline "Name at least five townsfolk with small arcs in Ki: they're the stakes in Ketsu."])
     [:div.chips
      (doall (for [[id label] (cons ["all" "All"] (map (fn [[k v]] [(name k) v]) groups))]
               ^{:key id} [:button.chip {:type "button" :aria-pressed (= id group) :on-click #(ui/set-ui! :group id)} label]))]
     (if (seq shown)
       [:div.grid (doall (map #(npc-card content ledger %) shown))]
       [:div.empty "No one matches."])]))

;; ---------- Factions ----------

(defn- exposed [{:keys [state revealedIn reveal truth]}]
  (when (= "revealed" state)
    [:div.item.known [:span.label "Exposed " (ui/session-label revealedIn)] [:p (or (not-empty reveal) truth)]]))

(defn- corp-card [{:keys [id name source pub share breaks ledger]}]
  (let [doc @(rf/subscribe [::sub/doc])
        l (first (filter #(= ledger (:id %)) (:ledger doc)))
        people (filter #(= id (:faction %)) (:npcs doc))
        debtors (filter #(= id (:creditor %)) @(rf/subscribe [::sub/party]))]
    ^{:key id}
    [:section.card.corp {:style {:padding "14px"}} [:h3 name " " [text/cite source]]
     [:dl.kv [:dt "Role aboard"] [:dd pub] [:dt "Share"] [:dd share]
      (when (seq people)
        [:<> [:dt "People"]
         [:dd (doall (for [{nid :id n :name} people]
                       ^{:key nid} [:button.linkbtn {:type "button" :on-click #(ui/open! {:kind :npc :id nid})} n " "]))]])
      [:dt "Holds Debt"]
      [:dd (if (seq debtors)
             (doall (for [{:keys [debt] did :id :as p} debtors] ^{:key did} [:span.debtor [ui/pc-chip p] " " [:b.mono debt] " "]))
             [:span.sub "No one's"])]]
     [exposed l]
     [ui/gm-block [:p [:b "Secret agenda. "] (:truth l)]
      [:div.lrow [:span.note "Breaks " breaks] [drawers/state-buttons l]]
      [:button.linkbtn {:type "button" :on-click #(ui/open! {:kind :secret :id (:id l)})}
       "Clues: " (count (filter #(some? (:s %)) (:clues l))) " of " (count (:clues l)) " planted"]]]))

(defn- power-card [{:keys [id name pub gm ledger]}]
  (let [l (first (filter #(= ledger (:id %)) @(rf/subscribe [::sub/at [:ledger]])))]
    ^{:key id}
    [:div.item [:div.row1 [:h3 name]] [:p pub]
     (when l [exposed l])
     [ui/gm-block (if l [secret-link l] [:p gm])]]))

(defn- unclaimed-debt [pcs corps]
  (let [loose (filter #(and (:built? %) (nil? (:creditor %))) pcs)
        idle (remove (fn [c] (some #(= (:id c) (:creditor %)) pcs)) corps)]
    (when (and (seq pcs) (or (seq loose) (seq idle)))
      [:p.warnline "Tie each Debt to a corp so a triggered Debt pulls its agenda into the session. "
       (when (seq loose) (str (str/join ", " (map party/first-name loose)) ": not a corp. "))
       (when (seq idle) (str (str/join " and " (map :short idle)) " hold no one's Debt."))])))

(defn factions-tab []
  (let [{:keys [corps powers]} @(rf/subscribe [::sub/content])
        pcs @(rf/subscribe [::sub/party])]
    [:<>
     [ui/intro "Factions" "Every corp has a public role and a secret agenda, and each breaks at a different point."]
     [unclaimed-debt pcs corps]
     [:div.grid {:style {:grid-template-columns "repeat(auto-fill,minmax(min(100%,420px),1fr))"}} (doall (map corp-card corps))]
     [:section.card.panel [:header [:h2 "Other powers"]] [:div.pbody [:div.grid (doall (map power-card powers))]]]]))

;; ---------- Ledger ----------

(defn- planted [{:keys [clues]}] (count (filter #(some? (:s %)) clues)))

(defn- ledger-row [content npcs {:keys [id title owner truth clues] :as l}]
  ^{:key id}
  [:div.item
   [:div.lrow
    [:div [:div.row1 [:h3 [:button.linkbtn {:type "button" :on-click #(ui/open! {:kind :secret :id id})} title]]]
     [:div.meta [:span (owner-name content npcs owner)] [:span (planted l) "/" (count clues) " clues"]]]
    [drawers/state-buttons l]]
   [:p.sub truth]])

(defn ledger-tab []
  (let [{:keys [acts] :as content} @(rf/subscribe [::sub/content])
        {:keys [ledger npcs]} @(rf/subscribe [::sub/doc])
        tally (fn [st] (count (filter #(= st (:state %)) ledger)))]
    [:<>
     [ui/intro "Betrayal ledger" "Which secrets the players have uncovered, so the reveals stay consistent." [ui/gm-tag]]
     [:div.tally
      (doall (for [st ["hidden" "hinted" "revealed"]] ^{:key st} [:div.t [:b (tally st)] [:span st]]))
      [:div.t [:b (reduce + (map planted ledger))] [:span "clues planted"]]]
     (doall (for [{:keys [id]} acts
                  :let [rows (filter #(= id (:act %)) ledger)]
                  :when (seq rows)]
              ^{:key id}
              [:section.card.panel [:header [:h2 "Breaks in " (ui/act-label acts id)]]
               [:div.pbody (doall (map #(ledger-row content npcs %) rows))]]))]))

;; ---------- Arc ----------

(defn- session-row [{:keys [n title status recap choices]}]
  ^{:key n}
  [:button.item {:type "button" :on-click #(ui/open! {:kind :session :n n})}
   [:div.sesh {:class (case status "now" "now" "plan" "plan" "")} [:span.n n]
    [:div [:b (or (not-empty title) "Untitled")
           (case status "now" [:span.tag.now " tonight"] "plan" [:span.tag.gm " planned"] nil)]
     (when (seq recap) [:span.recap recap])
     (when (seq choices) (into [:div.choices] (for [c choices] ^{:key c} [:span.choice c])))]]])

(defn- thread-button [[id text]]
  (let [k (keyword id) st (or (ui/at [:threads k]) "plan")]
    ^{:key id}
    [:button.thread {:type "button" :class st :on-click #(ui/op! doc/cycle-thread k)}
     [:i ({"plan" "planned" "live" "in play" "done" "done"} st)] [:span text]]))

(defn- pays-off [act]
  (let [{:keys [answers]} @(rf/subscribe [::sub/at [:s0]])
        pcs @(rf/subscribe [::sub/party])
        here (for [[qid list] answers a list :when (= act (:act a))] [qid a])]
    [:div [:span.label "Pays off here"]
     (if (seq here)
       [:div.payoff
        (doall (for [[qid {:keys [pc]}] here]
                 ^{:key (str (name qid) pc)}
                 [:button.chip {:type "button" :on-click #(ui/open! {:kind :question :id (name qid)})}
                  [ui/pc-chip (first (filter (fn [p] (= pc (:id p))) pcs)) (str " · " (str/upper-case (name qid)))]]))]
       [:span.note " No one's Session 0 answer yet."])]))

(defn- twist-panel []
  (let [{:keys [twists seeds]} @(rf/subscribe [::sub/content])
        {:keys [twist ledger]} @(rf/subscribe [::sub/doc])]
    [:<>
     [:div [:span.label "Pick the twist"]
      (doall (for [{:keys [n title text]} twists]
                 ^{:key n}
                 [:label.twist [:input {:type "radio" :name "twist" :checked (= twist n) :on-change #(ui/edit! [:twist] n)}]
                  [:span [:b n ". " title] [:br] [:span.sub text]]]))]
     [:div [:span.label "Seeds from the voyage"]
      (doall (for [{:keys [text for] lid :ledger} seeds
                   :when (or (nil? for) (nil? twist) (= for twist))
                   :let [{:keys [state] :as l} (first (filter #(= lid (:id %)) ledger))]]
               ^{:key lid}
               [:div.seed [ui/state-tag state]
                [:button.linkbtn {:type "button" :on-click #(ui/open! {:kind :secret :id lid})} text]
                [:span.note (planted l) "/" (count (:clues l)) " clues planted"]]))]]))

(defn- act-card [view {:keys [id roman kanji name title length pub gm end threads]}]
  (let [{:keys [act sessions]} @(rf/subscribe [::sub/doc])
        here (filter #(= id (:act %)) sessions)]
    ^{:key id}
    [:section.act {:class [(when (= id act) "cur") (when (= id view) "shown")]}
     [:header [:span.kanji "Act " roman " · " kanji (when (seq length) (str " · " length))]
      [:h2 name " " [:small title] (when (= id act) [:span.tag.now "now"])]]
     (when (seq pub) [:p pub])
     [:div.sessions (if (seq here) (doall (map session-row here)) [:p.note "No sessions planned."])]
     [:button.linkbtn {:type "button" :on-click #(ui/op! doc/plan-session id)} "+ Plan a session"]
     [ui/gm-block [:p gm] [pays-off id]
      (when (seq threads) [:div [:span.label "Threads · tap to cycle"] [:div (doall (map thread-button threads))]])
      (cond (= "ten" id) [twist-panel]
            end [:p [:b "Ends with the crossing. "] (str/replace end #"^The crossing: " "")])]]))

(defn- untied-warning [acts]
  (let [{:keys [answers done]} @(rf/subscribe [::sub/at [:s0]])
        pcs @(rf/subscribe [::sub/party])
        tied (set (for [[qid list] answers a list :when (and (= "p" (subs (name qid) 0 1)) (some #(= (:id %) (:act a)) acts))] (:pc a)))
        untied (remove #(tied (:id %)) pcs)]
    (when (and done (seq untied))
      [:p.warnline "No act pays off " (str/join ", " (map party/first-name untied)) "'s Session 0 answer yet."])))

(defn arc-tab []
  (let [{:keys [acts]} @(rf/subscribe [::sub/content])
        act @(rf/subscribe [::sub/at [:act]])
        view (or (:act-view @(rf/subscribe [::sub/ui])) act)]
    [:<>
     [ui/intro "The arc" "Four acts in Kishōtenketsu. Sessions sit under their act; Session 0 answers show where they pay off."]
     [untied-warning acts]
     [:div.chips.actpick {:role "group" :aria-label "Act"}
      (doall (for [{:keys [id roman name]} acts]
               ^{:key id}
               [:button.chip {:type "button" :aria-pressed (= id view) :on-click #(ui/set-ui! :act-view id)}
                roman " · " name (when (= id act) " ●")]))]
     [:div.acts (doall (map #(act-card view %) acts))]]))
