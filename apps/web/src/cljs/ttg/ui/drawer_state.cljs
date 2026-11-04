(ns ttg.ui.drawer-state
  "Open, slide away and close an info drawer whose state sits at path in app-db: {:kind :id :closing?}. Pure, so the
   builder and the sheet each register their own events over these. The exit time matches the drawer-out animations."
  (:require [ttg.roller.db :as roller-db]))

(defn open
  "Ignored while the last one is still leaving, so its close can't take this one with it."
  [db path kind id]
  (if (get-in db (conj path :closing?))
    db
    (assoc-in db path {:kind kind :id id})))

(defn dismiss
  "Effects that slide the drawer away, then dispatch close-event once it's gone. nil when nothing is open."
  [db path close-event]
  (when-let [{:keys [closing?]} (get-in db path)]
    (when-not closing?
      {:db (assoc-in db (conj path :closing?) true)
       :fx [[:dispatch-later {:ms roller-db/drawer-out-ms :dispatch [close-event]}]]})))

(defn close [db path] (assoc-in db path nil))
