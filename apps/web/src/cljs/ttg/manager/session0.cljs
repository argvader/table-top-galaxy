(ns ttg.manager.session0
  "Running Session 0: the run sheet, then the tools it points at."
  (:require [re-frame.core :as rf]
            [ttg.manager.doc :as doc]
            [ttg.manager.events :as ev]
            [ttg.manager.party :as party]
            [ttg.manager.subscriptions :as sub]
            [ttg.manager.ui :as ui]
            [ttg.routes :as routes]
            [ttg.ui.portrait :as portrait]))


(defn- answered-by
  "The personal question this PC has answered, if any."
  [answers id]
  (some (fn [[qid list]] (when (and (= "p" (subs (name qid) 0 1)) (some #(and (= id (:pc %)) (seq (:text %))) list)) qid))
        answers))

(defn- no-players []
  (let [{:keys [campaign-id]} @(rf/subscribe [::sub/manager])]
    [:div.empty "Nobody has joined yet. "
     [:a {:href (routes/href {:page :campaign :id campaign-id})} "Invite your players"] " from the campaign page."]))

;; ---------- Party ----------

(defn- progress [{:keys [built? archetype role portrait-key debt]}]
  (let [step (fn [done? label] [:span.tag {:class (when done? "ok")} label])]
    [:span.progress
     (step true "Joined") (step built? "Character") (step archetype (or archetype "Archetype"))
     (step role (or role "Career")) (step portrait-key "Portrait") (step (pos? debt) "Debt")]))

(defn- seat [i n {:keys [id player name] :as pc}]
  ^{:key id}
  [:div.seat
   [portrait/face {:name name :portrait-key (:portrait-key pc)}]
   [:div [:b name] (when (not= name player) [:span.sub " " player]) [progress pc]
    (when-let [cid (:character-id pc)]
      [:a.linkbtn {:href (routes/href {:page :sheet :id cid})} "Open sheet"])]
   [:span.moves
    [:button.btn {:type "button" :disabled (zero? i) :aria-label (str "Move " name " earlier")
                  :on-click #(rf/dispatch [::ev/move-seat id -1])} "↑"]
    [:button.btn {:type "button" :disabled (= i (dec n)) :aria-label (str "Move " name " later")
                  :on-click #(rf/dispatch [::ev/move-seat id 1])} "↓"]]])

(defn- party-progress []
  (let [pcs @(rf/subscribe [::sub/party])]
    (if (empty? pcs)
      [no-players]
      [:div [:p.note "Seat order: each player's crew question is about the next one down, and the last player's is about the first."]
       (doall (map-indexed #(seat %1 (count pcs) %2) pcs))])))

;; ---------- Run sheet ----------

(defn- jump [tab label]
  [:button.btn {:type "button" :on-click #(ui/set-ui! :s0-tab tab)} label])

(defn- block-body [tool {:keys [readAloud touchstones looksLike expectations roles buildRules questionsHow shipSteps close bid]}]
  (case tool
    "record" [:p "Start the recording before anyone sits down, and check the levels on a few words from each player."]
    "pitch" [:<> [:blockquote.readaloud readAloud] [:p [:b "Touchstones. "] touchstones] [:p [:b "A session looks like: "] looksLike]
             [jump :table "Tone dials"]]
    "safety" [:<> (into [:ul] (for [e expectations] ^{:key e} [:li e])) [jump :table "Lines and veils"]]
    "characters" [:<> [:p roles " " buildRules] [party-progress]]
    "questions" [:<> [:p questionsHow] [jump :questions "Questions and the d20"]]
    "ship" [:<> (into [:ol] (for [e shipSteps] ^{:key e} [:li e])) [jump :ship "The ship"]]
    "bid" [:<> [:p (:how bid)] [jump :bid "The bid"]]
    "debt" [jump :debt "Debt"]
    "world" [jump :questions "World questions"]
    "wrap" [:<> [:p close] [jump :wrap "Wrap-up checklist"]]
    nil))

(defn- run-row [run open {:keys [id time title output tool]}]
  (let [done? (get run (keyword id)) open? (= id open)]
    ^{:key id}
    [:div.runrow {:class (when done? "done")}
     [:input {:type "checkbox" :checked (boolean done?) :aria-label (str title " done")
              :on-change #(ui/edit! [:s0 :run (keyword id)] (not done?))}]
     [:div [:b title] (when (seq output) [:span.sub " " output]) [:div.when time]]
     (when (seq tool)
       [:button.linkbtn {:type "button" :aria-expanded open? :on-click #(ui/set-ui! :block (when-not open? id))} (if open? "Close" "Open")])
     (when open? [:div.runbody [block-body tool (:run @(rf/subscribe [::sub/content]))]])]))

(defn- run-sheet []
  (let [{:keys [run]} @(rf/subscribe [::sub/content])
        done @(rf/subscribe [::sub/at [:s0 :run]])
        open (:block @(rf/subscribe [::sub/ui]))]
    [:section.card.panel [:header [:h2 "Run sheet" [:small "tick each block as you finish it"]]]
     [:div.pbody [:p.note (:summary run) " Bring: " (:bring run)]
      (doall (map #(run-row done open %) (:agenda run)))]]))

;; ---------- Questions ----------

(defn- answer-line [pcs act-names {:keys [pc about text follow act]}]
  (let [by-id #(first (filter (fn [p] (= % (:id p))) pcs))]
    [:div.ans [ui/pc-chip (or (by-id pc) {:name "Someone" :color "var(--muted)"}) (when-let [a (by-id about)] (str " → " (party/first-name a)))]
     [:p text]
     (when (seq follow) [:span.fu follow])
     (when (seq act) [:<> [:span] [:span [:span.tag.gm "Pays off in " (ui/act-label act-names act)]]])]))

(defn- question [{:keys [id text fit]}]
  (let [k (keyword id)
        {:keys [answers alt truth]} @(rf/subscribe [::sub/at [:s0]])
        {:keys [acts truths]} @(rf/subscribe [::sub/content])
        pcs @(rf/subscribe [::sub/party])
        list (filter #(seq (:text %)) (get answers k))
        reworded (get alt k)]
    ^{:key id}
    [:div.q {:class (when (seq list) "answered")}
     [:div.row1 [:span.qn (.toUpperCase id)]
      [:button.linkbtn {:type "button" :on-click #(ui/open! {:kind :question :id id})} (if (seq (get answers k)) "Edit" "Answer")]]
     [:span.qt (or (not-empty reworded) text)]
     (when (seq reworded) [:span.alt "Book wording: " text])
     (when (and fit (empty? reworded)) [:p.warnline fit])
     (doall (map-indexed (fn [i a] ^{:key i} [answer-line pcs acts a]) list))
     (when (and (get truths k) (seq (get truth k)))
       [:p.note [:span.tag.gm (get truths k)] " " (get truth k)])]))

(defn- question-set [{:keys [id name how]} questions]
  (let [qs (filter #(= id (:set %)) questions)
        answers @(rf/subscribe [::sub/at [:s0 :answers]])
        n (count (filter #(seq (get answers (keyword (:id %)))) qs))]
    ^{:key id}
    [:section.card.panel [:header [:h2 name " questions" [:small n " answered"]]]
     [:div.pbody [:p.note how] [:div.qset (doall (map question qs))]]]))

(defn- rolled-cards [pc questions]
  (let [{:keys [draws alt]} @(rf/subscribe [::sub/at [:s0]])
        card (fn [qid]
               (let [{:keys [text]} (first (filter #(= qid (:id %)) questions))]
                 ^{:key qid}
                 [:div.qcard [:span.qn "Rolled " (subs qid 1)] [:p (or (not-empty (get alt (keyword qid))) text)]
                  [:div.chips [:button.btn.primary
                               {:type "button"
                                :on-click (fn []
                                            (ui/op! doc/add-answer (keyword qid) (:id pc) nil)
                                            (ui/open! {:kind :question :id qid}))}
                               (party/first-name pc) " answers this"]]]))]
    [:<> [:div.cards (doall (map card (get draws (keyword (:id pc)))))]
     [:div.chips [:button.linkbtn {:type "button" :on-click #(rf/dispatch [::ev/roll (:id pc)])} "Put it back and roll again"]]]))

(defn- d20
  "Rolls for the player, or takes the number from a real die. Numbers that are taken can't be picked."
  [{:keys [id] :as pc} questions]
  (let [s0 @(rf/subscribe [::sub/at [:s0]])
        open (set (doc/deck {:s0 s0} questions id))]
    [:div.d20
     [:button.btn.primary {:type "button" :on-click #(rf/dispatch [::ev/roll id])} "Roll d20 for " (party/first-name pc)]
     [:span.note "or tap the number they rolled"]
     [:div.pips {:role "group" :aria-label "d20 result"}
      (doall (for [{qid :id} (filter #(= "personal" (:set %)) questions)
                   :let [n (subs qid 1)]]
               ^{:key qid}
               [:button {:type "button" :disabled (not (open qid)) :aria-pressed (= [qid] (get-in s0 [:draws (keyword id)]))
                         :on-click #(ui/op! doc/take-number questions id n)} n]))]]))

(defn- roll-panel [questions]
  (let [pcs @(rf/subscribe [::sub/party])
        {:keys [draws answers]} @(rf/subscribe [::sub/at [:s0]])
        who (:draw-for @(rf/subscribe [::sub/ui]))
        pc (first (filter #(= who (:id %)) pcs))]
    [:section.draw
     [:div.gmhd [ui/gm-tag] " " [:b "Roll for questions"]
      [:span.note " Go round the table. Each player rolls a d20 and answers that personal question."]]
     (if (empty? pcs)
       [no-players]
       [:div.chips
        (doall (for [{:keys [id color] :as p} pcs
                     :let [done (answered-by answers id) rolled (get draws (keyword id))]]
                 ^{:key id}
                 [:button.chip {:type "button" :aria-pressed (= id who) :style {"--pc" color}
                                :on-click #(ui/set-ui! :draw-for id)}
                  [:span.pcdot] (party/first-name p) (cond done " ✓" rolled " · rolled")]))])
     (when pc
       (if (get draws (keyword who)) [rolled-cards pc questions] [d20 pc questions]))]))

(defn- questions-tab []
  (let [{:keys [qsets questions]} @(rf/subscribe [::sub/content])]
    [:<> [roll-panel questions]
     (doall (map #(question-set % questions) (remove #(#{"ship" "bid"} (:id %)) qsets)))]))

;; ---------- Tone and safety ----------

(defn- dial [{:keys [id name low high]}]
  (let [path [:s0 :dials (keyword id)] current (ui/at path)]
    ^{:key id}
    [:div.dial [:b name]
     [:div.pips {:role "group" :aria-label name}
      (doall (for [v (range 1 6)]
               ^{:key v} [:button {:type "button" :aria-pressed (= current v) :on-click #(ui/edit! path v)} v]))]
     [:div.ends [:span low] [:span high]]]))

(def ^:private line-kinds
  {"line" ["Line: never in play" "bad"] "veil" ["Veil: fade to black" "warn"] "fine" ["Fine" ""]})

(defn- line-row [i {:keys [topic kind]}]
  (let [[label cls] (line-kinds kind)]
    ^{:key (str topic i)}
    [:div.lv [:span topic] [:span.tag {:class cls} label]
     [:button.linkbtn {:type "button" :aria-label (str "Remove " topic) :on-click #(ui/op! doc/remove-line i)} "Remove"]]))

(defn- sort-theme [theme]
  ^{:key theme}
  [:div.lv [:span theme]
   (doall (for [[kind label] [["line" "Line"] ["veil" "Veil"] ["fine" "Fine"]]]
            ^{:key kind} [:button.btn {:type "button" :on-click #(ui/op! doc/add-line theme kind)} label]))])

(defn- table-tab []
  (let [{:keys [dials themes run]} @(rf/subscribe [::sub/content])
        lv @(rf/subscribe [::sub/at [:s0 :lv]])
        sorted (set (map :topic lv))]
    [:div.grid {:style {:grid-template-columns "repeat(auto-fit,minmax(min(100%,420px),1fr))"}}
     [:section.card.panel [:header [:h2 "Tone dials" [:small "the table's number"]]]
      [:div.pbody (doall (map dial dials)) [:p.note "Alien weirdness starts at 2 and rises. " (:looksLike run)]]]
     [:section.card.panel [:header [:h2 "Lines and veils" [:small "X-card plus open door"]]]
      [:div.pbody
       (if (seq lv)
         (doall (map-indexed line-row lv))
         [:div.empty "Nothing sorted yet. Fill this in before anyone answers an unsettling question."])
       [ui/add-row "Add a topic of your own, as a line" "Line" #(ui/op! doc/add-line % "line")]
       [ui/add-row "Add a topic of your own, as a veil" "Veil" #(ui/op! doc/add-line % "veil")]
       (when-let [left (seq (remove sorted themes))]
         [:div [:span.label "Likely themes, not sorted yet"] (doall (map sort-theme left))])]]]))

;; ---------- The ship ----------

(defn- ship-tab []
  (let [{:keys [questions run]} @(rf/subscribe [::sub/content])
        {:keys [painted registry]} @(rf/subscribe [::sub/at [:s0 :ship]])]
    [:<>
     [:section.card.panel [:header [:h2 (or (not-empty painted) "The ship") [:small registry]]]
      [:div.pbody
       [:div.ship
        [ui/text-field [:s0 :ship :painted] "Name on the hull"]
        [ui/text-field [:s0 :ship :registry] "Name on the registry" "Should differ from the hull"]
        [ui/text-field [:s0 :ship :hull] "Hull" "Light freighter or salvage tug"]]
       [ui/text-area [:s0 :ship :quirks] "Quirks: one helpful, one troublesome"]
       (into [:ol.note] (for [s (:shipSteps run)] ^{:key s} [:li s]))
       [:p.note "Hull from Shattered Empire, attachments from the Expanded Player's Guide."]]]
     [:section.card.panel [:header [:h2 "The ship's unsettling questions"]]
      [:div.pbody [:div.qset (doall (map question (filter #(= "ship" (:set %)) questions)))]]]]))

;; ---------- The bid ----------

(defn- label-of
  "The label for id in [id label ...] options."
  [options id]
  (some (fn [[i label]] (when (= i id) label)) options))

(defn- choices [options] (cons ["" "Not chosen yet"] (map (juxt first second) options)))

(defn- stake-note
  "What the contract says about the crew's stake, and its fine print for the GM."
  [stakes stake]
  (when-let [[_ _ says catch] (first (filter #(= stake (first %)) stakes))]
    [:<> [:p.note says] [ui/gm-block [:p [:b "The fine print. "] catch]]]))

(defn- bid-fields [{:keys [backers stakes]}]
  (let [{:keys [backer stake]} (ui/at [:s0 :bid])
        expects (some (fn [[id _ e]] (when (= id backer) e)) backers)]
    [:<>
     [:div.ship
      [ui/select-field [:s0 :bid :backer] "Backer" (choices backers)]
      [ui/text-field [:s0 :bid :name] "Backer's name" "Who, exactly"]
      [ui/select-field [:s0 :bid :stake] "The crew's stake" (choices stakes)]]
     (when expects [:p.note [:b "They expect: "] expects])
     [stake-note stakes stake]
     [ui/text-area [:s0 :bid :promise] "What the crew promised to win the berth"]]))

(defn- bid-tab []
  (let [{:keys [questions run corps]} @(rf/subscribe [::sub/content])
        {:keys [how corpNote stakes] :as bid} (:bid run)]
    [:<>
     [:section.card.panel [:header [:h2 "The bid" [:small (label-of stakes (ui/at [:s0 :bid :stake]))]]]
      [:div.pbody [:p.note how] [bid-fields bid]
       [ui/gm-block
        [ui/select-field [:s0 :bid :corp] "Corp hiding behind the backer"
         (cons ["" "None, or not decided"] (map (juxt :id :short) corps))]
        [:p.note corpNote]]]]
     [:section.card.panel [:header [:h2 "Bid questions"]]
      [:div.pbody [:div.qset (doall (map question (filter #(= "bid" (:set %)) questions)))]]]]))

;; ---------- Debt ----------

(defn- debt-row [corps {:keys [id name debt owes why creditor debt-type] :as pc}]
  (let [corp (first (filter #(= creditor (:id %)) corps))]
    ^{:key id}
    [:div.debt-row
     [:div.head [ui/pc-chip pc] [:b.mono debt] (when (seq debt-type) [:span.tag debt-type])
      (cond corp [:span.sub (:short corp)]
            (seq owes) [:span.sub owes]
            :else [:span.warnline "No creditor yet"])]
     (when (seq why) [:p.note why])
     [ui/text-area [:s0 :collateral (keyword id)] (str name "'s collateral")]]))

(defn- debt-tab []
  (let [{:keys [corps run]} @(rf/subscribe [::sub/content])
        {:keys [how start types collateral]} (:debt run)
        pcs @(rf/subscribe [::sub/party])]
    [:<>
     [:section.card.panel [:header [:h2 "Who you owe" [:small "from each character's Obligation"]]]
      [:div.pbody [:p.note how]
       (if (empty? pcs)
         [no-players]
         [:<> [:div.debt-total "Crew Debt " [:span.mono (party/crew-debt pcs)]]
          [:p [:b "Ask each player: "] collateral]
          (doall (map #(debt-row corps %) pcs))])
       [:p.note start " The builder lets each player choose their value, so treat this as guidance."]]]
     [:section.card.panel [:header [:h2 "Debt types"]]
      [:div.pbody (into [:dl.kv] (mapcat (fn [[kind creditor due]] [[:dt kind] [:dd creditor ". " due "."]])) types)]]]))

;; ---------- Wrap-up ----------

(defn- wrap-tab []
  (let [{:keys [wrapup run]} @(rf/subscribe [::sub/content])
        ticks @(rf/subscribe [::sub/at [:s0 :wrap]])
        {:keys [painted]} @(rf/subscribe [::sub/at [:s0 :ship]])
        stake (label-of (get-in run [:bid :stakes]) (ui/at [:s0 :bid :stake]))
        pcs @(rf/subscribe [::sub/party])]
    [:section.card.panel [:header [:h2 "After Session 0"] [ui/gm-tag]]
     [:div.pbody
      [:p (:close run)]
      [:p.note (or (not-empty painted) "The ship has no name yet") ". Crew Debt " (party/crew-debt pcs) ". "
       (if stake (str "The crew's stake: " stake ".") "No stake chosen yet.")]
      (doall (map-indexed
              (fn [i text]
                (let [k (keyword (str i)) done? (get ticks k)]
                  ^{:key i}
                  [:label.check {:class (when done? "done")}
                   [:input {:type "checkbox" :checked (boolean done?) :on-change #(ui/edit! [:s0 :wrap k] (not done?))}]
                   [:span text]]))
              wrapup))]]))

;; ---------- The tab ----------

(def ^:private tabs
  [[:run "Run sheet"] [:questions "Questions"] [:table "Tone and safety"] [:ship "The ship"] [:bid "The bid"] [:debt "Debt"] [:wrap "Wrap-up"]])

(defn tab []
  (let [{:keys [s0-tab]} @(rf/subscribe [::sub/ui])
        done? @(rf/subscribe [::sub/at [:s0 :done]])]
    [:<>
     [ui/intro "Session 0" "A crew, a ship, a debt that binds them, and a voyage they helped invent."
      [:button.btn {:type "button" :class (when-not done? "primary")
                    :on-click #(ui/edit! [:s0 :done] (not done?))}
       (if done? "Session 0 played ✓" "Mark Session 0 played")]]
     [:div.chips
      (doall (for [[id label] tabs]
               ^{:key id} [:button.chip {:type "button" :aria-pressed (= id s0-tab) :on-click #(ui/set-ui! :s0-tab id)} label]))]
     (case s0-tab
       :questions [questions-tab]
       :table [table-tab]
       :ship [ship-tab]
       :bid [bid-tab]
       :debt [debt-tab]
       :wrap [wrap-tab]
       [run-sheet])]))
