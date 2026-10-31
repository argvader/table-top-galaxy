(ns ttg.api.service
  "Library operations: one item per entry, pk LIBRARY#<owner>, sk <TYPE>#<id>, the entry stored as EDN.
   Entries are validated against the content specs here, so a bad entry never reaches the table.
   Errors are ex-info with :status, which the handler turns into the response."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [ttg.api.async :refer [go-try <?]]
            [ttg.api.table :as table]
            [ttg.library :as lib]))

(defn reject! [status message data]
  (throw (ex-info message (assoc data :status status))))

(defn pk [owner] (str "LIBRARY#" owner))
(defn sk [type id] (str (str/upper-case (name type)) "#" (name id)))

(defn- ->item [owner now type entry]
  {:pk (pk owner) :sk (sk type (:id entry)) :type (name type) :id (name (:id entry)) :name (:name entry)
   :edn (pr-str entry) :updatedAt now})

(defn library
  "{:entries {type [entry ...]}}, each type sorted by name."
  [t owner]
  (go-try
    (let [rows (<? (table/query t (pk owner) ""))]
      {:entries (update-vals (group-by #(keyword (:type %)) rows)
                             #(vec (sort-by :name (map (comp reader/read-string :edn) %))))})))

(defn save-entry!
  "Creates or replaces one entry. The id in the path must match the entry's."
  [t owner type id entry]
  (go-try
    (let [entry (lib/clean entry)]
      (when (not= id (name (:id entry)))
        (reject! 400 "The entry's id doesn't match the address" {:id id}))
      (when-let [ps (lib/problems type entry)]
        (reject! 422 "The entry isn't valid" {:problems ps}))
      (<? (table/put-items! t [(->item owner (table/now) type entry)]))
      {:entry entry})))

(defn delete-entry! [t owner type id]
  (go-try
    (<? (table/delete-items! t [{:pk (pk owner) :sk (sk type id)}]))
    {:deleted {:type type :id (keyword id)}}))

(defn import!
  "Saves every valid entry in {type [entry]} and reports the rest, so one bad row never blocks a whole file."
  [t owner entries]
  (go-try
    (let [now (table/now)
          {valid false invalid true} (group-by #(= :invalid (:status %)) (lib/plan-import {} entries))
          saved (<? (table/put-items! t (mapv #(->item owner now (:type %) (:entry %)) valid)))]
      {:saved saved
       :rejected (mapv #(select-keys % [:type :id :name :problems]) invalid)})))
