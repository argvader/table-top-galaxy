(ns ttg.ui.drawer
  "The side drawer's frame: a side panel on desktop, a bottom sheet on a phone (css/game.css .drawer). The roll drawer
   and the builder's info drawer fill it. closing? plays the exit animation and makes the panel inert while it leaves.")

(defn panel
  "title and meta head the panel; close-label names the × button for screen readers."
  [{:keys [title meta on-close closing? close-label]} & body]
  (let [leaving (when closing? "closing")]
    [:<>
     [:div.scrim {:class leaving :on-click on-close}]
     [:aside.drawer {:class leaving :inert (when closing? "") :aria-modal "true" :role "dialog" :aria-labelledby "d-title"}
      [:div.grab {:aria-hidden true}]
      [:header [:div [:h2#d-title title] (when meta [:div.meta meta])]
       [:button.x {:type "button" :aria-label (or close-label "Close") :on-click on-close} "×"]]
      (into [:div.body] body)]]))
