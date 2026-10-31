(ns ttg.library.events
  "Library events. Handlers are plain named functions (tested directly); the server, files and downloads are effects.
   The editor validates with the same code the API runs, so a save the form allows is a save the server takes."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [ttg.auth :as auth]
            [ttg.http :as http]
            [ttg.library :as lib]
            [ttg.library.db :as db]
            [ttg.library.fields :as fields]
            [ttg.library.io :as io]
            [ttg.routes :as routes]))

(defn- check-and-throw [spec db]
  (when-not (s/valid? spec db)
    (throw (ex-info (str "app-db failed spec: " (s/explain-str spec db)) {:db db}))))

(def validate-spec
  (if goog.DEBUG
    (rf/after (partial check-and-throw ::db/app-db))
    []))

(def default-type :talents)

(defn current-type [db] (or (get-in db [:route :type]) default-type))

;; ---------- Loading ----------

(defn load-library [{:keys [db]} _]
  (cond
    (not (http/configured?))
    {:db (update db :library assoc :status :error :error "The library server isn't deployed yet, so the library only works in bb dev.")}
    (and (auth/enabled?) (not (auth/signed-in?)))
    {:db (update db :library assoc :status :error :error "Sign in to use your library.")}
    :else
    {:db (update db :library assoc :status :loading :error nil)
     :fx [[::http/request {:path "/library" :on-ok [::loaded] :on-fail [::load-failed]}]]}))

(defn- index [entries] (update-vals entries #(into {} (map (juxt :id identity)) %)))

(declare open-from-route)

(defn loaded [db [_ {:keys [entries]}]]
  (-> db (update :library assoc :status :ready :entries (index entries)) open-from-route))

(defn load-failed [db [_ {:keys [error]}]]
  (update db :library assoc :status :error :error (or error "The library didn't load")))

;; ---------- The editor follows the route ----------

(defn- draft [type entry new?]
  {:type type :base entry :strings (io/strings type entry) :errors {} :new? new? :saving? false :confirm-delete? false})

(defn open-from-route
  "Opens the entry the route names (or a blank one for .../new), closes the editor otherwise.
   An entry the library doesn't have yet (still loading) opens once it arrives."
  [db]
  (let [{:keys [type id]} (:route db)
        entry (get-in db [:library :entries type id])]
    (assoc-in db [:library :draft]
              (cond (and type (= :new id) (fields/type-info type)) (draft type (lib/new-entry type "") true)
                    (and entry (fields/type-info type)) (draft type entry false)
                    :else nil))))

(defn new-entry [{:keys [db]} _]
  {:fx [[::routes/navigate {:page :library :type (current-type db) :id :new}]]})

(defn close-editor [{:keys [db]} _]
  {:fx [[::routes/navigate {:page :library :type (current-type db)}]]})

(defn search [db [_ q]] (assoc-in db [:library :query] q))

(defn dismiss-toast [db [_ text]]
  (cond-> db (= text (get-in db [:library :toast])) (assoc-in [:library :toast] nil)))

(defn- toast-fx
  "Shows text, and clears it after a few seconds unless a newer toast replaced it."
  [db text]
  [(assoc-in db [:library :toast] text) [:dispatch-later {:ms 3500 :dispatch [::dismiss-toast text]}]])

;; ---------- Editing and saving ----------

(defn edit-field [db [_ path value]]
  (update-in db [:library :draft] #(-> % (assoc-in [:strings path] value) (update :errors dissoc path))))

(defn- draft->entry
  "{:entry e :errors {path msg}}: field parsing first, then the content spec for whatever the fields can't see."
  [{:keys [type base strings new?]} taken]
  (let [{:keys [entry errors]} (io/apply-strings type base strings)
        entry (cond-> entry new? (assoc :id (lib/unique-id taken (io/slug (:name entry)))))]
    {:entry entry
     :errors (if (seq errors) errors (or (lib/problems type entry) {}))}))

(defn save [{:keys [db]} _]
  (let [{:keys [type] :as d} (get-in db [:library :draft])
        taken (set (keys (get-in db [:library :entries type])))
        {:keys [entry errors]} (draft->entry d taken)]
    (if (seq errors)
      {:db (assoc-in db [:library :draft :errors] errors)}
      {:db (assoc-in db [:library :draft :saving?] true)
       :fx [[::http/request {:method "PUT" :path (str "/library/" (name type) "/" (name (:id entry)))
                             :body {:entry entry} :on-ok [::saved type] :on-fail [::save-failed]}]]})))

(defn saved [{:keys [db]} [_ type {:keys [entry]}]]
  (let [was-new? (get-in db [:library :draft :new?])
        [db toast] (toast-fx (-> db
                                 (assoc-in [:library :entries type (:id entry)] entry)
                                 (assoc-in [:library :draft] (draft type entry false)))
                             (str "Saved " (:name entry)))]
    {:db db
     :fx (cond-> [toast] was-new? (conj [::routes/navigate {:page :library :type type :id (:id entry)}]))}))

(defn save-failed [db [_ {:keys [error problems]}]]
  (update-in db [:library :draft] assoc :saving? false
             :errors (or (not-empty problems) {[] (or error "Saving failed")})))

;; ---------- Deleting ----------

(defn ask-delete [db [_ ask?]] (assoc-in db [:library :draft :confirm-delete?] ask?))

(defn delete-entry [{:keys [db]} _]
  (let [{:keys [type base]} (get-in db [:library :draft])]
    {:db (assoc-in db [:library :draft :saving?] true)
     :fx [[::http/request {:method "DELETE" :path (str "/library/" (name type) "/" (name (:id base)))
                           :on-ok [::deleted type (:id base) (:name base)] :on-fail [::save-failed]}]]}))

(defn deleted [{:keys [db]} [_ type id entry-name]]
  (let [[db toast] (toast-fx (update-in db [:library :entries type] dissoc id) (str "Deleted " entry-name))]
    {:db db :fx [toast [::routes/navigate {:page :library :type type}]]}))

;; ---------- Import ----------

(defn open-import [db _]
  (assoc-in db [:library :import] {:step :pick :csv-type (current-type db) :rows [] :notes [] :saved 0}))

(defn close-import [db _] (assoc-in db [:library :import] nil))

(defn- csv-rows
  "Plan rows for a CSV file; rows whose cells don't parse are invalid before the spec ever sees them."
  [library type text]
  (let [{:keys [rows ignored]} (io/from-csv type text)
        bad (filter #(seq (:errors %)) rows)
        good (remove #(seq (:errors %)) rows)]
    {:rows (into (mapv (fn [{:keys [line entry errors]}]
                         {:type type :id (:id entry) :name (:name entry) :line line :status :invalid :problems errors})
                       bad)
                 (lib/plan-import library {type (mapv :entry good)}))
     :notes (mapv #(str "Ignored column \"" % "\"") ignored)}))

(defn- json-rows [library text]
  (let [{:keys [entries unknown]} (io/from-json lib/types (js->clj (js/JSON.parse text)))]
    {:rows (lib/plan-import library entries)
     :notes (mapv #(str "Skipped \"" % "\": not a library type") unknown)}))

(defn parse-file
  "Text of a picked file -> import preview. JSON is any type; CSV is the type chosen in the dialog."
  [db [_ file-name text]]
  (let [library (get-in db [:library :entries])
        csv-type (get-in db [:library :import :csv-type])
        result (try (if (str/ends-with? (str/lower-case file-name) ".csv")
                      (csv-rows library csv-type text)
                      (json-rows library text))
                    (catch :default e {:error (str "Couldn't read " file-name ": " (ex-message e))}))]
    (update-in db [:library :import] merge {:file-name file-name :text text :error nil :rows [] :notes []}
               (if (:error result) result (assoc result :step :preview)))))

(defn set-csv-type
  "Picking a different CSV type re-reads the same file as that type."
  [db [_ type]]
  (let [{:keys [file-name text]} (get-in db [:library :import])
        db (assoc-in db [:library :import :csv-type] type)]
    (if text (parse-file db [::parse-file file-name text]) db)))

(defn- importable [rows] (filterv #(#{:add :replace} (:status %)) rows))

(defn- send-chunk [chunk]
  [::http/request {:method "POST" :path "/library/import"
                   :body {:entries (update-vals (group-by :type chunk) #(mapv :entry %))}
                   :on-ok [::chunk-done chunk] :on-fail [::import-failed]}])

(defn run-import [{:keys [db]} _]
  (let [[first-chunk & more] (partition-all db/import-chunk (importable (get-in db [:library :import :rows])))]
    {:db (update-in db [:library :import] assoc :step :running :saved 0 :pending (vec more))
     :fx [(send-chunk (vec first-chunk))]}))

(defn chunk-done
  "A chunk is stored: show it in the library, then send the next one."
  [{:keys [db]} [_ chunk {:keys [saved]}]]
  (let [[next-chunk & more] (get-in db [:library :import :pending])
        db (-> db
               (update-in [:library :entries] #(reduce (fn [es {:keys [type entry]}] (assoc-in es [type (:id entry)] entry)) % chunk))
               (update-in [:library :import :saved] + saved)
               (assoc-in [:library :import :pending] (vec more)))]
    (if next-chunk
      {:db db :fx [(send-chunk (vec next-chunk))]}
      (let [[db toast] (toast-fx (assoc-in db [:library :import :step] :done)
                                 (str "Imported " (get-in db [:library :import :saved]) " entries"))]
        {:db db :fx [toast]}))))

(defn import-failed [db [_ {:keys [error]}]]
  (update-in db [:library :import] assoc :step :preview :error (str "Import stopped: " (or error "the server refused it")
                                                                    ". Entries saved so far are kept.")))

;; ---------- Files ----------

(rf/reg-fx ::read-file
  (fn [{:keys [^js file on-read]}]
    (-> (.text file) (.then #(rf/dispatch (conj on-read (.-name file) %))))))

(rf/reg-fx ::download
  (fn [{:keys [file-name text mime]}]
    (let [url (js/URL.createObjectURL (js/Blob. #js [text] #js {:type mime}))
          a (js/document.createElement "a")]
      (set! (.-href a) url)
      (set! (.-download a) file-name)
      (.click a)
      (js/setTimeout #(js/URL.revokeObjectURL url) 1000))))

(defn pick-file [_ [_ file]] {:fx [[::read-file {:file file :on-read [::parse-file]}]]})

(defn export-csv [{:keys [db]} [_ template?]]
  (let [type (current-type db)
        entries (when-not template? (sort-by :name (vals (get-in db [:library :entries type]))))]
    {:fx [[::download {:file-name (str (name type) (when template? "-template") ".csv")
                       :text (io/to-csv type entries) :mime "text/csv"}]]}))

;; ---------- Registration ----------

(rf/reg-event-db ::initialize (fn [db _] (assoc db :library db/default-library)))
(rf/reg-event-fx ::load [validate-spec] load-library)
(rf/reg-event-db ::loaded [validate-spec] loaded)
(rf/reg-event-db ::load-failed [validate-spec] load-failed)
(rf/reg-event-db ::route-changed [validate-spec] (fn [db _] (open-from-route db)))
(rf/reg-event-fx ::new-entry [validate-spec] new-entry)
(rf/reg-event-fx ::close-editor [validate-spec] close-editor)
(rf/reg-event-db ::search [validate-spec] search)
(rf/reg-event-db ::dismiss-toast [validate-spec] dismiss-toast)
(rf/reg-event-db ::edit-field [validate-spec] edit-field)
(rf/reg-event-fx ::save [validate-spec] save)
(rf/reg-event-fx ::saved [validate-spec] saved)
(rf/reg-event-db ::save-failed [validate-spec] save-failed)
(rf/reg-event-db ::ask-delete [validate-spec] ask-delete)
(rf/reg-event-fx ::delete [validate-spec] delete-entry)
(rf/reg-event-fx ::deleted [validate-spec] deleted)
(rf/reg-event-db ::open-import [validate-spec] open-import)
(rf/reg-event-db ::close-import [validate-spec] close-import)
(rf/reg-event-fx ::pick-file [validate-spec] pick-file)
(rf/reg-event-db ::parse-file [validate-spec] parse-file)
(rf/reg-event-db ::set-csv-type [validate-spec] set-csv-type)
(rf/reg-event-fx ::run-import [validate-spec] run-import)
(rf/reg-event-fx ::chunk-done [validate-spec] chunk-done)
(rf/reg-event-db ::import-failed [validate-spec] import-failed)
(rf/reg-event-fx ::export-csv [validate-spec] export-csv)
