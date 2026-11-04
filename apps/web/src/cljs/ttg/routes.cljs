(ns ttg.routes
  "Clean URLs (/library/weapons/knife) through pushy and the History API. The app is served at the site root: bb dev's
   shadow-cljs server and CloudFront (infra/web.yml) both answer any path without a file extension with index.html.
   Links the app used to share were hashes (/roller/index.html#/join/<code>); legacy-path turns one into its path.
   :page picks the view."
  (:require [clojure.string :as str]
            [pushy.core :as pushy]
            [re-frame.core :as rf]))

(defn parse
  "\"/library/weapons/knife\" -> {:page :library :type :weapons :id :knife}. Campaign and character ids stay strings.
   At / you land on your campaigns and characters; unknown paths go to the roller. A query string is ignored."
  [path]
  (let [[page a b c d] (remove str/blank? (str/split (str/replace (or path "") #"\?.*$" "") #"/"))]
    (case page
      nil {:page :campaigns}
      "library" (cond-> {:page :library}
                  a (assoc :type (keyword a))
                  b (assoc :id (keyword b)))
      "campaigns" (cond (= "manager" b) {:page :manager :id a}
                        (= "chronicle" b) (cond-> {:page :chronicle :id a} (and c d) (assoc :kind c :slug d))
                        a {:page :campaign :id a}
                        :else {:page :campaigns})
      "join" {:page :join :code a}
      "build" (cond-> {:page :builder} a (assoc :campaign-id a))
      "edit" {:page :builder :character-id a}
      "characters" {:page :sheet :id a}
      {:page :roller})))

(defn href
  "The path for a route map: the inverse of parse."
  [{:keys [page type id code campaign-id character-id kind slug]}]
  (case page
    :library (str "/library" (when type (str "/" (name type))) (when id (str "/" (name id))))
    :campaigns "/campaigns"
    :campaign (str "/campaigns/" id)
    :manager (str "/campaigns/" id "/manager")
    :chronicle (str "/campaigns/" id "/chronicle" (when slug (str "/" kind "/" slug)))
    :join (str "/join/" code)
    :builder (cond character-id (str "/edit/" character-id)
                   campaign-id (str "/build/" campaign-id)
                   :else "/build")
    :sheet (str "/characters/" id)
    "/roller"))

(defn legacy-path
  "The path an old hash link meant (\"#/join/ab12\" -> \"/join/ab12\"), or nil for any other hash."
  [hash]
  (when-let [[_ path] (re-matches #"#(/.*)" (or hash ""))]
    path))

(defn- app-path?
  "Paths the app answers: anything without a file extension. Files (/portraits/..., the prototypes) load as usual."
  [path]
  (not (re-find #"\.[^/]*$" (str/replace path #"\?.*$" ""))))

(defonce ^:private history
  (delay (pushy/pushy #(rf/dispatch [:ttg.app/navigated %]) #(when (app-path? %) (parse %)))))

(rf/reg-fx ::navigate (fn [route] (pushy/set-token! @history (href route))))

(defn start!
  "Follows the address bar from here on, and turns clicks on the app's own links into navigation without a reload."
  []
  (pushy/start! @history))
