(ns ttg.api.chronicle
  "The Chronicle: what every member of a campaign can read. bb chronicle:publish writes it through the Data API, so
   nothing here changes it. Strangers get the same 404 as for the campaign itself."
  (:require [ttg.api.async :refer [go-try <?]]
            [ttg.api.campaigns :as campaigns]
            [ttg.api.db :as db]
            [ttg.api.service :refer [reject!]]))

(def ^:private played-on "to_char(played_on, 'YYYY-MM-DD') AS played_on")

(defn- ->entry [{:keys [kind slug title body number played_on image_key]}]
  (cond-> {:kind kind :slug slug :title title}
    body (assoc :body body)
    (some? number) (assoc :number number)
    played_on (assoc :played-on played_on)
    image_key (assoc :image-key image_key)))

(defn index
  "Every page without its body: sessions newest first, then the wiki by title."
  [d user-id id]
  (go-try
    (<? (campaigns/role! d user-id id))
    {:entries (mapv ->entry
                    (<? (db/query d (str "SELECT kind, slug, title, number, image_key, " played-on "
                                          FROM chronicle_entries WHERE campaign_id = CAST(:id AS uuid)
                                          ORDER BY number DESC NULLS LAST, title")
                                  {:id id})))}))

(defn- portrait
  "The app's own portrait for a PC page, found by the character's name."
  [d id title]
  (go-try
    (when-let [{:keys [name portrait_key]}
               (<? (db/one d "SELECT name, portrait_key FROM characters
                              WHERE campaign_id = CAST(:id AS uuid) AND name = :title LIMIT 1"
                           {:id id :title title}))]
      (cond-> {:name name} portrait_key (assoc :portrait-key portrait_key)))))

(defn entry [d user-id id kind slug]
  (go-try
    (<? (campaigns/role! d user-id id))
    (let [row (or (<? (db/one d (str "SELECT kind, slug, title, body, number, image_key, " played-on "
                                      FROM chronicle_entries
                                      WHERE campaign_id = CAST(:id AS uuid) AND kind = :kind AND slug = :slug")
                              {:id id :kind kind :slug slug}))
                  (reject! 404 "That page isn't in the Chronicle" {:kind kind :slug slug}))
          found (->entry row)]
      {:entry (cond-> found
                (= "pc" kind) (assoc :character (<? (portrait d id (:title found)))))})))
