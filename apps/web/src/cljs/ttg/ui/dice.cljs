(ns ttg.ui.dice
  "SVG dice and symbols, drawn from the same shapes and glyphs as the approved prototypes.
   Custom glyphs only: the FFG dice font can't be used."
  (:require [ttg.dice :as dice]))

(defn- standard
  "A numbered die. :mark is what its icon shows, and :faces how many faces the 3D die has."
  [sides shape mark]
  {:name (str "d" sides) :sides (str "1 to " sides) :shape shape :mark mark :faces (min sides 10) :hex "#e9e3d3"
   :color "var(--d-plain)" :ink "#1b1b1d"})

;; :hex is the same colour as the CSS variable, for the 3D face textures (canvas can't read CSS variables).
(def die-info
  {:proficiency {:name "Proficiency" :sides "d12" :shape :d12 :color "var(--d-prof)" :hex "#f2c230" :ink "#1b1b1d"}
   :ability     {:name "Ability" :sides "d8" :shape :d8 :color "var(--d-ability)" :hex "#3f9b46" :ink "#0d1f10"}
   :boost       {:name "Boost" :sides "d6" :shape :d6 :color "var(--d-boost)" :hex "#8fd0ea" :ink "#10222b"}
   :challenge   {:name "Challenge" :sides "d12" :shape :d12 :color "var(--d-chal)" :hex "#b8262f" :ink "#ffffff"}
   :difficulty  {:name "Difficulty" :sides "d8" :shape :d8 :color "var(--d-diff)" :hex "#5b3c8f" :ink "#ffffff"}
   :setback     {:name "Setback" :sides "d6" :shape :d6 :color "var(--d-set)" :hex "#1b1b1d" :ink "#ffffff"}
   :d4 (standard 4 :d4 "4")
   :d6 (standard 6 :d6 "6")
   :d8 (standard 8 :d8 "8")
   :d10 (standard 10 :d10 "10")
   :d20 (assoc (standard 20 :d20 "20") :faces 20)
   :d100 (standard 100 :d10 "%")
   ;; The two dice a d100 is thrown as.
   :d100-tens (assoc (standard 100 :d10 "%") :hex "#2f5d8a" :ink "#ffffff")
   :d100-ones (standard 100 :d10 "%")})

(defn face-count
  "How many faces the 3D die for this type has."
  [type]
  (or (:faces (die-info type)) (count (dice/faces type))))

(defn face-text
  "What a numbered 3D die shows on a face: 00 to 90 on the tens die, 0 to 9 on the ones die, otherwise 1 up."
  [type face]
  (case type
    :d100-tens (if (zero? face) "00" (str (* 10 face)))
    :d100-ones (str face)
    (str (inc face))))

(def symbol-name
  {:success "Success" :failure "Failure" :advantage "Advantage" :threat "Threat" :triumph "Triumph" :despair "Despair"})

;; Each glyph on a 24x24 grid; :ring adds the circle Triumph and Despair sit in. Shared by the SVG and 3D dice.
(def glyph-paths
  {:success {:d "M12 1 L14.6 9.4 L23 12 L14.6 14.6 L12 23 L9.4 14.6 L1 12 L9.4 9.4 Z"}
   :failure {:d "M4 6.5 L6.5 4 L12 9.5 L17.5 4 L20 6.5 L14.5 12 L20 17.5 L17.5 20 L12 14.5 L6.5 20 L4 17.5 L9.5 12 Z"}
   :advantage {:d "M12 2 L21 10 L18.2 12.6 L12 7.2 L5.8 12.6 L3 10 Z M12 10.5 L21 18.5 L18.2 21.1 L12 15.7 L5.8 21.1 L3 18.5 Z"}
   :threat {:d "M12 22 L21 14 L18.2 11.4 L12 16.8 L5.8 11.4 L3 14 Z M12 13.5 L21 5.5 L18.2 2.9 L12 8.3 L5.8 2.9 L3 5.5 Z"}
   :triumph {:ring true :d "M12 4.5 L13.7 10.3 L19.5 12 L13.7 13.7 L12 19.5 L10.3 13.7 L4.5 12 L10.3 10.3 Z"}
   :despair {:ring true :d "M7 8.6 L8.6 7 L12 10.4 L15.4 7 L17 8.6 L13.6 12 L17 15.4 L15.4 17 L12 13.6 L8.6 17 L7 15.4 L10.4 12 Z"}})

(defn- glyph [{:keys [ring d]}]
  [:<>
   (when ring [:circle {:cx 12 :cy 12 :r 10 :fill "none" :stroke "currentColor" :stroke-width 2.4}])
   [:path {:fill "currentColor" :d d}]])

(def ^:private glyphs (update-vals glyph-paths glyph))

(defn symbol-defs
  "Mount once per page; dice and symbols reference these by id."
  []
  [:svg {:width 0 :height 0 :style {:position "absolute"} :aria-hidden true}
   (into [:defs] (for [[k g] glyphs] [:symbol {:id (str "sym-" (name k)) :view-box "0 0 24 24"} g]))])

(def ^:private shapes
  {:d6 [:rect {:x 4 :y 4 :width 40 :height 40 :rx 7}]
   :d8 [:polygon {:points "24,1.5 46.5,24 24,46.5 1.5,24"}]
   :d12 [:polygon {:points "24,2 43.5,13 43.5,35 24,46 4.5,35 4.5,13"}]
   :d4 [:polygon {:points "24,3 45,43 3,43"}]
   :d10 [:polygon {:points "24,2 45,23 24,46 3,23"}]
   :d20 [:polygon {:points "24,1.5 44,12.75 44,35.25 24,46.5 4,35.25 4,12.75"}]})

(defn numbered-die
  "A standard die showing text."
  [type text]
  (let [{:keys [shape color ink name]} (die-info type)]
    [:svg {:view-box "0 0 48 48" :role "img" :aria-label (str name ": " text)}
     [:g {:fill color :stroke "var(--die-stroke)" :stroke-width 1.5} (shapes shape)]
     [:text {:x 24 :y (if (= shape :d4) 31 25) :text-anchor "middle" :dominant-baseline "central" :fill ink :font-weight 700
             :font-size (if (> (count text) 2) 15 19)}
      text]]))

(defn- face-label [type symbols]
  (str (:name (die-info type)) " die"
       (cond (nil? symbols) ""
             (empty? symbols) ": blank"
             :else (str ": " (apply str (interpose ", " (map symbol-name symbols)))))))

(defn die
  "A die, blank or showing the symbols of a rolled face. label overrides the spoken name."
  ([type] (die type nil nil))
  ([type symbols] (die type symbols nil))
  ([type symbols label]
   (let [{:keys [shape color ink mark]} (die-info type)
         size (if (> (count symbols) 1) 15 20)
         xs (if (> (count symbols) 1) [(- 24 size 0.5) 24.5] [(- 24 (/ size 2))])]
     (if mark
       [numbered-die type mark]
       [:svg {:view-box "0 0 48 48" :role "img" :aria-label (or label (face-label type symbols))}
        [:g {:fill color :stroke "var(--die-stroke)" :stroke-width 1.5} (shapes shape)]
        (into [:g {:style {:color ink}}]
              (map-indexed (fn [i s] [:use {:href (str "#sym-" (name s)) :x (xs i) :y (- 24 (/ size 2)) :width size :height size}])
                           symbols))]))))

(defn sym
  "n copies of a result symbol, decorative (the surrounding text says what they are)."
  ([s] (sym s 1))
  ([s n]
   (into [:<>] (repeat n [:svg {:view-box "0 0 24 24" :aria-hidden true} [:use {:href (str "#sym-" (name s))}]]))))
