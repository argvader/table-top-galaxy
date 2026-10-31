(ns ttg.api.campaigns
  "Campaigns, membership and invites. The GM who creates a campaign is its only gm; others join as players
   through an invite link. Anything under the campaign's :gm key is GM-only: it is stripped here, on the server,
   before a player ever receives the campaign. The manager and manager_content columns are GM-only too, and only the
   manager functions here touch them. Deleting a campaign cascades to members and invites; its characters go back to their owners, unattached."
  (:require [ttg.api.async :refer [go-try <?]]
            [ttg.api.characters :as characters]
            [ttg.api.db :as db]
            [ttg.api.keys :as k]
            [ttg.api.service :refer [reject!]]))

(def invite-days 7)

(defn redact
  "What a member may see: the GM gets everything, a player never gets :gm."
  [campaign role]
  (if (= "gm" role) campaign (dissoc campaign :gm)))

(defn- body [campaign] (db/->json (dissoc campaign :id :name :role)))

(defn- role! [d user-id campaign-id]
  (go-try
    (or (<? (characters/role-in d user-id campaign-id))
        (reject! 404 "No such campaign" {:id campaign-id}))))

(defn- gm! [d user-id campaign-id]
  (go-try
    (when-not (= "gm" (<? (role! d user-id campaign-id))) (reject! 403 "Only the GM can do that" {}))
    true))

(defn mine [d user]
  (go-try
    {:campaigns (<? (db/query d "SELECT c.id, c.name, m.role FROM campaign_members m
                                 JOIN campaigns c ON c.id = m.campaign_id
                                 WHERE m.user_id = :u ORDER BY c.name"
                              {:u (:id user)}))}))

(defn create! [d user campaign]
  (go-try
    (let [id (k/new-id)]
      (<? (db/query d "WITH c AS (INSERT INTO campaigns (id, gm_id, name, data)
                                  VALUES (CAST(:id AS uuid), :gm, :name, CAST(:data AS jsonb)) RETURNING id)
                       INSERT INTO campaign_members (campaign_id, user_id, role, display_name)
                       SELECT id, CAST(:gm AS text), 'gm', CAST(:who AS text) FROM c"
                    {:id id :gm (:id user) :name (:name campaign) :data (body campaign) :who (:name user)}))
      {:campaign (assoc campaign :id id) :role "gm"})))

(defn- members [d id]
  (go-try
    (mapv (fn [{:keys [user_id display_name role]}] {:user-id user_id :name display_name :role role})
          (<? (db/query d "SELECT user_id, display_name, role FROM campaign_members
                           WHERE campaign_id = CAST(:id AS uuid) ORDER BY role, display_name"
                        {:id id})))))

(defn- roster [d id]
  (go-try
    (mapv (fn [{:keys [id name owner_id portrait_key theme]}]
            (cond-> {:id id :name name :owner owner_id}
              portrait_key (assoc :portrait-key portrait_key)
              theme (assoc :theme theme)))
          (<? (db/query d "SELECT id, name, owner_id, portrait_key, theme FROM characters
                           WHERE campaign_id = CAST(:id AS uuid) ORDER BY name"
                        {:id id})))))

(defn fetch
  "The campaign as this member may see it, with its members and the characters in it."
  [d user-id id]
  (go-try
    (let [role (<? (role! d user-id id))
          row (<? (db/one d (str "SELECT name, " (db/json-col "data") " FROM campaigns WHERE id = CAST(:id AS uuid)") {:id id}))]
      {:campaign (assoc (redact (db/parse-json (:data row)) role) :id id :name (:name row))
       :role role
       :members (<? (members d id))
       :characters (<? (roster d id))})))

(defn save! [d user-id id campaign]
  (go-try
    (<? (gm! d user-id id))
    (<? (db/query d "UPDATE campaigns SET name = :name, data = CAST(:data AS jsonb), updated_at = now()
                     WHERE id = CAST(:id AS uuid)"
                  {:id id :name (:name campaign) :data (body campaign)}))
    {:campaign (assoc campaign :id id) :role "gm"}))

(defn delete! [d user-id id]
  (go-try
    (<? (gm! d user-id id))
    (<? (db/query d "DELETE FROM campaigns WHERE id = CAST(:id AS uuid)" {:id id}))
    {:deleted id}))

(defn invite! [d user-id id]
  (go-try
    (<? (gm! d user-id id))
    (let [code (k/new-code)
          expires (.toISOString (js/Date. (+ (js/Date.now) (* invite-days 24 3600 1000))))]
      (<? (db/query d "INSERT INTO invites (code, campaign_id, created_by, expires_at)
                       VALUES (:code, CAST(:id AS uuid), :u, CAST(:expires AS timestamptz))"
                    {:code code :id id :u user-id :expires expires}))
      {:code code :expires-at expires})))

(defn- live-invite! [d code]
  (go-try
    (or (<? (db/one d "SELECT i.campaign_id, c.name FROM invites i JOIN campaigns c ON c.id = i.campaign_id
                       WHERE i.code = :code AND i.expires_at > now()"
                    {:code code}))
        (reject! 404 "This invite has expired or doesn't exist" {}))))

(defn invite-info [d code]
  (go-try {:campaign-name (:name (<? (live-invite! d code)))}))

(defn join! [d user code]
  (go-try
    (let [{:keys [campaign_id name]} (<? (live-invite! d code))]
      (<? (db/query d "INSERT INTO campaign_members (campaign_id, user_id, role, display_name)
                       VALUES (CAST(:c AS uuid), :u, 'player', :who) ON CONFLICT DO NOTHING"
                    {:c campaign_id :u (:id user) :who (:name user)}))
      {:campaign-id campaign_id :name name})))

(defn remove-member!
  "The GM removes a player, or a player leaves. Their characters leave the campaign with them."
  [d user-id id member-id]
  (go-try
    (let [mine (<? (role! d user-id id))
          theirs (<? (role! d member-id id))]
      (cond (= "gm" theirs) (reject! 400 "The GM can't leave their own campaign; delete it instead" {})
            (and (not= user-id member-id) (not= "gm" mine)) (reject! 403 "Only the GM can remove players" {}))
      (<? (db/query d "WITH freed AS (UPDATE characters SET campaign_id = NULL
                                      WHERE campaign_id = CAST(:c AS uuid) AND owner_id = :m)
                       DELETE FROM campaign_members WHERE campaign_id = CAST(:c AS uuid) AND user_id = :m"
                    {:c id :m member-id}))
      {:removed member-id})))

;; The campaign's written prep: arc, NPCs and ledger. Separate from manager, which is what happens at the table.
(defn manager-content
  "As JSON text. An empty object until the GM loads it."
  [d user-id id]
  (go-try
    (<? (gm! d user-id id))
    (or (:manager_content (<? (db/one d (str "SELECT " (db/json-col "manager_content") " FROM campaigns WHERE id = CAST(:id AS uuid)") {:id id})))
        "{}")))

(defn save-manager-content! [d user-id id text]
  (go-try
    (<? (gm! d user-id id))
    (<? (db/query d "UPDATE campaigns SET manager_content = CAST(:m AS jsonb), updated_at = now() WHERE id = CAST(:id AS uuid)"
                  {:id id :m text}))
    "{}"))

(defn- party-character
  "What the manager needs from a character. The character stays the source; none of this is copied."
  [{:keys [id name portrait_key theme data]}]
  (let [{:keys [archetype career ob]} (db/parse-json data)]
    (cond-> {:id id :name name :ob (select-keys ob [:type :magnitude :attachment :why])}
      archetype (assoc :archetype archetype)
      career (assoc :career career)
      portrait_key (assoc :portrait-key portrait_key)
      theme (assoc :theme theme))))

(defn- party-characters
  "Each owner's character in this campaign, keyed by owner. If someone has two, the one touched last is theirs."
  [d id]
  (go-try
    (into {} (map (juxt :owner_id party-character))
          (<? (db/query d (str "SELECT id, owner_id, name, portrait_key, theme, " (db/json-col "data") " FROM characters
                                WHERE campaign_id = CAST(:id AS uuid) ORDER BY updated_at")
                        {:id id})))))

(defn party
  "The players, GM excluded, each with their character if they've built one. A player is a member, not a character:
   Session 0 answers stay theirs if they rebuild, and someone who has only just joined is still at the table."
  [d user-id id]
  (go-try
    (<? (gm! d user-id id))
    (let [by-owner (<? (party-characters d id))]
      {:members (mapv (fn [{member-id :user-id :as member}]
                        (cond-> member
                          (by-owner member-id) (assoc :character (by-owner member-id))))
                      (filterv #(= "player" (:role %)) (<? (members d id))))})))

(defn library-owner
  "Whose library a member builds from: the campaign's GM. Players have no library of their own."
  [d user-id id]
  (go-try
    (<? (role! d user-id id))
    (:gm_id (<? (db/one d "SELECT gm_id FROM campaigns WHERE id = CAST(:id AS uuid)" {:id id})))))

(defn manager
  "The GM's campaign manager document, as JSON text. An empty object until the first save."
  [d user-id id]
  (go-try
    (<? (gm! d user-id id))
    (or (:manager (<? (db/one d (str "SELECT " (db/json-col "manager") " FROM campaigns WHERE id = CAST(:id AS uuid)") {:id id})))
        "{}")))

(defn save-manager! [d user-id id text]
  (go-try
    (<? (gm! d user-id id))
    (<? (db/query d "UPDATE campaigns SET manager = CAST(:m AS jsonb), updated_at = now() WHERE id = CAST(:id AS uuid)"
                  {:id id :m text}))
    "{}"))
