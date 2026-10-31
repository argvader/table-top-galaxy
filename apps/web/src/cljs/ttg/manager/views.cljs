(ns ttg.manager.views
  "The campaign manager, for the campaign's GM: Session 0 first, then the cast, factions, ledger and arc."
  (:require [re-frame.core :as rf]
            [ttg.manager.drawers :as drawers]
            [ttg.manager.events :as ev]
            [ttg.manager.session0 :as session0]
            [ttg.manager.subscriptions :as sub]
            [ttg.manager.ui :as ui]
            [ttg.manager.world :as world]
            [ttg.routes :as routes]))

(defn- save-problem []
  (let [state @(rf/subscribe [::sub/save-state])]
    (case state
      :signin [:div.mgr-problem {:role "alert"} "Not saved to the server. Your work is safe in this browser. Sign in again, and it sends."]
      :failed [:div.mgr-problem {:role "alert"} "Not saved to the server. Your work is safe in this browser. "
               [:button.linkbtn {:type "button" :on-click #(rf/dispatch [::ev/retry])} "Try again"]]
      nil)))

(defn- header []
  (let [{:keys [s0 session]} @(rf/subscribe [::sub/doc])
        {:keys [campaign-id]} @(rf/subscribe [::sub/manager])
        state @(rf/subscribe [::sub/save-state])]
    [:div.mgr-head
     [:h1 "Campaign manager"]
     [:span.sub (cond (pos? session) (str "Session " session) (:done s0) "Session 1 next" :else "Session 0 next")]
     [:a.linkbtn {:href (routes/href {:page :campaign :id campaign-id})} "Back to the campaign"]
     [:span.mgr-save {:role "status"} (case state :saving "Saving…" :saved "Saved" "Not saved")]]))

(defn- tabs []
  (let [{:keys [tab]} @(rf/subscribe [::sub/ui])
        {:keys [npcs ledger s0]} @(rf/subscribe [::sub/doc])
        revealed (count (filter #(= "revealed" (:state %)) ledger))]
    [:nav.tabs {:role "tablist" :aria-label "Campaign sections"}
     (doall (for [[id label badge] [[:s0 "Session 0" (when-not (:done s0) "next")] [:cast "Cast" (count npcs)] [:factions "Factions" nil]
                                    [:ledger "Ledger" (str revealed "/" (count ledger))] [:arc "Arc" nil]]]
              ^{:key id}
              [:button {:type "button" :role "tab" :aria-selected (= id tab) :on-click #(ui/set-ui! :tab id)}
               label (when badge [:span.badge badge])]))]))

(defn- ready []
  (let [{:keys [tab]} @(rf/subscribe [::sub/ui])]
    [:<>
     [header] [save-problem] [tabs]
     [:div#main
      (case tab
        :cast [world/cast-tab]
        :factions [world/factions-tab]
        :ledger [world/ledger-tab]
        :arc [world/arc-tab]
        [session0/tab])]
     [drawers/drawer]]))

(defn page []
  (let [{:keys [status error campaign-id]} @(rf/subscribe [::sub/manager])]
    [:main.page-manager
     (case status
       :ready [ready]
       :no-prep [:section.box [:h1 "Campaign manager"]
                 [:p "This campaign's written prep isn't loaded yet. Load it once with:"]
                 [:pre (str "bb manager:seed [local|dev|prod] " campaign-id)]]
       :failed [:section.box [:h1 "Campaign manager"] [:p (if error error "That didn't load.")]
                [:p.note "Only the campaign's GM can open this page."]]
       [:p.note "Loading…"])]))
