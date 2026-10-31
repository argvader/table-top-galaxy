(ns ttg.ui.dice3d
  "Acts out an engine roll with 3D dice. The engine has already picked every face; this throws the dice in the sim,
   repaints each one so the face it lands on shows the engine's face, and replays the throw.
   Loaded lazily (its own shadow module) so three and cannon-es only download when someone rolls."
  (:require [ttg.ui.dice :as ui]
            [ttg.ui.dice3d.geometry :as geo]
            [ttg.ui.dice3d.render :as render]
            [ttg.ui.dice3d.sim :as sim]))

(defn- cast-dice
  "Pairs each rolled die with the repainting that puts its engine face on the face it landed on."
  [rolled landed]
  (mapv (fn [{:keys [type face]} up]
          {:type type :kind (:shape (ui/die-info type)) :layout (geo/face-layout (ui/face-count type) up face)})
        rolled landed))

(defn play!
  "Throws the rolled dice [{:type :face}] and calls on-done when they rest. Calls on-done straight away when there's
   no WebGL or no throw came to rest flat, so the result always shows."
  [rolled on-done]
  (try
    (if-let [throw (and (render/init!)
                        (sim/simulate (map #(:shape (ui/die-info (:type %))) rolled) (render/bounds) js/Math.random))]
      (render/play! (cast-dice rolled (:landed throw)) (:frames throw) on-done)
      (do (render/clear!) (on-done)))
    (catch :default e
      (js/console.error "3D dice failed" e)
      (on-done))))
