(ns ttg.ui.dice3d.sim
  "Throws dice in a cannon-es world offscreen and records every frame, so a throw is checked (every die must come
   to rest flat) before anyone sees it. The physics only decides how the dice tumble, never what they show.
   No DOM: this runs under Node in the tests."
  (:require ["cannon-es" :as cannon]
            [ttg.ui.dice3d.geometry :as geo]))

(def gravity -60)
(def dt (/ 1 60))
(def max-steps 480)   ; 8 s of simulated time
(def flat 0.97)       ; how squarely a face must point up; below this the die is on an edge or leaning on a wall
(def max-throws 5)
(def ^:private half-pi (/ js/Math.PI 2))

(defn- vec3 [[x y z]] (cannon/Vec3. x y z))

(defn- polyhedron
  "A fresh cannon shape per die: cannon ties a shape to the one body it is added to."
  [kind]
  (let [{:keys [verts faces radius]} (geo/shape kind)]
    (cannon/ConvexPolyhedron. #js {:vertices (into-array (map #(vec3 (mapv (partial * radius) %)) verts))
                                   :faces (clj->js (map :idx faces))})))

(defn- add-plane! [^js world mat [axis angle pos]]
  (let [^js b (cannon/Body. #js {:mass 0 :material mat})]
    (.addShape b (cannon/Plane.))
    (when axis (.setFromAxisAngle (.-quaternion b) (vec3 axis) angle))
    (.set (.-position b) (pos 0) (pos 1) (pos 2))
    (.addBody world b)))

(defn- walls
  "Floor, a low ceiling, and four walls at the edges of the visible table."
  [{:keys [x z]}]
  [[[1 0 0] (- half-pi) [0 0 0]] [[1 0 0] half-pi [0 9 0]]
   [[0 1 0] half-pi [(- x) 0 0]] [[0 1 0] (- half-pi) [x 0 0]]
   [nil 0 [0 0 (- z)]] [[0 1 0] js/Math.PI [0 0 z]]])

(defn- make-world [bounds]
  (let [^js world (cannon/World. #js {:gravity (cannon/Vec3. 0 gravity 0)})
        mat (cannon/Material. "dice")]
    (set! (.-broadphase world) (cannon/NaiveBroadphase.))
    (set! (.. world -solver -iterations) 12)
    (.addContactMaterial world (cannon/ContactMaterial. mat mat #js {:friction 0.35 :restitution 0.35}))
    (doseq [w (walls bounds)] (add-plane! world mat w))
    {:world world :mat mat}))

(defn- start-position
  "Dice start in rows along the near edge, so the throw goes away from the player."
  [i {:keys [x z]}]
  (let [cols (max 1 (js/Math.floor (/ (- (* 2 x) 2) 3)))
        col (mod i cols) row (quot i cols)]
    [(+ (- x) 1.5 (* (+ col 0.5) (/ (- (* 2 x) 3) cols))) (+ 2.5 (* row 1.5)) (- z 1.8 (* row 0.6))]))

(defn- throw-die! [{:keys [^js world mat]} kind i bounds rand]
  (let [^js b (cannon/Body. #js {:mass 1 :material mat :linearDamping 0.12 :angularDamping 0.12})
        [px py pz] (start-position i bounds)
        r #(- (rand) 0.5)]
    (.addShape b (polyhedron kind))
    (.set (.-position b) px py pz)
    (.setFromEuler (.-quaternion b) (* (rand) 6.28) (* (rand) 6.28) (* (rand) 6.28))
    (.set (.-velocity b) (* (r) 10) (+ 2 (* (rand) 4)) (- (+ 22 (* (rand) 14))))
    (.set (.-angularVelocity b) (* (r) 40) (* (r) 40) (* (r) 40))
    (.addBody world b)
    b))

;; ---------- Settling ----------

(defn quaternion [^js b] (let [q (.-quaternion b)] [(.-x q) (.-y q) (.-z q) (.-w q)]))

(defn- calm? [^js b] (and (< (.length (.-velocity b)) 0.12) (< (.length (.-angularVelocity b)) 0.12)))

(defn- rests [kinds bodies] (mapv (fn [k b] (geo/up-face (geo/shape k) (quaternion b))) kinds bodies))

(defn- hop-cocked!
  "Gives each die resting on an edge or against a wall a small hop so it can land flat. True if any hopped."
  [kinds bodies rand]
  (let [cocked (keep (fn [[r b]] (when (< (:flatness r) flat) b)) (map vector (rests kinds bodies) bodies))
        r #(- (rand) 0.5)]
    (doseq [^js b cocked]
      (.set (.-velocity b) (* (r) 4) (+ 6 (* (rand) 3)) (* (r) 4))
      (.set (.-angularVelocity b) (* (r) 12) (* (r) 12) (* (r) 12)))
    (boolean (seq cocked))))

(defn- snapshot
  "Position and orientation of every die for one frame: 7 floats per die (x y z, qx qy qz qw)."
  [bodies]
  (let [a (js/Float32Array. (* 7 (count bodies)))]
    (doseq [[i ^js b] (map-indexed vector bodies)
            :let [p (.-position b) o (* 7 i)]]
      (.set a (into-array (concat [(.-x p) (.-y p) (.-z p)] (quaternion b))) o))
    a))

(defn- damp! [bodies]
  (doseq [^js b bodies] (set! (.-linearDamping b) 0.4) (set! (.-angularDamping b) 0.4)))

(defn- settle!
  "Steps the world until every die has been still for 18 frames (hopping cocked dice up to 3 times), or time runs out.
   Returns the recorded frames."
  [^js world kinds bodies rand]
  (loop [step 0 still 0 hops 0 frames (transient [])]
    (.step world dt)
    (when (= step 132) (damp! bodies))   ; after 2.2 s, so long tumbles wind down
    (let [frames (conj! frames (snapshot bodies))
          still (if (every? calm? bodies) (inc still) 0)]
      (cond
        (and (> still 18) (< hops 3) (hop-cocked! kinds bodies rand)) (recur (inc step) 0 (inc hops) frames)
        (or (> still 18) (>= step max-steps)) (persistent! frames)
        :else (recur (inc step) still hops frames)))))

(defn- attempt [kinds bounds rand]
  (let [{:keys [world] :as w} (make-world bounds)
        bodies (mapv #(throw-die! w %1 %2 bounds rand) kinds (range))
        frames (settle! world kinds bodies rand)
        rested (rests kinds bodies)]
    (when (every? #(>= (:flatness %) flat) rested)
      {:frames frames :landed (mapv :face rested)})))

(defn simulate
  "Throws dice of these kinds (:d6 :d8 :d12) inside walls at +-x and +-z. rand returns a number in [0, 1).
   Returns {:frames [Float32Array per step] :landed [face index per die]} for the first throw where every die
   rests flat, trying up to max-throws times; nil if none does."
  [kinds bounds rand]
  (some (fn [_] (attempt kinds bounds rand)) (range max-throws)))
