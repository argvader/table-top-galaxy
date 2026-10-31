(ns ttg.ui.portrait
  "Portraits: shown from /portraits/... with initials as the fallback, and prepared in the browser for upload."
  (:require [ttg.genesys.rules :as rules]))

(def ^:private size 512)
(def ^:private quality 0.85)

(defn src [portrait-key] (str "/" portrait-key))

(defn face
  "A round portrait, or the character's initials when they have none."
  [{:keys [portrait-key name]}]
  [:span.face
   (if portrait-key
     [:img {:src (src portrait-key) :alt (str "Portrait of " name)}]
     [:span {:aria-hidden true} (rules/initials name)])])

(defn- draw-square!
  "Center-crops the image onto a square canvas."
  [^js image]
  (let [canvas (js/document.createElement "canvas")
        w (.-naturalWidth image) h (.-naturalHeight image)
        side (min w h)]
    (set! (.-width canvas) size)
    (set! (.-height canvas) size)
    (.drawImage (.getContext canvas "2d") image (/ (- w side) 2) (/ (- h side) 2) side side 0 0 size size)
    canvas))

(defn- load-image [file]
  (js/Promise.
   (fn [resolve reject]
     (let [url (js/URL.createObjectURL file) image (js/Image.)]
       (set! (.-onload image) (fn [] (js/URL.revokeObjectURL url) (resolve image)))
       (set! (.-onerror image) (fn [] (js/URL.revokeObjectURL url) (reject (js/Error. "That file isn't a picture"))))
       (set! (.-src image) url)))))

(defn prepare
  "A promise of the file as a 512px square JPEG, base64. Whatever was uploaded, the server only ever sees this."
  [file]
  (.then (load-image file)
         (fn [image]
           (subs (.toDataURL (draw-square! image) "image/jpeg" quality) (count "data:image/jpeg;base64,")))))
