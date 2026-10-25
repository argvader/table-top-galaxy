(ns ttg.roller.effects
  "The 3D dice as a re-frame effect. The roll is already decided; this only decides when it is shown.
   ttg.ui.dice3d is deliberately not required: requiring it would pull three and cannon-es into the main bundle."
  {:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [ttg.ui.dice3d]}}}}
  (:require [re-frame.core :as rf]
            [shadow.lazy :as lazy]))

(def ^:private dice3d (lazy/loadable ttg.ui.dice3d/play!))

(def ^:private give-up-ms 15000)   ; never leave a roll hidden if the 3D module stalls

;; Resolves to play!, or rejects; lazy/load can also throw before it returns a promise.
(defn- load-dice3d []
  (try (lazy/load dice3d) (catch :default e (js/Promise.reject e))))

(defn- reduced-motion? []
  (.-matches (js/window.matchMedia "(prefers-reduced-motion: reduce)")))

;; {:dice [{:type :face}] :on-done event}. on-done is dispatched when the dice rest, or at once without 3D;
;; it may be dispatched more than once, so its handler must ignore repeats.
(rf/reg-fx ::act-out
  (fn [{:keys [dice on-done]}]
    (let [done #(rf/dispatch on-done)]
      (if (reduced-motion?)
        (done)
        (do (js/setTimeout done give-up-ms)
            (-> (load-dice3d)
                (.then (fn [play!] (play! dice done)))
                (.catch (fn [e] (js/console.error "3D dice didn't load" e) (done)))))))))
