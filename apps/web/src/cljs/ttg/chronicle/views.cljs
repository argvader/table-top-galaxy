(ns ttg.chronicle.views
  "The Chronicle, read inside the campaign: sessions newest first, the wiki by kind, and one page at a time."
  (:require [re-frame.core :as rf]
            [ttg.chronicle.markdown :as markdown]
            [ttg.chronicle.subscriptions :as subs]
            [ttg.routes :as routes]
            [ttg.ui.portrait :as portrait]))

(def ^:private wiki-sections [["pc" "Player characters"] ["npc" "NPCs"] ["faction" "Factions"] ["location" "Locations"]])

(defn- session-label [{:keys [number played-on]}]
  (str (if (zero? number) "Session zero" (str "Session " number)) (when played-on (str " · " played-on))))

(defn- page-link [id {:keys [kind slug title] :as entry}]
  ^{:key (str kind "/" slug)}
  [:li [:a {:href (routes/href {:page :chronicle :id id :kind kind :slug slug})}
        [:span.grow title]
        (when (some? (:number entry)) [:span.chronicle-sub (session-label entry)])]])

(defn- section [id title entries]
  (when (seq entries)
    [:section.box [:h2 title] (into [:ul.rowlist] (map #(page-link id %)) entries)]))

(defn- index [id entries]
  (let [by-kind (group-by :kind entries)]
    [:<>
     [:a.linkbtn {:href (routes/href {:page :campaign :id id})} "Back to the campaign"]
     [:h1 "Chronicle"]
     (when (empty? entries) [:p.note "Nothing has been published yet."])
     [section id "Sessions" (by-kind "session")]
     (doall (for [[kind title] wiki-sections]
              ^{:key kind} [section id title (by-kind kind)]))]))

(defn- entry-page [id {:keys [kind body number image-key character] :as entry}]
  [:<>
   [:a.linkbtn {:href (routes/href {:page :chronicle :id id})} "Chronicle"]
   (when image-key [:img.chronicle-card {:src (portrait/src image-key) :alt ""}])
   (when character [portrait/face character])
   (when (some? number) [:div.chronicle-sub (session-label entry)])
   [:article.box [markdown/render {:campaign-id id :kind kind} body]]])

(defn page []
  (let [{:keys [status campaign-id entries entry error]} @(rf/subscribe [::subs/chronicle])]
    [:main.table-page
     (case status
       :error [:p.banner {:role "alert"} error]
       :ready (if entry [entry-page campaign-id entry] [index campaign-id entries])
       [:p.note "Loading…"])]))
