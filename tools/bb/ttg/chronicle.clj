(ns ttg.chronicle
  "bb chronicle:*: what the session pipeline in campaigns/manifest-destiny reads from a campaign. The players come from
   the campaign's members rather than a hand-kept cast, so the speaker map and summaries use the names in the app."
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [ttg.data-deploy :as data]
            [ttg.deploy :as deploy]))

(def ^:private campaign-dir "campaigns/manifest-destiny")
(def ^:private dev-api "http://localhost:3000")
(def ^:private dev-owner "local-gm")

(defn- campaign-arg
  "[campaign-id the other args]. Stops with usage if there's no id."
  [args usage]
  (let [id (first (filter #(re-matches #"[0-9a-fA-F-]{36}" %) args))]
    (when-not id (throw (ex-info usage {})))
    [id (remove #{id} args)]))

(defn- local? [args] (some #{"local"} args))

(defn- dev-get
  "A GM's read from the running dev API (bb dev)."
  [path]
  (let [resp (try (http/get (str dev-api path) {:headers {"x-ttg-owner" dev-owner "accept" "application/edn"} :throw false})
                  (catch java.net.ConnectException _
                    (throw (ex-info (str "The API isn't running on " dev-api ". Start it with bb dev.") {}))))]
    (when-not (= 200 (:status resp))
      (throw (ex-info (str "Couldn't read " path ": " (:body resp)) {})))
    (:body resp)))

;; ---------- Roster ----------

(defn- player
  "One player's roster entry, from a member and their character in this campaign."
  [{:keys [user_id display_name character archetype career portrait_key]}]
  (cond-> {:user-id user_id :player display_name}
    character (assoc :character character)
    archetype (assoc :archetype archetype)
    career (assoc :career career)
    portrait_key (assoc :portrait-key portrait_key)))

(defn roster
  "From rows ordered oldest character first, so a player with two gets the one touched last."
  [id rows]
  (let [{gms "gm" players "player"} (group-by :role rows)]
    {:campaign-id id
     :gm (:display_name (first gms))
     :players (->> players
                   (map (juxt :user_id player))
                   (into {})
                   vals
                   (sort-by :player)
                   vec)}))

(def ^:private roster-sql
  "SELECT m.user_id, m.display_name, m.role, c.name AS character, c.portrait_key,
          c.data->>'archetype' AS archetype, c.data->>'career' AS career
   FROM campaign_members m
   LEFT JOIN characters c ON c.owner_id = m.user_id AND c.campaign_id = m.campaign_id
   WHERE m.campaign_id = CAST(:id AS uuid)
   ORDER BY c.updated_at NULLS FIRST")

(defn- stage-rows [args id]
  (let [[stage cfg] (deploy/setup args)]
    (data/execute! cfg (data/database cfg stage) roster-sql [{:name "id" :value {:stringValue id}}])))

(defn- local-rows
  "The dev API's party, shaped like the SQL rows. The party leaves out the GM, so the dev GM stands in."
  [id]
  (cons {:user_id dev-owner :display_name dev-owner :role "gm"}
        (for [{:keys [user-id name character]} (:members (edn/read-string (dev-get (str "/campaigns/" id "/party"))))]
          {:user_id user-id :display_name name :role "player" :character (:name character)
           :archetype (:archetype character) :career (:career character) :portrait_key (:portrait-key character)})))

(defn roster!
  "bb chronicle:roster [local|dev|prod] <campaign-id>: write roster.json for the speaker map and the summarizer."
  [& args]
  (let [[id others] (campaign-arg args "Usage: bb chronicle:roster [local|dev|prod] <campaign-id>")
        {:keys [players] :as r} (roster id (if (local? others) (local-rows id) (stage-rows others id)))
        out (str campaign-dir "/roster.json")]
    (spit out (str (json/generate-string r {:pretty true}) "\n"))
    (println "Wrote" out "with" (count players) "player(s)")
    (doseq [{:keys [player character]} players]
      (println " " player "as" (or character "(no character yet)")))))

;; ---------- Manager document ----------

(defn- newest-session
  "The latest dated folder under sessions-raw."
  []
  (or (->> (fs/list-dir (str campaign-dir "/sessions-raw"))
           (filter fs/directory?)
           (map (comp str fs/file-name))
           (filter #(re-matches #"\d{4}-\d{2}-\d{2}" %))
           sort
           last)
      (throw (ex-info "No session folder yet. Create sessions-raw/<DATE>/ first." {}))))

(defn- stage-manager [args id]
  (let [[stage cfg] (deploy/setup args)
        rows (data/execute! cfg (data/database cfg stage) "SELECT manager::text AS manager FROM campaigns WHERE id = CAST(:id AS uuid)"
                            [{:name "id" :value {:stringValue id}}])]
    (or (:manager (first rows)) (throw (ex-info (str "No manager document for campaign " id " on " stage) {})))))

(defn manager!
  "bb chronicle:manager [local|dev|prod] <campaign-id> [--date D]: copy the manager document in for the summarizer. It
   holds GM secrets, so it goes into sessions-raw, which git ignores."
  [& args]
  (let [[id others] (campaign-arg args "Usage: bb chronicle:manager [local|dev|prod] <campaign-id> [--date 2026-11-04]")
        date (or (second (drop-while #(not= "--date" %) others)) (newest-session))
        stage-args (remove #{"--date" date} others)
        text (if (local? stage-args) (dev-get (str "/campaigns/" id "/manager")) (stage-manager stage-args id))
        out (str campaign-dir "/sessions-raw/" date "/manager.json")]
    (fs/create-dirs (fs/parent out))
    (spit out text)
    (println "Wrote" out)))
