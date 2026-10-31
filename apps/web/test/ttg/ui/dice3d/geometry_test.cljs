(ns ttg.ui.dice3d.geometry-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [ttg.dice :as dice]
            [ttg.ui.dice :as ui]
            [ttg.ui.dice3d.geometry :as geo]))

(defn- close? [a b] (< (abs (- a b)) 1e-9))

(deftest every-die-type-has-one-physical-face-per-engine-face
  (doseq [t dice/die-types
          :let [s (geo/shape (:shape (ui/die-info t)))]]
    (is (= (count (dice/faces t)) (count (:faces s))) (name t))))

(deftest every-standard-die-has-one-physical-face-per-number
  (doseq [t (concat [:d4 :d6 :d8 :d10 :d20] [:d100-tens :d100-ones])]
    (is (= (ui/face-count t) (count (:faces (geo/shape (:shape (ui/die-info t)))))) (name t))))

(deftest numbered-dice-show-every-number-once
  (is (= ["00" "10" "20" "30" "40" "50" "60" "70" "80" "90"] (map #(ui/face-text :d100-tens %) (range 10))))
  (is (= (map str (range 10)) (map #(ui/face-text :d100-ones %) (range 10))))
  (is (= (map str (range 1 21)) (map #(ui/face-text :d20 %) (range 20)))))

(deftest a-d10s-kites-are-flat
  (doseq [{:keys [idx n]} (:faces (geo/shape :d10))
          :let [verts (:verts (geo/shape :d10)) ds (map #(geo/dot (verts %) n) idx)]]
    (is (= 4 (count idx)))
    (is (< (- (apply max ds) (apply min ds)) 1e-9))))

(deftest a-d4-reads-by-the-corner-pointing-up
  (let [s (geo/shape :d4)]
    (doseq [[i {:keys [idx]}] (map-indexed vector (:faces s))]
      (is (= 3 (count idx)))
      (is (not (some #{i} idx)) "face i is the one opposite vertex i"))
    (testing "the face on the floor counts as flat"
      (let [{:keys [face flatness]} (geo/up-face s [0 0 0 1])]
        (is (> flatness 0.5))
        (is (= face (first (apply min-key #((:n (second %)) 1) (map-indexed vector (:faces s))))))))))

(deftest faces-are-regular-polygons-with-in-plane-texture-frames
  (doseq [[kind corners] {:d4 3 :d6 4 :d8 3 :d12 5 :d20 3}
          {:keys [n idx c u w]} (:faces (geo/shape kind))]
    (is (= corners (count idx)) (name kind))
    (is (close? 1 (geo/dot n n)))
    (is (close? 1 (geo/dot (geo/norm c) n)) "the face centre sits straight out along the normal")
    (is (close? 0 (geo/dot u n)))
    (is (close? 0 (geo/dot w n)))))

(deftest rotation-and-up-face
  (let [s (/ (js/Math.sqrt 2) 2)
        quarter-turn-x [s 0 0 s]]
    (is (every? true? (map close? [0 0 1] (geo/rotate quarter-turn-x [0 1 0]))) "y goes to z"))
  (testing "unrotated, the d6 face with normal +y is up and perfectly flat"
    (let [{:keys [face flatness]} (geo/up-face (geo/shape :d6) [0 0 0 1])]
      (is (= [0 1 0] (:n ((:faces (geo/shape :d6)) face))))
      (is (close? 1 flatness)))))

(def gen-quaternion
  ;; Integer components (gen/choose ignores size) normalised to a unit quaternion; near-zero vectors are skipped.
  (gen/fmap geo/norm (gen/such-that #(> (geo/dot % %) 100) (gen/vector (gen/choose -1000 1000) 4) 100)))

(defspec up-face-is-the-highest-pointing-face 200
  (prop/for-all [kind (gen/elements [:d6 :d8 :d10 :d12 :d20]) q gen-quaternion]
    (let [s (geo/shape kind)
          {:keys [face flatness]} (geo/up-face s q)
          ys (map #((geo/rotate q (:n %)) 1) (:faces s))]
      (and (close? flatness (apply max ys)) (close? flatness (nth ys face))))))

(defspec face-layout-puts-the-chosen-face-up-and-keeps-a-real-die 300
  (prop/for-all [[n landed chosen] (gen/bind (gen/elements [4 6 8 10 12 20])
                                             (fn [n] (gen/tuple (gen/return n) (gen/choose 0 (dec n)) (gen/choose 0 (dec n)))))]
    (let [layout (geo/face-layout n landed chosen)]
      (and (= chosen (layout landed))
           (= (range n) (sort layout))))))
