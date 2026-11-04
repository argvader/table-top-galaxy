(ns ttg.app
  "Web entry point: the app shell (top bar + page nav) and routing between the pages (ttg.routes)."
  (:require [re-frame.core :as rf]
            [re-frame.db]
            [reagent.dom.client :as rdc]
            [ttg.auth :as auth]
            [ttg.builder.events :as builder-ev]
            [ttg.builder.views :as builder]
            [ttg.campaign.events :as campaign-ev]
            [ttg.campaign.subscriptions :as campaign-sub]
            [ttg.campaign.views :as campaign]
            [ttg.config :as config]
            [ttg.content :as content]
            [ttg.http]
            [ttg.library.events :as lib-ev]
            [ttg.library.views :as library]
            [ttg.chronicle.events :as chronicle-ev]
            [ttg.chronicle.views :as chronicle]
            [ttg.manager.events :as manager-ev]
            [ttg.manager.views :as manager]
            [ttg.roller.effects]
            [ttg.roller.events :as ev]
            [ttg.roller.subscriptions :as roller-sub]
            [ttg.roller.views :as roller]
            [ttg.routes :as routes]
            [ttg.sheet.events :as sheet-ev]
            [ttg.sheet.views :as sheet]
            [ttg.theme :as theme]
            [ttg.ui.dice :as ui]))

(defn- page-load
  "What each page loads as it opens."
  [{:keys [page id code campaign-id character-id kind slug]}]
  (case page
    :campaigns [::campaign-ev/load-home]
    :campaign [::campaign-ev/load-campaign id]
    :manager [::manager-ev/open id]
    :chronicle [::chronicle-ev/open id kind slug]
    :join [::campaign-ev/load-invite code]
    :builder (if character-id [::builder-ev/open-existing character-id] [::builder-ev/open-new campaign-id])
    :sheet [::sheet-ev/open id]
    nil))

(defn route-changed
  "Stores the route and opens the page. A sheet or manager being left saves first; the first visit to the library loads it, and
   the editor follows the route."
  [{:keys [db]} [_ route]]
  (let [db (assoc db :route route)
        library? (= :library (:page route))
        load-event (page-load route)]
    {:db db
     :fx [[:dispatch [::sheet-ev/flush-now]]
          [:dispatch [::manager-ev/flush-now]]
          [:dispatch [::ev/close-check]]
          (when load-event [:dispatch load-event])
          (when (and library? (= :idle (get-in db [:library :status]))) [:dispatch [::lib-ev/load]])
          (when library? [:dispatch [::lib-ev/route-changed]])]}))

(rf/reg-event-fx ::route-changed route-changed)

;; pushy reports the address on start and on every move; only a new route reopens the page.
(rf/reg-event-fx ::navigated
  (fn [{:keys [db]} [_ route]]
    (when (not= route (:route db)) {:fx [[:dispatch [::route-changed route]]]})))
(rf/reg-sub ::route (fn [db _] (:route db)))

;; :session mirrors ttg.auth for the views: nil when sign-in is off (bb dev) or signed out.
(defn- current-session []
  (when (auth/signed-in?)
    (let [claims (auth/claims)] {:id (:sub claims) :name (some claims [:name :email])})))

(rf/reg-event-fx ::signed-in
  (fn [{:keys [db]} _]
    (let [route (:route db)]
      {:db (assoc db :session (current-session))
       :fx [[:dispatch [::campaign-ev/load-roles]]
            (when (= :library (:page route)) [:dispatch [::lib-ev/load]])
            (when-let [load-event (page-load route)] [:dispatch load-event])]})))

(rf/reg-sub ::session (fn [db _] (:session db)))

(defn- account []
  (when (auth/enabled?)
    (if-let [{:keys [name]} @(rf/subscribe [::session])]
      [:div.account [:span.who name]
       [:button.linkbtn {:type "button" :on-click auth/sign-out!} "Sign out"]]
      [:button.btn.primary {:type "button" :on-click auth/sign-in!} "Sign in"])))

(def ^:private pages
  [{:page :campaigns :label "Your table"} {:page :roller :label "Dice roller"} {:page :library :label "Library" :gm-only? true}])

;; Library is for GMs. With sign-in off (bb dev) there are no roles to go on, so it stays.
(defn- top-bar [current]
  (let [gm? (or (not (auth/enabled?)) @(rf/subscribe [::campaign-sub/gm?]))]
    [:header.top
     [:div.brand "Table Top Galaxy"]
     [:nav.pages {:aria-label "Pages"}
      (for [{:keys [page label]} (remove #(and (:gm-only? %) (not gm?)) pages)]
        ^{:key page} [:a {:href (routes/href {:page page}) :aria-current (when (= page current) "page")} label])]
     [:div.spacer]
     [account]]))

(defn- shell []
  (let [{:keys [page]} @(rf/subscribe [::route])]
    [:<>
     [ui/symbol-defs]
     [:style (theme/css)]
     [top-bar page]
     (case page
       :library [library/page]
       :campaigns [campaign/home]
       :campaign [campaign/campaign-page]
       :manager [manager/page]
       :chronicle [chronicle/page]
       :join [campaign/join-page]
       :builder [builder/page]
       :sheet [sheet/page]
       [roller/page])]))

(defonce ^:private root (delay (rdc/create-root (js/document.getElementById "app"))))

(defn mount! []
  (rf/clear-subscription-cache!)
  (rdc/render @root [shell]))

(defn- typing? [] (re-matches #"INPUT|TEXTAREA|SELECT" (.-tagName js/document.activeElement)))

;; R rolls, as in the prototype, unless a modifier is held, focus is in a text field, or nothing's open to roll.
(defn- roll-key? [e]
  (and (= "r" (.toLowerCase (.-key e))) (not (or (.-metaKey e) (.-ctrlKey e) (.-altKey e))) (not (typing?))
       (or (= :roller (:page @(rf/subscribe [::route]))) @(rf/subscribe [::roller-sub/drawer-rolls?]))))

(defn- on-key [e]
  (cond (= "Escape" (.-key e)) (do (rf/dispatch [::ev/dismiss-check]) (rf/dispatch [::builder-ev/dismiss-info])
                                     (rf/dispatch [::sheet-ev/dismiss-info]))
        (roll-key? e) (do (.preventDefault e) (rf/dispatch [::ev/roll]))))

(defonce ^:private keys-bound (delay (.addEventListener js/document "keydown" on-key)))

(defn init []
  ;; The campaign manager is a separate page on this origin; this tells it where the API is.
  (.setItem js/localStorage "ttg.api-url" config/api-url)
  (rf/dispatch-sync [::ev/initialize])
  (rf/dispatch-sync [::lib-ev/initialize])
  (rf/dispatch-sync [::campaign-ev/initialize])
  (rf/dispatch-sync [::content/initialize])
  (rf/dispatch-sync [::builder-ev/initialize])
  (rf/dispatch-sync [::sheet-ev/initialize])
  (rf/dispatch-sync [::manager-ev/initialize])
  (rf/dispatch-sync [::chronicle-ev/initialize])
  (swap! re-frame.db/app-db assoc :session (current-session))
  ;; Back from the hosted sign-in page: complete! puts the page's address back at once (so the route below is the
  ;; one the user left from), then trades the code for tokens and lets the open page load its data.
  (-> (auth/complete!)
      (.then #(when % (rf/dispatch [::signed-in])))
      (.catch #(js/console.error "Sign-in didn't complete" %)))
  ;; Links shared before clean URLs were hashes (#/join/<code>): they open the page they meant.
  (when-let [path (routes/legacy-path (.-hash js/location))] (js/history.replaceState nil "" path))
  (rf/dispatch-sync [::route-changed (routes/parse (.-pathname js/location))])
  (rf/dispatch [::campaign-ev/load-roles])
  @keys-bound
  (routes/start!)
  (mount!))
