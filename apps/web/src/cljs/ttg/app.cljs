(ns ttg.app
  "Web entry point. Today it hosts the standalone roller; routing arrives with the sheet (M4)."
  (:require [re-frame.core :as rf]
            [reagent.dom.client :as rdc]
            [ttg.roller.effects]
            [ttg.roller.events :as ev]
            [ttg.roller.views :as views]))

(defonce ^:private root (delay (rdc/create-root (js/document.getElementById "app"))))

(defn mount! []
  (rf/clear-subscription-cache!)
  (rdc/render @root [views/page]))

(defn- typing? [] (re-matches #"INPUT|TEXTAREA|SELECT" (.-tagName js/document.activeElement)))

;; R rolls, as in the prototype, unless a modifier is held or focus is in a text field.
(defn- on-key [e]
  (when (and (= "r" (.toLowerCase (.-key e))) (not (or (.-metaKey e) (.-ctrlKey e) (.-altKey e))) (not (typing?)))
    (.preventDefault e)
    (rf/dispatch [::ev/roll])))

(defonce ^:private keys-bound (delay (.addEventListener js/document "keydown" on-key)))

(defn init []
  (rf/dispatch-sync [::ev/initialize])
  @keys-bound
  (mount!))
