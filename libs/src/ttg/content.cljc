(ns ttg.content
  "Merges content from every source book into one library, then narrows it to a setting.
   Pure data in, data out: the JVM tools read EDN files, the web app reads the bundled JSON.")

;; Low to high: a later source replaces (or patches, or removes) an earlier entry with the same :id.
(def precedence ["crb" "epg" "sotb" "sotb-guns" "house"])

(defn- apply-entry
  "One source's entry onto the merged map. :patch merges into the existing entry, :remove deletes it.
   :sources records every book that touched the entry, oldest first."
  [merged {:keys [id patch remove] :as entry}]
  (let [prior (get merged id)
        src (:source entry)]
    (cond
      remove (dissoc merged id)
      (and patch prior) (assoc merged id (-> (merge prior (dissoc entry :patch :source))
                                             (update :sources (fnil conj []) src)))
      :else (assoc merged id (assoc entry :sources (conj (vec (:sources prior)) src))))))

(defn merge-library
  "books: {\"crb\" {:talents [...] :skills [...]} \"sotb\" {...}} -> {:talents {id entry} ...}"
  [books]
  (reduce (fn [lib book]
            (reduce-kv (fn [lib type entries] (update lib type #(reduce apply-entry (or % {}) entries)))
                       lib (get books book {})))
          {} (filter books precedence)))

(defn- pick
  "Narrows one entry type by a rule from the setting profile:
   {:books [...]}    entries whose original source is one of these books
   {:list type}      ids listed in another type (a setting's allowed-talent table)
   {:settings #{..}} gear tagged with one of these settings
   :all              everything"
  [lib type rule]
  (let [entries (vals (get lib type))]
    (cond
      (= :all rule) entries
      (:books rule) (filter #(some (set (:books rule)) [(get-in % [:source :book])]) entries)
      (:list rule) (keep #(get-in lib [type (:id %)]) (vals (get lib (:list rule))))
      (:settings rule) (filter #((:settings rule) (:setting %)) entries))))

(defn for-setting
  "The library a campaign in this setting uses: {type [entries sorted by name]}."
  [lib profile]
  (into {} (for [[type rule] (:types profile)]
             [type (vec (sort-by :name (pick lib type rule)))])))

(defn unresolved
  "Ids a profile's :list rules point at that no source defines; empty when the library is complete."
  [lib profile]
  (into {} (for [[type rule] (:types profile) :when (:list rule)
                 :let [missing (remove #(get-in lib [type %]) (keys (get lib (:list rule))))]
                 :when (seq missing)]
             [type (vec missing)])))
