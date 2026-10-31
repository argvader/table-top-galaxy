(ns ttg.routes
  "Hash routes (#/library/weapons/knife). Hashes work under any base path, so the app runs unchanged at / in dev
   and under /roller/ on the prototype site, with no server rewrites. :page picks the view."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]))

(defn parse
  "\"#/library/weapons/knife\" -> {:page :library :type :weapons :id :knife}. Campaign and character ids stay strings.
   With no hash you land on your campaigns and characters; unknown hashes go to the roller."
  [hash]
  (let [[page a b] (remove str/blank? (str/split (str/replace (or hash "") #"^#/?" "") #"/"))]
    (case page
      nil {:page :campaigns}
      "library" (cond-> {:page :library}
                  a (assoc :type (keyword a))
                  b (assoc :id (keyword b)))
      "campaigns" (cond (= "manager" b) {:page :manager :id a}
                        a {:page :campaign :id a}
                        :else {:page :campaigns})
      "join" {:page :join :code a}
      "build" (cond-> {:page :builder} a (assoc :campaign-id a))
      "edit" {:page :builder :character-id a}
      "characters" {:page :sheet :id a}
      {:page :roller})))

(defn href
  "The hash for a route map: the inverse of parse."
  [{:keys [page type id code campaign-id character-id]}]
  (case page
    :library (str "#/library" (when type (str "/" (name type))) (when id (str "/" (name id))))
    :campaigns "#/campaigns"
    :campaign (str "#/campaigns/" id)
    :manager (str "#/campaigns/" id "/manager")
    :join (str "#/join/" code)
    :builder (cond character-id (str "#/edit/" character-id)
                   campaign-id (str "#/build/" campaign-id)
                   :else "#/build")
    :sheet (str "#/characters/" id)
    "#/roller"))

(rf/reg-fx ::navigate (fn [route] (set! (.-hash js/location) (href route))))

(defonce ^:private listening
  (delay (.addEventListener js/window "hashchange" #(rf/dispatch [:ttg.app/route-changed (parse (.-hash js/location))]))))

(defn start! [] @listening)
