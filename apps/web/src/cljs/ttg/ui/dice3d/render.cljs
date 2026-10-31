(ns ttg.ui.dice3d.render
  "Draws dice over the page with three.js and replays a throw the sim recorded. Each die is painted by face-layout,
   so the face that lands up carries the engine's face. The canvas never takes clicks; it fades once the dice rest."
  (:require ["three" :as three]
            [ttg.dice :as dice]
            [ttg.ui.dice :as ui]
            [ttg.ui.dice3d.geometry :as geo]))

(def px-per-unit 34)   ; one world unit is this many pixels on any screen
(def fov 30)
(def fade-after 4500)
(def ^:private frame-ms (/ 1000 60))

;; The renderer, scene and the dice on screen: true runtime state, created on the first throw.
(defonce ^:private state (atom nil))

;; ---------- Scene ----------

(defn- canvas! []
  (doto (js/document.createElement "canvas")
    (.setAttribute "class" "dice3d")
    (.setAttribute "aria-hidden" "true")
    (->> (.appendChild js/document.body))))

(defn- renderer [canvas]
  (try
    (doto (three/WebGLRenderer. #js {:canvas canvas :alpha true :antialias true})
      (.setPixelRatio (min (or js/window.devicePixelRatio 1) 2))
      (-> .-shadowMap .-enabled (set! true)))
    (catch :default _ nil)))

(defn- sun []
  ;; three r155+ uses physical light units; x pi keeps the prototype's brightness.
  (let [^js light (three/DirectionalLight. 0xffffff (* 0.75 js/Math.PI))
        ^js cam (.. light -shadow -camera)]
    (.set (.-position light) -8 30 10)
    (set! (.-castShadow light) true)
    (set! (.-left cam) -40) (set! (.-right cam) 40) (set! (.-top cam) 40) (set! (.-bottom cam) -40)
    (.set (.. light -shadow -mapSize) 1024 1024)
    (.updateProjectionMatrix cam)
    light))

(defn- floor []
  (doto (three/Mesh. (three/PlaneGeometry. 200 200) (three/ShadowMaterial. #js {:opacity 0.22}))
    (-> .-rotation .-x (set! (/ js/Math.PI -2)))
    (-> .-receiveShadow (set! true))))

(defn- scene []
  (doto (three/Scene.)
    (.add (three/HemisphereLight. 0xffffff 0x445566 (* 0.75 js/Math.PI)))
    (.add (sun))
    (.add (floor))))

(defn- fit!
  "Sizes the canvas to the window and looks straight down; returns the walls {:x :z} at the window edges."
  [^js r ^js camera]
  (let [w js/window.innerWidth h js/window.innerHeight
        half-z (/ h 2 px-per-unit)
        height (/ half-z (js/Math.tan (* (/ fov 2) (/ js/Math.PI 180))))]
    (.setSize r w h false)
    (set! (.-aspect camera) (/ w h))
    (.set (.-position camera) 0 height 0)
    (.set (.-up camera) 0 0 -1)
    (.lookAt camera 0 0 0)
    (.updateProjectionMatrix camera)
    {:x (- (* half-z (/ w h)) 0.4) :z (- half-z 0.4)}))

(defn init!
  "Creates the overlay on first use. Returns false when WebGL isn't available."
  []
  (or (some? @state)
      (let [canvas (canvas!)]
        (if-let [r (renderer canvas)]
          (do (reset! state {:canvas canvas :renderer r :scene (scene) :camera (three/PerspectiveCamera. fov 1 0.1 1000)
                             :meshes [] :raf 0 :fade 0})
              true)
          (do (.remove canvas) false)))))

(defn bounds
  "Refits to the current window and returns the walls for the sim."
  []
  (let [{:keys [renderer camera]} @state] (fit! renderer camera)))

;; ---------- Dice ----------

(defn- face-triangles
  "Fan triangles for one face: positions scaled by radius, the face normal, and UVs in the face's own frame."
  [{:keys [verts radius]} {:keys [idx n c u w r]}]
  (for [k (range 1 (dec (count idx))) vi [(idx 0) (idx k) (idx (inc k))]
        :let [v (verts vi) p (mapv - v c)]]
    {:pos (mapv #(* radius %) v) :n n :uv [(+ 0.5 (/ (* 0.5 (geo/dot p u)) r)) (+ 0.5 (/ (* 0.5 (geo/dot p w)) r))]}))

(defn- attribute [corners k size] (three/Float32BufferAttribute. (clj->js (mapcat k corners)) size))

(defn- build-geometry [kind]
  (let [s (geo/shape kind)
        per-face (mapv #(face-triangles s %) (:faces s))
        corners (apply concat per-face)
        ^js g (three/BufferGeometry.)]
    (.setAttribute g "position" (attribute corners :pos 3))
    (.setAttribute g "normal" (attribute corners :n 3))
    (.setAttribute g "uv" (attribute corners :uv 2))
    (reduce (fn [start [i tris]] (.addGroup g start (count tris) i) (+ start (count tris))) 0 (map-indexed vector per-face))
    g))

(def ^:private geometry (memoize build-geometry))

(defn- paint-glyph! [^js g {:keys [ring d]} cx cy s ink]
  (.save g)
  (.translate g (- cx (/ s 2)) (- cy (/ s 2)))
  (.scale g (/ s 24) (/ s 24))
  (set! (.-fillStyle g) ink) (set! (.-strokeStyle g) ink) (set! (.-lineWidth g) 2.4)
  (when ring (.beginPath g) (.arc g 12 12 10 0 (* 2 js/Math.PI)) (.stroke g))
  (.fill g (js/Path2D. d))
  (.restore g))

(def ^:private size 256)

(defn- paint-face!
  "A 256px face in the die's colour, handed to paint for its ink."
  [type paint]
  (let [cv (doto (js/document.createElement "canvas") (-> .-width (set! size)) (-> .-height (set! size)))
        ^js g (.getContext cv "2d")
        {:keys [hex ink]} (ui/die-info type)]
    (set! (.-fillStyle g) hex) (.fillRect g 0 0 size size)
    (paint g ink)
    (doto (three/CanvasTexture. cv) (-> .-anisotropy (set! 4)) (-> .-colorSpace (set! three/SRGBColorSpace)))))

(defn- build-texture
  "A face of a narrative die: its symbols in ink, one centred or two side by side."
  [type face inset]
  (let [syms (get-in dice/faces [type face])
        room (* size 0.5 inset 0.8)
        s (if (> (count syms) 1) (* room 0.95) (* room 1.35))]
    (paint-face! type
                 (fn [g ink]
                   (doseq [[i sym] (map-indexed vector syms)
                           :let [dx (if (> (count syms) 1) (if (zero? i) (- (/ s 2)) (/ s 2)) 0)]]
                     (paint-glyph! g (ui/glyph-paths sym) (+ (/ size 2) dx) (/ size 2) s ink))))))

(def ^:private texture (memoize build-texture))

(defn- paint-number!
  "A number centred on x y, turned so its top points along angle. 6 and 9 are underlined so they can be told apart."
  [^js g text x y px angle ink]
  (.save g)
  (.translate g x y)
  (.rotate g angle)
  (set! (.-font g) (str "700 " px "px sans-serif"))
  (set! (.-textAlign g) "center") (set! (.-textBaseline g) "middle")
  (set! (.-fillStyle g) ink)
  (.fillText g text 0 0)
  (when (#{"6" "9"} text) (.fillRect g (* px -0.28) (* px 0.46) (* px 0.56) (* px 0.08)))
  (.restore g))

(defn- build-number-texture [type text inset]
  (let [centre (/ size 2)]
    (paint-face! type (fn [g ink] (paint-number! g text centre centre (* size inset 0.62) 0 ink)))))

(def ^:private number-texture (memoize build-number-texture))

(defn- build-corner-texture
  "A d4 face: a number in each corner with its top towards the corner, which is how a d4 reads from above."
  [type corners]
  (paint-face! type
               (fn [g ink]
                 (doseq [[text dx dy] corners
                         :let [x (+ (/ size 2) (* dx size 0.3)) y (+ (/ size 2) (* dy size 0.3))]]
                   (paint-number! g text x y 58 (js/Math.atan2 dx (- dy)) ink)))))

(def ^:private corner-texture (memoize build-corner-texture))

(defn- corners
  "Each corner of a d4 face as [text dx dy]: its vertex's number and the way to it from the middle of the texture."
  [{:keys [verts]} {:keys [idx c u w r]} type layout]
  (for [vi idx :let [p (mapv - (verts vi) c)]]
    [(ui/face-text type (layout vi)) (/ (geo/dot p u) r) (- (/ (geo/dot p w) r))]))

(defn- face-texture
  "The texture for physical face i, or on a d4, for the three vertices around it."
  [type kind layout i]
  (let [{:keys [inset faces] :as s} (geo/shape kind)]
    (cond
      (= kind :d4) (corner-texture type (vec (corners s (faces i) type layout)))
      (:mark (ui/die-info type)) (number-texture type (ui/face-text type (layout i)) inset)
      :else (texture type (layout i) inset))))

(defn- material [tex]
  (three/MeshStandardMaterial. #js {:map tex :roughness 0.42 :metalness 0.05}))

(defn- mesh
  "A die whose physical face i shows engine face (layout i)."
  [type kind layout]
  (doto (three/Mesh. (geometry kind) (into-array (map #(material (face-texture type kind layout %)) (range (count layout)))))
    (-> .-castShadow (set! true))))

;; ---------- Playback ----------

(defn- dispose! [^js m] (doseq [^js mat (.-material m)] (.dispose mat)))

(defn clear!
  "Removes the last throw's dice and stops any playback."
  []
  (when-let [{:keys [^js scene meshes raf fade ^js canvas ^js renderer camera]} @state]
    (js/cancelAnimationFrame raf)
    (js/clearTimeout fade)
    (doseq [m meshes] (.remove scene m) (dispose! m))
    (.remove (.-classList canvas) "fade")
    (.render renderer scene camera)
    (swap! state assoc :meshes [] :raf 0)))

(defn- pose! [meshes ^js frame]
  (doseq [[i ^js m] (map-indexed vector meshes) :let [o (* 7 i)]]
    (.set (.-position m) (aget frame o) (aget frame (+ o 1)) (aget frame (+ o 2)))
    (.set (.-quaternion m) (aget frame (+ o 3)) (aget frame (+ o 4)) (aget frame (+ o 5)) (aget frame (+ o 6)))))

(defn- finish! [on-done]
  (let [^js canvas (:canvas @state)]
    (swap! state assoc :raf 0 :fade (js/setTimeout #(.add (.-classList canvas) "fade") fade-after))
    (on-done)))

(defn- tick! [frames started on-done now]
  (let [{:keys [meshes renderer scene camera]} @state
        i (min (dec (count frames)) (js/Math.floor (/ (- now started) frame-ms)))]
    (pose! meshes (frames i))
    (.render ^js renderer scene camera)
    (if (= i (dec (count frames)))
      (finish! on-done)
      (swap! state assoc :raf (js/requestAnimationFrame #(tick! frames started on-done %))))))

(defn play!
  "Shows dice [{:type :kind :layout}] following frames, then calls on-done once they rest."
  [dice frames on-done]
  (clear!)
  (let [meshes (mapv (fn [{:keys [type kind layout]}] (mesh type kind layout)) dice)]
    (doseq [m meshes] (.add ^js (:scene @state) m))
    (pose! meshes (first frames))
    (swap! state assoc :meshes meshes
           :raf (js/requestAnimationFrame (fn [now] (tick! frames now on-done now))))))
