(ns ttg.library.views
  "The GM's content library (M3): browse by type, search, edit an entry, import JSON/CSV, export CSV.
   Desktop shows types | list | editor side by side; a phone shows the list, then the editor in its place."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [ttg.auth :as auth]
            [ttg.http :as http]
            [ttg.library :as lib]
            [ttg.library.events :as ev]
            [ttg.library.fields :as fields]
            [ttg.library.subscriptions :as sub]
            [ttg.routes :as routes]))

;; ---------- One-line summaries for the list ----------

(defn- credits [n] (if n (str n " cr") "No price"))

(defn- damage [{:keys [damage damage-bonus adds-brawn]}]
  (cond damage-bonus (str "Dam +" damage-bonus (when adds-brawn " Brawn"))
        adds-brawn (str "Dam Brawn+" damage)
        :else (str "Dam " damage)))

(defn summary [type e]
  (str/join " · "
            (remove str/blank?
                    (case type
                      :talents [(str "Tier " (:tier e)) (:activation e) (when (:ranked e) "Ranked")]
                      :item-qualities [(str/capitalize (:activation e "")) (when (:ranked e) "Ranked")]
                      :weapons [(:skill e) (damage e) (str "Crit " (or (:crit e) "—")) (:range e)]
                      :armor [(str "Defense " (:defense e)) (str "Soak " (:soak e)) (credits (:price e))]
                      :gear [(when (:encumbrance e) (str "Enc " (:encumbrance e))) (:group e) (credits (:price e))]
                      :attachments [(when (:hard-points e) (str "HP " (:hard-points e))) (credits (:price e))]
                      :archetypes [(str/join " " (map #(str (subs (name %) 0 2) " " (get-in e [:characteristics %]))
                                                      [:brawn :agility :intellect :cunning :willpower :presence]))
                                   (str (:xp e) " XP")]
                      :careers [(str/join ", " (take 3 (:skills e))) (when (> (count (:skills e)) 3) "…")]
                      []))))

;; ---------- Type navigation ----------

(defn- type-nav []
  (let [current @(rf/subscribe [::sub/type])
        counts @(rf/subscribe [::sub/counts])
        other @(rf/subscribe [::sub/other-count])]
    [:nav.lib-types {:aria-label "Library types"}
     [:ul
      (for [{:keys [type label]} fields/editable-types]
        ^{:key type}
        [:li [:a {:href (routes/href {:page :library :type type}) :aria-current (when (= type current) "page")}
              [:span label] [:span.count.mono (counts type)]]])]
     (when (pos? other)
       [:p.note (str other " more entries (skills, crits, adversaries, …) are stored for the sheet and GM tools.")])]))

;; ---------- List ----------

(defn- toolbar [type]
  (let [{:keys [one]} (fields/type-info type)]
    [:div.lib-tools
     [:label.sr {:for "lib-search"} "Search"]
     [:input#lib-search.search {:type "search" :placeholder (str "Search " (str/lower-case (:label (fields/type-info type))))
                                :value @(rf/subscribe [::sub/query])
                                :on-change #(rf/dispatch [::ev/search (.. % -target -value)])}]
     [:button.btn.primary {:type "button" :on-click #(rf/dispatch [::ev/new-entry])} (str "New " one)]
     [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/open-import])} "Import"]
     [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/export-csv false])} "Export CSV"]]))

(defn- status-line []
  (let [{:keys [status error]} @(rf/subscribe [::sub/status])]
    (case status
      :loading [:p.lib-status "Loading your library…"]
      :error [:div.lib-status.bad {:role "alert"} [:p error]
              (cond (and (auth/enabled?) (not (auth/signed-in?)))
                    [:button.btn.primary {:type "button" :on-click auth/sign-in!} "Sign in"]
                    (http/configured?)
                    [:button.linkbtn {:type "button" :on-click #(rf/dispatch [::ev/load])} "Try again"])]
      nil)))

(defn- entry-row [type open-id e]
  [:li [:a {:href (routes/href {:page :library :type type :id (:id e)}) :aria-current (when (= open-id (:id e)) "true")}
        [:span.nm (:name e)]
        [:span.cite (lib/cite (:source e))]
        [:span.sum (summary type e)]]])

(defn- empty-list [type]
  (let [{:keys [label one]} (fields/type-info type)
        q @(rf/subscribe [::sub/query])]
    [:div.lib-empty
     (if (str/blank? q)
       [:<> [:p (str "No " (str/lower-case label) " yet.")]
        [:p.note "Add one with " [:b (str "New " one)] ", or " [:b "Import"] " a CSV or the JSON file "
         [:code "bb library:export"] " writes from your books."]]
       [:p (str "Nothing matches \"" q "\".")])]))

(defn- entry-list []
  (let [type @(rf/subscribe [::sub/type])
        entries @(rf/subscribe [::sub/visible])
        open-id (:id (:base @(rf/subscribe [::sub/draft])))]
    [:section.panel.lib-list {:aria-labelledby "h-list"}
     [:header [:h2#h-list (:label (fields/type-info type))] [:span.label (count entries) " shown"]]
     [toolbar type]
     [status-line]
     (if (seq entries)
       [:ul.rows (for [e entries] ^{:key (:id e)} [entry-row type open-id e])]
       (when (= :ready (:status @(rf/subscribe [::sub/status]))) [empty-list type]))]))

;; ---------- Editor ----------

(defn- field-id [path] (str "f-" (str/join "-" (map name path))))

(defn- error-for [errors path]
  (when-let [e (get errors path)] [:p.err {:id (str (field-id path) "-err")} e]))

(defn- input-attrs [path value errors]
  {:id (field-id path) :value value
   :aria-invalid (when (get errors path) "true")
   :aria-describedby (when (get errors path) (str (field-id path) "-err"))
   :on-change #(rf/dispatch [::ev/edit-field path (.. % -target -value)])})

(defn- abilities-input [path value]
  (let [abilities (if (vector? value) value [])
        put! #(rf/dispatch [::ev/edit-field path %])]
    [:div.abilities
     (for [[i {:keys [name text]}] (map-indexed vector abilities)]
       ^{:key i}
       [:div.ability
        [:input {:aria-label (str "Ability " (inc i) " name") :value name
                 :on-change #(put! (assoc-in abilities [i :name] (.. % -target -value)))}]
        [:textarea {:aria-label (str "Ability " (inc i) " text") :rows 3 :value text
                    :on-change #(put! (assoc-in abilities [i :text] (.. % -target -value)))}]
        [:button.linkbtn {:type "button" :on-click #(put! (into (subvec abilities 0 i) (subvec abilities (inc i))))}
         "Remove ability"]])
     [:button.btn {:type "button" :on-click #(put! (conj abilities {:name "" :text ""}))} "Add ability"]]))

(defn- field-input [{:keys [path kind options] :as field} value errors]
  (let [attrs (input-attrs path value errors)]
    (case kind
      :long [:textarea (assoc attrs :rows 6)]
      :int [:input (assoc attrs :type "number" :inputMode "numeric" :min (:min field) :max (:max field))]
      :bool [:input (assoc attrs :type "checkbox" :value nil :checked (= "yes" value)
                           :on-change #(rf/dispatch [::ev/edit-field path (if (.. % -target -checked) "yes" "no")]))]
      :choice (into [:select attrs] (for [o (cond->> options (not (some #{value} options)) (cons value))]
                                      [:option {:value o} o]))
      :list [:textarea (assoc attrs :rows 3 :placeholder "Separate with ;")]
      :qualities [:input (assoc attrs :placeholder "Auto-fire; Pierce 2; Vicious 1")]
      :abilities [abilities-input path value]
      [:input (cond-> attrs options (assoc :list (str (field-id path) "-opts")))])))

(defn- field-row [{:keys [path label kind options] :as field} strings errors]
  [:div.field {:class (name kind)}
   (if (= :bool kind)
     [:label.check-row [field-input field (get strings path) errors] label]
     [:<> [:label {:for (field-id path)} label (when (= :required (:blank field)) [:span.req " *"])]
      [field-input field (get strings path) errors]])
   (when (and options (= :text kind))
     (into [:datalist {:id (str (field-id path) "-opts")}] (for [o options] [:option {:value o}])))
   (error-for errors path)])

(defn- skills-hint []
  (let [names @(rf/subscribe [::sub/skill-names])]
    (when (seq names)
      [:p.note "Skills in your library: " (str/join ", " names)])))

(defn- form-fields [type strings errors]
  (let [fs (fields/by-type type)
        chars (filter #(= :characteristics (first (:path %))) fs)]
    [:<>
     (for [f (remove #(= :characteristics (first (:path %))) (take-while #(not= [:wounds] (:path %)) fs))]
       ^{:key (field-id (:path f))} [field-row f strings errors])
     (when (seq chars)
       [:fieldset.chars [:legend "Characteristics"]
        (for [f chars] ^{:key (field-id (:path f))} [field-row f strings errors])])
     (for [f (drop-while #(not= [:wounds] (:path %)) fs)]
       ^{:key (field-id (:path f))} [field-row f strings errors])
     (when (= :careers type) [skills-hint])]))

(defn- delete-controls [{:keys [new? confirm-delete? saving? base]}]
  (when-not new?
    (if confirm-delete?
      [:span.confirm {:role "group" :aria-label "Confirm delete"}
       (str "Delete " (:name base) "?")
       [:button.btn.danger {:type "button" :disabled saving? :on-click #(rf/dispatch [::ev/delete])} "Delete"]
       [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/ask-delete false])} "Keep"]]
      [:button.btn.danger-quiet {:type "button" :on-click #(rf/dispatch [::ev/ask-delete true])} "Delete"])))

(defn- editor []
  (when-let [{:keys [type base strings errors new? saving?] :as d} @(rf/subscribe [::sub/draft])]
    (let [{:keys [one label]} (fields/type-info type)
          book? (not= "custom" (get-in base [:source :book]))]
      [:section.panel.lib-editor {:aria-labelledby "h-edit"}
       [:header
        [:button.linkbtn.back {:type "button" :on-click #(rf/dispatch [::ev/close-editor])} (str "← " label)]
        [:h2#h-edit (if new? (str "New " one) (:name base))]
        [:span.cite (lib/cite (:source base))]]
       (when book?
         [:p.note.book-note "From your books. Your edits are saved to your library and keep the page reference."])
       [:form.lib-form {:on-submit (fn [e] (.preventDefault e) (rf/dispatch [::ev/save]))}
        (when-let [e (get errors [])] [:p.err.form-err {:role "alert"} e])
        [form-fields type strings errors]
        [:div.form-actions
         [:button.btn.primary {:type "submit" :disabled saving?} (if saving? "Saving…" "Save")]
         [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/close-editor])} "Close"]
         [:span.spacer]
         [delete-controls d]]
        (when (seq errors)
          [:p.sr {:role "alert"} (str (count errors) " field" (when (> (count errors) 1) "s") " need attention")])]])))

;; ---------- Import ----------

(def ^:private status-labels {:add "New" :replace "Replaces" :same "Unchanged" :invalid "Can't import"})

(defn- file-picker [{:keys [csv-type error]}]
  [:<>
   [:p "Pick a " [:b "JSON"] " file (any mix of types, such as the one " [:code "bb library:export"]
    " writes) or a " [:b "CSV"] " of one type."]
   [:div.field
    [:label {:for "csv-type"} "CSV files hold"]
    (into [:select#csv-type {:value (name csv-type) :on-change #(rf/dispatch [::ev/set-csv-type (keyword (.. % -target -value))])}]
          (for [{:keys [type label]} fields/editable-types] [:option {:value (name type)} label]))
    [:button.linkbtn {:type "button" :on-click #(rf/dispatch [::ev/export-csv true])} "Download a blank CSV template"]]
   [:label.drop
    [:input {:type "file" :accept ".json,.csv,application/json,text/csv"
             :on-change #(when-let [f (aget (.. % -target -files) 0)] (rf/dispatch [::ev/pick-file f]))}]
    [:span "Choose a file"]]
   (when error [:p.err {:role "alert"} error])])

(defn- problem-text [problems]
  (str/join "; " (for [[path msg] problems] (if (seq path) (str (str/join " " (map name path)) ": " msg) msg))))

(defn- preview [{:keys [rows notes file-name error]}]
  (let [counts (frequencies (map :status rows))
        invalid (filter #(= :invalid (:status %)) rows)
        n (+ (counts :add 0) (counts :replace 0))]
    [:<>
     [:p [:b file-name] ": " (count rows) " entries"]
     [:ul.import-counts
      (for [s [:add :replace :same :invalid] :when (pos? (counts s 0))]
        ^{:key s} [:li {:class (name s)} [:b.mono (counts s)] " " (status-labels s)])]
     (for [note notes] ^{:key note} [:p.note note])
     (when (seq invalid)
       [:details.rejects {:open (< (count invalid) 6)}
        [:summary (count invalid) " can't be imported"]
        [:ul (for [[i r] (map-indexed vector (take 50 invalid))]
               ^{:key i} [:li [:b (or (:name r) "(no name)")] (when (:line r) (str " (line " (:line r) ")")) ": "
                          (problem-text (:problems r))])]])
     (when error [:p.err {:role "alert"} error])
     [:div.form-actions
      [:button.btn.primary {:type "button" :disabled (zero? n) :on-click #(rf/dispatch [::ev/run-import])}
       (if (zero? n) "Nothing new to import" (str "Import " n " entr" (if (= 1 n) "y" "ies")))]
      [:button.btn {:type "button" :on-click #(rf/dispatch [::ev/open-import])} "Pick another file"]]]))

(defn- progress [{:keys [rows saved step]}]
  (let [total (count (filter #(#{:add :replace} (:status %)) rows))]
    [:<>
     [:p {:aria-live "polite"} (if (= :done step) (str "Imported " saved " entries.") (str "Saving " saved " of " total "…"))]
     [:div.bar {:role "progressbar" :aria-valuemin 0 :aria-valuemax total :aria-valuenow saved}
      [:i {:style {:width (str (if (pos? total) (* 100 (/ saved total)) 100) "%")}}]]
     (when (= :done step)
       [:div.form-actions [:button.btn.primary {:type "button" :on-click #(rf/dispatch [::ev/close-import])} "Done"]])]))

(defn- import-dialog []
  (when-let [{:keys [step] :as imp} @(rf/subscribe [::sub/import])]
    [:div.scrim {:on-click #(when (= (.-target %) (.-currentTarget %)) (when-not (= :running step) (rf/dispatch [::ev/close-import])))}
     [:div.dialog {:role "dialog" :aria-modal "true" :aria-labelledby "h-import"}
      [:header [:h2#h-import "Import into your library"]
       (when-not (= :running step)
         [:button.linkbtn {:type "button" :on-click #(rf/dispatch [::ev/close-import])} "Close"])]
      (case step
        :pick [file-picker imp]
        :preview [preview imp]
        [progress imp])]]))

;; ---------- Page ----------

(defn- toast []
  (let [t @(rf/subscribe [::sub/toast])]
    [:div.toast {:aria-live "polite" :class (when t "on")} t]))

(defn page []
  (let [draft @(rf/subscribe [::sub/draft])]
    [:<>
     [:main.lib {:class (when draft "editing")}
      [type-nav] [entry-list] [editor]]
     [import-dialog]
     [toast]]))
