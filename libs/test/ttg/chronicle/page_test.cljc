(ns ttg.chronicle.page-test
  (:require [clojure.test :refer [deftest is testing]]
            [ttg.chronicle.page :as page]))

(def session "---\ntitle: \"A Debt: Worth Keeping\"\nnumber: 0\nplayed_on: 2026-11-04\n---\n\n# A Debt\n\nBody.\n")

(deftest front-matter
  (is (= {:front {:title "A Debt: Worth Keeping" :number "0" :played_on "2026-11-04"} :body "# A Debt\n\nBody."}
         (page/parse session)))
  (testing "a horizontal rule further down isn't front matter"
    (is (= "One\n\n---\n\nTwo" (:body (page/parse "---\ntitle: T\n---\nOne\n\n---\n\nTwo")))))
  (testing "no front matter"
    (is (= {:front {} :body "Just text"} (page/parse "Just text\n")))))

(deftest entries
  (is (= {:kind "session" :slug "2026-11-04" :title "A Debt: Worth Keeping" :body "# A Debt\n\nBody." :number 0 :played-on "2026-11-04"}
         (page/entry "session" "2026-11-04" session)))
  (is (= {:kind "npc" :slug "imre-vasko" :title "Imre Vasko" :body "# Imre"}
         (page/entry "npc" "imre-vasko" "---\ntitle: Imre Vasko\n---\n# Imre")))
  (testing "a session's date falls back to its file name"
    (is (= "2026-11-04" (:played-on (page/entry "session" "2026-11-04" "---\ntitle: T\n---\nx")))))
  (testing "a page that can't be linked or titled stops the publish"
    (is (thrown? #?(:clj Exception :cljs :default) (page/entry "npc" "Imre Vasko" "---\ntitle: T\n---\nx")))
    (is (thrown? #?(:clj Exception :cljs :default) (page/entry "npc" "imre" "No front matter")))))

(deftest what-a-publish-would-change
  (let [a {:kind "npc" :slug "a" :title "A" :body "1"}
        b {:kind "npc" :slug "b" :title "B" :body "1"}
        c {:kind "pc" :slug "c" :title "C" :body "1"}
        d {:kind "pc" :slug "d" :title "D" :body "1"}
        {:keys [new changed same gone]} (page/changes [a (assoc b :body "2") c] [b c d])]
    (is (= [a] new))
    (is (= [(assoc b :body "2")] changed))
    (is (= [c] same))
    (is (= [d] gone))))
