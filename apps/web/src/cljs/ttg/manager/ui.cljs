(ns ttg.manager.ui
  "The manager's small shared pieces: fields bound to a path in the document, chips and labels."
  (:require [re-frame.core :as rf]
            [ttg.manager.events :as ev]
            [ttg.manager.party :as party]
            [ttg.manager.subscriptions :as sub]))

(defn- value [e] (.. e -target -value))

;; dispatch-sync: a controlled input loses its cursor if the new value arrives a tick later.
(defn edit! [path v] (rf/dispatch-sync [::ev/change path v]))

(defn open! [drawer] (rf/dispatch [::ev/open-drawer drawer]))
(defn set-ui! [k v] (rf/dispatch [::ev/set-ui k v]))
(defn op! [& args] (rf/dispatch (into [::ev/op] args)))

(defn at [path] @(rf/subscribe [::sub/at path]))

(defn text-field
  ([path label] (text-field path label ""))
  ([path label placeholder]
   [:label.field [:span label]
    [:input {:value (or (at path) "") :placeholder placeholder :on-change #(edit! path (value %))}]]))

(defn text-area
  ([path label] (text-area path label ""))
  ([path label placeholder]
   [:label.field [:span label]
    [:textarea {:value (or (at path) "") :placeholder placeholder :on-change #(edit! path (value %))}]]))

(defn select-field
  "options are [value label]. A blank value stores nil when nil-blank? is set, and :number? stores numbers."
  [path label options & [{:keys [number?]}]]
  (let [parse (if number? #(when (seq %) (js/parseInt %)) identity)]
    [:label.field [:span label]
     (into [:select {:value (str (at path)) :on-change #(edit! path (parse (value %)))}]
           (for [[v text] options] ^{:key v} [:option {:value (str v)} text]))]))

(defn gm-tag [] [:span.tag.gm "GM only"])

(defn gm-block [& children]
  (into [:div.gmonly [:div.gmhd [gm-tag]]] children))

(defn pc-chip
  ([pc] (pc-chip pc ""))
  ([{:keys [color] :as pc} extra]
   (when pc [:span.who {:style {"--pc" color}} [:span.pcdot] (party/first-name pc) extra])))

(defn intro [title text & [right]]
  [:div.intro [:div [:h1 title] (when text [:p text])] right])

(defn state-tag [state] [:span.st {:class state} state])

(defn act-label [acts id]
  (if-let [{:keys [roman name]} (first (filter #(= id (:id %)) acts))]
    (str "Act " roman " · " name)
    "No act"))

(defn session-label [n] (cond (nil? n) "" (zero? n) "Session 0" :else (str "S" n)))

(defn act-options [acts none]
  (into [["" none]] (map (fn [{:keys [id roman name]}] [id (str "Act " roman " · " name)])) acts))

(defn session-options
  "The sessions something could have happened in."
  [none]
  (let [{:keys [s0 sessions]} @(rf/subscribe [::sub/doc])]
    (concat [["" none]]
            (when (:done s0) [[0 "Session 0"]])
            (for [{:keys [n title status]} sessions :when (not= "plan" status)] [n (str "Session " n ": " title)]))))

(defn add-row
  "A text box with a button; the text goes to on-add and the box empties."
  [_placeholder _label _on-add]
  (let [text (atom "")]
    (fn [placeholder label on-add]
      [:div.addrow
       [:input {:placeholder placeholder :aria-label placeholder
                :on-change #(reset! text (value %))
                :on-key-down (fn [e] (when (= "Enter" (.-key e)) (.click (.. e -target -nextSibling))))}]
       [:button.btn {:type "button"
                     :on-click (fn [e]
                                 (when (seq @text)
                                   (on-add @text)
                                   (reset! text "")
                                   (set! (.. e -target -previousSibling -value) "")))}
        label]])))
