(ns ttg.chronicle.page
  "One Chronicle page as the summarizer writes it: Markdown with a little front matter. bb chronicle:publish reads these."
  (:require [clojure.string :as str]))

(def wiki-kinds "Wiki folder -> the entry's kind." {"pcs" "pc" "npcs" "npc" "factions" "faction" "locations" "location"})

(def ^:private slug-pattern #"[a-z0-9-]{1,120}")

(defn- field
  "\"title: \\\"A Debt\\\"\" -> [:title \"A Debt\"]."
  [line]
  (when-let [[_ k v] (re-matches #"([a-z_]+):\s*(.*)" (str/trim line))]
    [(keyword k) (str/replace (str/trim v) #"^\"(.*)\"$" "$1")]))

(defn parse
  "{:front {...} :body \"...\"}. A page with no front matter is all body."
  [text]
  (if-let [[_ head body] (re-matches #"---\r?\n([\s\S]*?)\r?\n---\r?\n?([\s\S]*)" text)]
    {:front (into {} (keep field) (str/split-lines head)) :body (str/trim body)}
    {:front {} :body (str/trim text)}))

(defn entry
  "What gets published for a page. A session's file is named by the date it was played."
  [kind slug text]
  (let [{{:keys [title number played_on]} :front body :body} (parse text)]
    (when-not (re-matches slug-pattern slug)
      (throw (ex-info (str "\"" slug "\" isn't a usable page name: use lowercase words joined by hyphens") {:slug slug})))
    (when (str/blank? title)
      (throw (ex-info (str kind "/" slug " has no title in its front matter") {:slug slug})))
    (cond-> {:kind kind :slug slug :title title :body body}
      (= "session" kind) (assoc :number (some-> number parse-long) :played-on (or played_on slug)))))

(defn- id-of [e] [(:kind e) (:slug e)])

(defn changes
  "How the pages here differ from what's published: {:new [...] :changed [...] :same [...] :gone [...]}. A page that's
   gone from here stays published until it's unpublished by name."
  [local published]
  (let [was (into {} (map (juxt id-of identity)) published)
        here (set (map id-of local))]
    (assoc (group-by #(if-let [old (was (id-of %))] (if (= old %) :same :changed) :new) local)
           :gone (vec (remove (comp here id-of) published)))))
