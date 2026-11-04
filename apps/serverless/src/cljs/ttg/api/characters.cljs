(ns ttg.api.characters
  "Characters. The owner reads and writes their own; the GM of the campaign a character is in can read and write it
   too, so they can fix or override a sheet. Moving a character between campaigns and deleting it stay the owner's.
   The character's body is a jsonb document; id, owner, campaign, portrait and version are columns and only the server
   sets them. Saves carry the :version they started from, so two open sheets can't silently overwrite each other."
  (:require [ttg.api.async :refer [go-try <?]]
            [ttg.api.db :as db]
            [ttg.api.images :as images]
            [ttg.api.keys :as k]
            [ttg.api.service :refer [reject!]]))

(def ^:private columns
  (str "id, owner_id, campaign_id, name, theme, portrait_key, version, "
       (db/json-col "data") ", " (db/ts-col "updated_at")))

(def ^:private server-keys [:id :owner :campaign-id :name :theme :portrait-key :version :updated-at :access])

(def max-portrait-bytes (* 256 1024))

(defn- row->character [{:keys [id owner_id campaign_id name theme portrait_key version data updated_at]}]
  (cond-> (assoc (db/parse-json data) :id id :owner owner_id :name name :version version :updated-at updated_at)
    campaign_id (assoc :campaign-id campaign_id)
    theme (assoc :theme theme)
    portrait_key (assoc :portrait-key portrait_key)))

(defn- body [character] (db/->json (apply dissoc character server-keys)))

(defn- load! [d id]
  (go-try
    (or (<? (db/one d (str "SELECT " columns " FROM characters WHERE id = CAST(:id AS uuid)") {:id id}))
        (reject! 404 "No such character" {:id id}))))

(defn- own! [row user]
  (when-not (= user (:owner_id row)) (reject! 403 "That character isn't yours" {}))
  row)

(defn role-in
  "The user's role in the campaign, or nil."
  [d user campaign-id]
  (go-try
    (when campaign-id
      (:role (<? (db/one d "SELECT role FROM campaign_members WHERE campaign_id = CAST(:c AS uuid) AND user_id = :u"
                         {:c campaign-id :u user}))))))

(defn- access!
  "\"owner\" or \"gm\" for someone who may write the character, otherwise a 403."
  [d row user]
  (go-try
    (cond (= user (:owner_id row)) "owner"
          (= "gm" (<? (role-in d user (:campaign_id row)))) "gm"
          :else (reject! 403 "That character isn't yours" {}))))

(defn mine [d user]
  (go-try
    {:characters (mapv (fn [{:keys [id name campaign_id portrait_key theme updated_at]}]
                         (cond-> {:id id :name name :updated-at updated_at}
                           campaign_id (assoc :campaign-id campaign_id)
                           portrait_key (assoc :portrait-key portrait_key)
                           theme (assoc :theme theme)))
                       (<? (db/query d (str "SELECT id, name, campaign_id, portrait_key, theme, " (db/ts-col "updated_at")
                                            " FROM characters WHERE owner_id = :u ORDER BY name")
                                     {:u user})))}))

(defn create! [d user character]
  (go-try
    (let [row (<? (db/one d (str "INSERT INTO characters (id, owner_id, name, theme, data)
                                  VALUES (CAST(:id AS uuid), :u, :name, :theme, CAST(:data AS jsonb)) RETURNING " columns)
                          {:id (k/new-id) :u user :name (:name character) :theme (:theme character) :data (body character)}))]
      {:character (assoc (row->character row) :access "owner")})))

(defn fetch
  "The owner, or the GM of the character's campaign, may read it. :access says which."
  [d user id]
  (go-try
    (let [row (<? (load! d id))]
      {:character (assoc (row->character row) :access (<? (access! d row user)))})))

(defn save!
  "Saves over :version, for the owner or the campaign's GM. A version that's moved on means a 409."
  [d user id character]
  (go-try
    (let [access (<? (access! d (<? (load! d id)) user))
          row (<? (db/one d (str "UPDATE characters SET name = :name, theme = :theme, data = CAST(:data AS jsonb),
                                         version = version + 1, updated_at = now()
                                  WHERE id = CAST(:id AS uuid) AND version = :v RETURNING " columns)
                          {:id id :v (:version character) :name (:name character)
                           :theme (:theme character) :data (body character)}))]
      (when-not row (reject! 409 "This character changed somewhere else. Reload to get the latest." {}))
      {:character (assoc (row->character row) :access access)})))

(defn delete! [d images user id]
  (go-try
    (let [{:keys [portrait_key]} (own! (<? (load! d id)) user)]
      (<? (db/query d "DELETE FROM characters WHERE id = CAST(:id AS uuid)" {:id id}))
      (when portrait_key (<? (images/delete-image! images portrait_key)))
      {:deleted id})))

(defn set-campaign!
  "Puts the owner's character into a campaign they belong to (or takes it out with nil)."
  [d user id campaign-id]
  (go-try
    (own! (<? (load! d id)) user)
    (when (and campaign-id (not (<? (role-in d user campaign-id))))
      (reject! 403 "Join the campaign before adding a character to it" {}))
    {:character (-> (<? (db/one d (str "UPDATE characters SET campaign_id = CAST(:c AS uuid), updated_at = now()
                                         WHERE id = CAST(:id AS uuid) RETURNING " columns)
                                 {:id id :c campaign-id}))
                    row->character
                    (assoc :access "owner"))}))

(defn- jpeg? [^js bytes]
  (and (> (.-length bytes) 3) (= 0xFF (aget bytes 0)) (= 0xD8 (aget bytes 1)) (= 0xFF (aget bytes 2))))

(defn set-portrait!
  "The owner or the campaign's GM. bytes is the decoded upload. The browser always sends a JPEG it drew itself, so anything else is refused.
   Each upload gets a new key, so CloudFront can cache portraits forever."
  [d images user id ^js bytes]
  (go-try
    (when-not (<= (.-length bytes) max-portrait-bytes) (reject! 413 "That picture is too large" {}))
    (when-not (jpeg? bytes) (reject! 422 "Portraits must be JPEG images" {}))
    (let [{old :portrait_key :as found} (<? (load! d id))
          access (<? (access! d found user))
          key (str "portraits/" id "/" (k/new-id) ".jpg")]
      (<? (images/put-image! images key bytes))
      (let [row (<? (db/one d (str "UPDATE characters SET portrait_key = :k, updated_at = now()
                                    WHERE id = CAST(:id AS uuid) RETURNING " columns)
                            {:id id :k key}))]
        (when old (<? (images/delete-image! images old)))
        {:character (assoc (row->character row) :access access)}))))
