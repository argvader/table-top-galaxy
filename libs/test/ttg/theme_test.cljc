(ns ttg.theme-test
  (:require [clojure.test :refer [deftest is testing]]
            [ttg.theme :as theme]))

(deftest every-palette-stays-readable
  (doseq [{:keys [id] :as p} theme/palettes, mode [:light :dark]
          :let [{:keys [accent ink]} (mode p)]]
    (testing (str id " " mode)
      (is (<= 4.5 (theme/contrast ink accent)) "text on accent buttons")
      (doseq [surface (theme/surfaces mode)]
        (is (<= 4.5 (theme/contrast accent surface)) "accent text on the sheet")))))

(deftest contrast-is-the-wcag-ratio
  (is (< 20.9 (theme/contrast "#000000" "#ffffff") 21.1))
  (is (= 1.0 (theme/contrast "#123456" "#123456"))))

(deftest ids-are-unique-and-the-default-exists
  (is (apply distinct? (map :id theme/palettes)))
  (is (some? (theme/palette theme/default-id))))
