(ns ttg.campaign.views
  "Your campaigns and characters, one campaign's members and roster, and the page an invite link opens."
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [ttg.auth :as auth]
            [ttg.campaign.events :as ev]
            [ttg.campaign.subscriptions :as subs]
            [ttg.routes :as routes]
            [ttg.ui.portrait :as portrait]))

(defn- problem [error]
  (when error
    [:p.err {:role "alert"} error
     (when (and (auth/enabled?) (not (auth/signed-in?)))
       [:<> " " [:button.linkbtn {:type "button" :on-click auth/sign-in!} "Sign in"]])]))

(defn- new-campaign []
  (let [text (r/atom "")]
    (fn []
      [:form.inline {:on-submit (fn [e] (.preventDefault e) (rf/dispatch [::ev/create @text]))}
       [:label.sr {:for "new-campaign"} "Campaign name"]
       [:input {:id "new-campaign" :type "text" :placeholder "Name a new campaign" :auto-complete "off"
                :value @text :on-change #(reset! text (.. % -target -value))}]
       [:button.btn.primary {:type "submit"} "Create campaign"]])))

(defn- character-row [{:keys [id name] :as character}]
  ^{:key id}
  [:li [:a {:href (routes/href {:page :sheet :id id})} [portrait/face character] [:span.grow name]]])

(defn- campaign-row [{:keys [id name role]}]
  ^{:key id}
  [:li [:a {:href (routes/href {:page :campaign :id id})}
        [:span.grow name] [:span.label (if (= "gm" role) "GM" "Player")]]])

(defn home []
  (let [{:keys [campaigns characters error status]} @(rf/subscribe [::subs/table])]
    [:main.table-page
     [:h1 "Your table"]
     [problem error]
     [:section.box
      [:h2 "Campaigns"]
      (when (seq campaigns) (into [:ul.rowlist] (map campaign-row) campaigns))
      (when (and (= :ready status) (empty? campaigns))
        [:p.note "No campaigns yet. Start one, or open the invite link your GM sent you."])
      [new-campaign]]
     [:section.box
      [:h2 "Characters"]
      (when (seq characters) (into [:ul.rowlist] (map character-row) characters))
      (when (and (= :ready status) (empty? characters))
        [:p.note "No characters yet. Open a campaign to build one for it."])]]))

(defn- invite-url [code]
  (str js/location.origin (routes/href {:page :join :code code})))

(defn- invites [code copied?]
  [:section.box
   [:h2 "Invite players"]
   (if code
     (let [url (invite-url code)]
       [:<>
        [:code.link url]
        [:p.inline
         [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/copy-invite url])} (if copied? "Copied" "Copy link")]
         [:span.note "Works for 7 days. Each player needs an account too."]]])
     [:button.btn.primary {:type "button" :on-click #(rf/dispatch [::ev/make-invite])} "Create invite link"])])

(defn- member-row [gm? {:keys [user-id name role]}]
  ^{:key user-id}
  [:li.row [:span.grow name] [:span.label (if (= "gm" role) "GM" "Player")]
   (when (and gm? (not= "gm" role))
     [:button.linkbtn {:type "button" :on-click #(rf/dispatch [::ev/remove-member user-id])} "Remove"])])

(defn- add-existing [characters]
  (when-let [free (seq (remove :campaign-id characters))]
    [:div
     [:span.label "Bring a character"]
     (into [:ul.rowlist]
           (map (fn [{:keys [id name] :as c}]
                  ^{:key id}
                  [:li.row [portrait/face c] [:span.grow name]
                   [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/add-character id])} "Add"]]))
           free)]))

(defn campaign-page []
  (let [{:keys [current characters invite-code copied? error]} @(rf/subscribe [::subs/table])
        {:keys [campaign role members] roster :characters} current
        gm? (= "gm" role)]
    [:main.table-page
     [:a.linkbtn {:href (routes/href {:page :campaigns})} "Your table"]
     [problem error]
     (when current
       [:<>
        [:h1 (:name campaign)]
        (when gm? [invites invite-code copied?])
        (when gm?
          [:p [:a.btn {:href (routes/href {:page :manager :id (:id campaign)})} "Campaign manager"]])
        [:p [:a.btn {:href (routes/href {:page :chronicle :id (:id campaign)})} "Chronicle"]]
        [:section.box
         [:h2 "Characters"]
         (if (seq roster)
           (into [:ul.rowlist] (map character-row) roster)
           [:p.note "Nobody has brought a character yet."])
         [:p [:a.btn.primary {:href (routes/href {:page :builder :campaign-id (:id campaign)})} "Build a character"]]
         [add-existing characters]]
        [:section.box
         [:h2 "Who's here"]
         (into [:ul.rowlist] (map #(member-row gm? %)) members)]])]))

(defn join-page []
  (let [{:keys [join error]} @(rf/subscribe [::subs/table])
        out? (and (auth/enabled?) (not (auth/signed-in?)))]
    [:main.table-page
     [:h1 "Join a campaign"]
     [problem (when-not out? error)]
     (cond out? [:section.box
                 [:p "Sign in to accept this invite."]
                 [:button.btn.primary {:type "button" :on-click auth/sign-in!} "Sign in"]]
           (:campaign-name join) [:section.box
                                  [:p "You've been invited to " [:b (:campaign-name join)] "."]
                                  [:button.btn.primary {:type "button" :on-click #(rf/dispatch [::ev/join])} "Join"]])]))
