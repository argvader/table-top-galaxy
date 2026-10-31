(ns ttg.theme
  "Preset palettes a player can give their character's sheet. Each sets the accent for the light and dark themes, and
   a test keeps every one readable: accent text on the sheet, and text on accent buttons, at 4.5:1 or better.
   The CSS is generated from this table, so it's the only place a palette is defined."
  (:require [clojure.string :as str]))

;; The app's own surfaces (apps/web/public/css/app.css), which the accents have to read against.
(def surfaces {:light ["#f6f9f9" "#edf2f2"] :dark ["#141d20" "#1a252a"]})

(def palettes
  [{:id "teal" :name "Concord teal" :light {:accent "#0d6f78" :ink "#ffffff"} :dark {:accent "#49bfc8" :ink "#062224"}}
   {:id "cobalt" :name "Cobalt" :light {:accent "#2456b8" :ink "#ffffff"} :dark {:accent "#7fa9f5" :ink "#0a1a38"}}
   {:id "violet" :name "Violet" :light {:accent "#6a3fb5" :ink "#ffffff"} :dark {:accent "#b79af0" :ink "#1e1038"}}
   {:id "magenta" :name "Neon magenta" :light {:accent "#a8246f" :ink "#ffffff"} :dark {:accent "#f08ac4" :ink "#3a0a26"}}
   {:id "crimson" :name "Crimson" :light {:accent "#ab2a33" :ink "#ffffff"} :dark {:accent "#f28f93" :ink "#3a0b0e"}}
   {:id "ember" :name "Ember" :light {:accent "#a34a0c" :ink "#ffffff"} :dark {:accent "#f2a66a" :ink "#331804"}}
   {:id "brass" :name "Brass" :light {:accent "#7a5c00" :ink "#ffffff"} :dark {:accent "#e3c04f" :ink "#2b2100"}}
   {:id "moss" :name "Moss" :light {:accent "#3a7028" :ink "#ffffff"} :dark {:accent "#94d27c" :ink "#10260a"}}])

(def default-id "teal")

(defn palette [id] (first (filter #(= id (:id %)) palettes)))

(defn- hex->channel [s]
  #?(:clj (Integer/parseInt s 16) :cljs (js/parseInt s 16)))

(defn- linear [v]
  (let [c (/ v 255.0)]
    (if (<= c 0.03928) (/ c 12.92) (Math/pow (/ (+ c 0.055) 1.055) 2.4))))

(defn- luminance [hex]
  (let [[r g b] (map #(linear (hex->channel (subs hex % (+ % 2)))) [1 3 5])]
    (+ (* 0.2126 r) (* 0.7152 g) (* 0.0722 b))))

(defn contrast
  "The WCAG contrast ratio of two #rrggbb colors."
  [a b]
  (let [[hi lo] (sort > [(luminance a) (luminance b)])]
    (/ (+ hi 0.05) (+ lo 0.05))))

(defn- vars [{:keys [accent ink]}] (str "--accent:" accent ";--accent-ink:" ink))

(defn- palette-css [{:keys [id light dark]}]
  (let [sel (str "[data-palette=\"" id "\"]")]
    (str sel "{" (vars light) "}"
         "@media (prefers-color-scheme:dark){:root:not([data-theme=\"light\"]) " sel "{" (vars dark) "}}"
         ":root[data-theme=\"dark\"] " sel "{" (vars dark) "}")))

(defn css [] (str/join (map palette-css palettes)))
