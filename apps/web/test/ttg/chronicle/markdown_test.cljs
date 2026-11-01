(ns ttg.chronicle.markdown-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [ttg.chronicle.markdown :as md]))

(def ctx {:campaign-id "c1" :kind "npc"})

(defn- render [text] (rest (md/render ctx text)))

(deftest blocks
  (is (= [[:h1 "Title"] [:p "One line and the next."]] (render "# Title\nOne line\nand the next.")))
  (is (= [[:h2 "Key Events"] [:ul [:li "One"] [:li "Two"]] [:hr] [:ol [:li "First"]]]
         (render "## Key Events\n- One\n* Two\n\n---\n\n1. First")))
  (is (= [[:blockquote "Said aloud at the table"]] (render "> Said aloud\n> at the table"))))

(deftest inline-marks
  (is (= [["" [:strong "Bold"] " and " [:em "lean"] " here"]]
         (map #(subvec % 1) (map vec (render "**Bold** and *lean* here")))))
  (testing "a subtitle line that opens with an asterisk isn't a list"
    (is (= :p (ffirst (render "*Session zero · 2026-11-04*"))))))

(deftest links-between-pages
  (let [href #(md/link-href ctx %)]
    (is (= "#/campaigns/c1/chronicle/npc/imre-vasko" (href "../wiki/npcs/imre-vasko.md")))
    (is (= "#/campaigns/c1/chronicle/location/the-concord" (href "../locations/the-concord.md")))
    (is (= "#/campaigns/c1/chronicle/session/2026-11-04" (href "../sessions/2026-11-04.md")))
    (is (= "#/campaigns/c1/chronicle/npc/rook" (href "rook.md")) "same folder keeps the page's kind")
    (is (= "https://example.com/a" (href "https://example.com/a")))))

(deftest what-cannot-be-followed-stays-text
  (is (nil? (md/link-href ctx "javascript:alert(1)")))
  (is (nil? (md/link-href ctx "../images/x.png")))
  (is (nil? (md/link-href ctx "../secrets/x.md")))
  (is (not (str/includes? (pr-str (render "[click](javascript:alert(1))")) ":href"))))

(deftest images
  (is (= [[:img {:src "/portraits/chronicle/c1/sessions/2026-11-04.png" :alt "The Gate"}]]
         (render "![The Gate](../images/sessions/2026-11-04.png)")))
  (testing "only the campaign's own images"
    (is (empty? (render "![x](https://evil.example/x.png)")))
    (is (nil? (md/image-src ctx "../images/sessions/../../x.png")))))

(deftest html-stays-text
  (is (= [[:p "<script>alert(1)</script>"]] (render "<script>alert(1)</script>"))))
