(ns ttg.ui.dice3d.geometry
  "The d6, d8 and d12 as solids: vertices, and per face its normal, corner order and texture frame.
   Also which face points up for a given orientation, and how to repaint a die so a chosen face lands up.
   Pure math (vectors are [x y z], quaternions [x y z w]), so it runs and is tested under Node.")

(def ^:private phi (/ (+ 1 (js/Math.sqrt 5)) 2))

(defn- v- [a b] (mapv - a b))
(defn dot [a b] (reduce + (map * a b)))
(defn- cross [[ax ay az] [bx by bz]] [(- (* ay bz) (* az by)) (- (* az bx) (* ax bz)) (- (* ax by) (* ay bx))])
(defn- len [a] (js/Math.sqrt (dot a a)))
(defn norm [a] (let [l (len a)] (mapv #(/ % l) a)))

(def ^:private signs (for [i [1 -1] j [1 -1] k [1 -1]] [i j k]))
(defn- pm [f] (mapcat (fn [[i j]] (f i j)) [[1 1] [1 -1] [-1 1] [-1 -1]]))
(defn- cyc [a b] [[0 a b] [a b 0] [b 0 a]])

;; Cube, octahedron and dodecahedron on the unit sphere. radius scales the die; inset is how much of a face
;; the symbols may use (the d8's triangles leave the least room).
(def ^:private solids
  {:d6 {:radius 1.3 :inset 0.707 :verts signs
        :normals [[1 0 0] [-1 0 0] [0 1 0] [0 -1 0] [0 0 1] [0 0 -1]]}
   :d8 {:radius 1.45 :inset 0.5 :verts [[1 0 0] [-1 0 0] [0 1 0] [0 -1 0] [0 0 1] [0 0 -1]] :normals signs}
   :d12 {:radius 1.45 :inset 0.809
         :verts (concat signs (pm #(cyc (/ %1 phi) (* %2 phi))))
         :normals (pm #(cyc (* %1 phi) %2))}})

(defn- on-face
  "Indices of the vertices furthest along n: the corners of the face with that normal."
  [verts n]
  (let [best (apply max (map #(dot % n) verts))]
    (vec (keep-indexed (fn [i v] (when (> (dot v n) (- best 1e-6)) i)) verts))))

(defn- face
  "A face's corners in winding order, its centre c, and an in-plane frame (u, w) for texture coordinates."
  [verts n0]
  (let [n (norm n0)
        idx (on-face verts n)
        c (mapv #(/ % (count idx)) (apply mapv + (map verts idx)))
        u (norm (v- (verts (first idx)) c))
        w (cross n u)
        angle #(let [p (v- (verts %) c)] (js/Math.atan2 (dot p w) (dot p u)))]
    {:n n :idx (vec (sort-by angle idx)) :c c :u u :w w :r (apply max (map #(len (v- (verts %) c)) idx))}))

(defn- build [kind]
  (let [{:keys [radius inset] :as s} (solids kind)
        verts (mapv norm (:verts s))]
    {:kind kind :radius radius :inset inset :verts verts :faces (mapv #(face verts %) (:normals s))}))

(def shape
  "(shape :d8) -> {:verts [...] :faces [{:n :idx :c :u :w :r}] :radius :inset}, unit-sphere vertices."
  (memoize build))

;; ---------- Orientation ----------

(defn rotate
  "Rotates vector v by unit quaternion q."
  [[qx qy qz qw] v]
  (let [t (mapv #(* 2 %) (cross [qx qy qz] v))]
    (mapv + v (mapv #(* qw %) t) (cross [qx qy qz] t))))

(defn up-face
  "Which face points most nearly straight up (+y) in orientation q, and how flat it is:
   {:face i :flatness dot-with-up}. A die lying flat has flatness ~1; one on an edge is well below."
  [{:keys [faces]} q]
  (->> faces
       (map-indexed (fn [i {:keys [n]}] {:face i :flatness ((rotate q n) 1)}))
       (apply max-key :flatness)))

;; ---------- Acting out the engine's roll ----------

(defn face-layout
  "Which engine face to paint on each physical face of an n-sided die so that `landed` shows `chosen`:
   the identity layout with those two swapped. Every engine face still appears exactly once."
  [n landed chosen]
  (-> (vec (range n)) (assoc landed chosen chosen landed)))
