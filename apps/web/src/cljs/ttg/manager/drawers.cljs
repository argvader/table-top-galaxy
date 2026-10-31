(ns ttg.manager.drawers
  "The side drawer: a question, a person, a secret or a session."
  (:require [re-frame.core :as rf]
            [ttg.manager.doc :as doc]
            [ttg.manager.party :as party]
            [ttg.manager.subscriptions :as sub]
            [ttg.manager.ui :as ui]))

(defn- index-of [coll pred] (first (keep-indexed #(when (pred %2) %1) coll)))

;; ---------- Question ----------

(defn- pc-options [pcs none]
  (concat (when none [["" none]]) (for [{:keys [id name]} pcs] [id name])))

(defn- answer-form [q k i {:keys [pc]}]
  (let [path [:s0 :answers k i]
        pcs @(rf/subscribe [::sub/party])
        acts (:acts @(rf/subscribe [::sub/content]))
        color (:color (first (filter #(= pc (:id %)) pcs)))]
    ^{:key i}
    [:div.item {:style {"--pc" color :border-left "4px solid var(--pc)"}}
     [:div.ship [ui/select-field (conj path :pc) "Answered by" (pc-options pcs nil)]
      (when (= "crew" (:set q)) [ui/select-field (conj path :about) "About" (pc-options pcs "—")])]
     [ui/text-area (conj path :text) "Answer · canon once said" "A few sentences. Leave gaps on purpose."]
     (when (= "personal" (:set q))
       [:<> [ui/text-field (conj path :follow) "Follow-up (one, may be declined)"]
        [ui/gm-block [ui/select-field (conj path :act) "Pays off in" (ui/act-options acts "Not tied to an act")]]])
     [:div [:button.linkbtn {:type "button" :on-click #(ui/op! doc/remove-answer k i)} "Remove this answer"]]]))

(defn- next-to-answer
  "Who hasn't answered a crew question yet, or else the first player."
  [{:keys [set]} pcs answers]
  (let [done (into #{} (comp (filter (fn [[qid _]] (= "c" (subs (name qid) 0 1)))) (mapcat val) (map :pc)) answers)]
    (or (when (= "crew" set) (first (remove #(done (:id %)) pcs))) (first pcs))))

(defn- question-drawer [{:keys [id]}]
  (let [{:keys [questions qsets truths]} @(rf/subscribe [::sub/content])
        {:keys [answers alt]} @(rf/subscribe [::sub/at [:s0]])
        pcs @(rf/subscribe [::sub/party])
        q (first (filter #(= id (:id %)) questions))
        k (keyword id)
        pc (next-to-answer q pcs answers)]
    {:title (str (:name (first (filter #(= (:set q) (:id %)) qsets))) " " (.toUpperCase id))
     :meta (:how (first (filter #(= (:set q) (:id %)) qsets)))
     :body [:<>
            [:p {:style {:margin 0 :font-weight 700}} (or (not-empty (get alt k)) (:text q))]
            (when (:fit q) [:p.warnline (:fit q)])
            [ui/text-area [:s0 :alt k] "Concord wording (optional)" (:text q)]
            (if-let [list (seq (get answers k))]
              (doall (map-indexed #(answer-form q k %1 %2) list))
              [:p.note "No answers yet."])
            (when pc
              [:div [:button.btn {:type "button"
                                  :on-click #(ui/op! doc/add-answer k (:id pc)
                                                     (when (= "crew" (:set q)) (party/left-of pcs (:id pc))))}
                     "Add an answer"]])
            (when-let [label (get truths k)]
              [ui/gm-block [ui/text-area [:s0 :truth k] label]])]}))

;; ---------- Person ----------

(defn- npc-drawer [{:keys [id]}]
  (let [{:keys [npcs ledger]} @(rf/subscribe [::sub/doc])
        {:keys [acts]} @(rf/subscribe [::sub/content])
        i (index-of npcs #(= id (:id %)))
        {:keys [name role arc] :as n} (npcs i)
        p [:npcs i]]
    {:title name
     :meta role
     :body [:<>
            [ui/select-field (conj p :met) "Players have met them" (ui/session-options "Not yet") {:number? true}]
            [ui/text-area (conj p :pub) "What players know"]
            [ui/gm-block
             [ui/text-area (conj p :gm) "Secret"]
             (when arc
               (into [:dl.kv] (mapcat (fn [{:keys [id name]}]
                                        (when-let [text (get arc (keyword id))] [[:dt name] [:dd text]])))
                     acts))
             (doall (for [{lid :id :keys [title state]} (filter #(= id (:owner %)) ledger)]
                      ^{:key lid}
                      [:div.seed [ui/state-tag state]
                       [:button.linkbtn {:type "button" :on-click #(ui/open! {:kind :secret :id lid})} title]]))
             [ui/text-area (conj p :notes) "Notes" "Voice, mannerisms, what they want right now"]]
            (when (:met n) [:p.note "Met " (ui/session-label (:met n))])]}))

;; ---------- Secret ----------

(defn- clue-row [id k {:keys [t s]}]
  (let [session (ui/at [:session])]
    ^{:key k}
    [:div.clue {:class (when (nil? s) "unplanted")} [:p t]
     [:button.chip {:type "button" :on-click #(ui/op! doc/plant-clue id k)}
      (if (some? s) (str (ui/session-label s) " ✓") (if (pos? session) (str "Plant in S" session) "Plant now"))]]))

(defn state-buttons [{:keys [id title state]}]
  [:div.seg {:role "group" :aria-label (str "Reveal state of " title)}
   (doall (for [st ["hidden" "hinted" "revealed"]]
            ^{:key st}
            [:button {:type "button" :class st :aria-pressed (= st state) :on-click #(ui/op! doc/set-state id st)}
             (.toUpperCase (subs st 0 1)) (subs st 1)]))])

(defn- secret-drawer [{:keys [id]}]
  (let [{:keys [ledger]} @(rf/subscribe [::sub/doc])
        {:keys [acts]} @(rf/subscribe [::sub/content])
        i (index-of ledger #(= id (:id %)))
        {:keys [title act clues] :as l} (ledger i)
        p [:ledger i]]
    {:title title
     :meta (str "Breaks in " (ui/act-label acts act))
     :body [:<>
            [state-buttons l]
            [ui/text-area (conj p :truth) "The truth"]
            [:div [:h4 "Clues"]
             (doall (map-indexed #(clue-row id %1 %2) clues))
             [ui/add-row "Something the crew could notice" "Add clue" #(ui/op! doc/add-clue id %)]]
            [ui/text-area (conj p :reveal) "When it's revealed, players read" "In the crew's words, not the GM's"]
            [ui/select-field (conj p :revealedIn) "Revealed in" (ui/session-options "Not revealed") {:number? true}]
            [ui/select-field (conj p :act) "Planned to break in" (ui/act-options acts "No act")]]}))

;; ---------- Session ----------

(defn- session-actions [{:keys [n status]}]
  [:div.chips
   (when (= "plan" status)
     [:<> [:button.btn.primary {:type "button" :on-click #(ui/op! doc/start-session n)} "Start this session"]
      [:button.btn.danger {:type "button" :on-click (fn [] (ui/op! doc/delete-session n) (ui/open! nil))} "Delete"]])
   (when (= "now" status)
     [:button.btn {:type "button" :on-click #(ui/op! doc/end-session n)} "Mark played"])])

(defn- session-drawer [{:keys [n]}]
  (let [{:keys [sessions]} @(rf/subscribe [::sub/doc])
        {:keys [acts]} @(rf/subscribe [::sub/content])
        i (index-of sessions #(= n (:n %)))
        {:keys [title act status choices] :as s} (sessions i)
        p [:sessions i]]
    {:title (str "Session " n (when (seq title) (str ": " title)))
     :meta (str (ui/act-label acts act) " · " ({"plan" "Planned" "now" "Tonight" "played" "Played"} status))
     :body [:<>
            [ui/text-field (conj p :title) "Title"]
            [ui/text-area (conj p :recap) "Recap" "What happened, in the crew's words"]
            [:div [:h4 "Choices that carry forward"]
             [:div.choices (doall (map-indexed
                                   (fn [k c] ^{:key k}
                                     [:span.choice c " " [:button.linkbtn {:type "button" :aria-label "Remove" :on-click #(ui/op! doc/remove-choice i k)} "×"]])
                                   choices))]
             [ui/add-row "The crew let them go…" "Add" #(ui/op! doc/add-choice i %)]]
            [ui/gm-block [ui/text-area (conj p :prep) "Prep notes"]
             [ui/select-field (conj p :act) "Act" (ui/act-options acts "No act")]]
            [session-actions s]]}))

;; ---------- Frame ----------

(defn drawer []
  (when-let [{:keys [kind] :as open} (:drawer @(rf/subscribe [::sub/ui]))]
    (let [{:keys [title meta body]} ((case kind :question question-drawer :npc npc-drawer :secret secret-drawer :session session-drawer) open)
          close #(ui/open! nil)]
      [:<>
       [:div.scrim {:on-click close}]
       [:aside.drawer {:aria-modal "true" :role "dialog" :aria-labelledby "d-title"
                       :on-key-down #(when (= "Escape" (.-key %)) (close))}
        [:div.grab {:aria-hidden true}]
        [:header [:div [:h2#d-title title] [:div.meta meta]]
         [:button.x {:type "button" :aria-label "Close" :on-click close} "×"]]
        [:div.body body]]])))
