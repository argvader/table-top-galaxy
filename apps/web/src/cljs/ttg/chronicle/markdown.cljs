(ns ttg.chronicle.markdown
  "The Markdown the summarizer writes, as hiccup: headings, paragraphs, lists, quotes, rules, images, links, bold and
   italic. Building elements rather than HTML means nothing in a page can run. Pages link each other by file
   (../wiki/npcs/imre-vasko.md), which become routes here; their images are served from the campaign's folder of the
   portrait bucket. A link anywhere else that isn't http(s) stays as plain text. ctx is {:campaign-id :kind}, the page's kind."
  (:require [clojure.string :as str]
            [ttg.chronicle.page :as page]
            [ttg.routes :as routes]))

(def ^:private inline-pattern #"\[([^\]]+)\]\(([^)\s]+)\)|\*\*(.+?)\*\*|\*([^*\s][^*]*)\*")

(defn- page-href [id kind slug] (routes/href {:page :chronicle :id id :kind kind :slug slug}))

(defn link-href
  "Where a link goes, or nil when it can't be followed."
  [{:keys [campaign-id kind]} href]
  (let [[_ folder slug] (re-matches #"(?:\.\./|\./)*(?:wiki/)?(?:([a-z]+)/)?([a-z0-9-]+)\.md" href)]
    (cond (re-matches #"https?://.+" href) href
          (nil? slug) nil
          (nil? folder) (page-href campaign-id kind slug)
          (= "sessions" folder) (page-href campaign-id "session" slug)
          (page/wiki-kinds folder) (page-href campaign-id (page/wiki-kinds folder) slug))))

(defn image-src
  "A page's own image, or nil for one from anywhere else."
  [{:keys [campaign-id]} src]
  (when-let [[_ path] (re-matches #"(?:\.\./)*images/([\w-]+(?:/[\w.-]+)*)" src)]
    (when-not (str/includes? path "..")
      (str "/portraits/chronicle/" campaign-id "/" path))))

(declare inline)

(defn- link [ctx label href]
  (if-let [target (link-href ctx href)]
    (into [:a {:href target}] (inline ctx label))
    label))

(defn inline
  "Text as a list of strings and elements."
  [ctx text]
  (loop [text text out []]
    (if-let [[whole label href strong em] (re-find inline-pattern text)]
      (let [i (str/index-of text whole)]
        (recur (subs text (+ i (count whole)))
               (conj out (subs text 0 i)
                     (cond label (link ctx label href)
                           strong (into [:strong] (inline ctx strong))
                           :else (into [:em] (inline ctx em))))))
      (conj out text))))

(defn- line-kind [line]
  (condp re-matches line
    #"\s*" :blank
    #"#{1,4} .*" :heading
    #"-{3,}\s*" :rule
    #"!\[.*\]\(.*\)\s*" :image
    #"[-*] .*" :list
    #"\d+\. .*" :ordered
    #">.*" :quote
    :text))

(defn- heading [ctx line]
  (let [[_ marks text] (re-matches #"(#+) (.*)" line)]
    (into [(keyword (str "h" (count marks)))] (inline ctx text))))

(defn- image [ctx line]
  (let [[_ alt src] (re-matches #"!\[(.*)\]\((.*)\)\s*" line)]
    (when-let [url (image-src ctx src)] [:img {:src url :alt alt}])))

(defn- items [ctx tag pattern lines]
  (into [tag] (map (fn [line] (into [:li] (inline ctx (str/replace-first line pattern ""))))) lines))

(defn- block [ctx lines]
  (let [text (str/join " " lines)]
    (case (line-kind (first lines))
      :heading (heading ctx (first lines))
      :rule [:hr]
      :image (image ctx (first lines))
      :list (items ctx :ul #"^[-*] " lines)
      :ordered (items ctx :ol #"^\d+\. " lines)
      :quote (into [:blockquote] (inline ctx (str/replace text #"(^|\s)>\s?" "$1")))
      (into [:p] (inline ctx text)))))

(defn- blocks
  "Lines grouped into blocks. Headings, rules and images always stand alone."
  [text]
  (->> (str/split-lines text)
       (partition-by line-kind)
       (remove #(= :blank (line-kind (first %))))
       (mapcat #(if (#{:heading :rule :image} (line-kind (first %))) (map vector %) [%]))))

(defn render [ctx text]
  (into [:div.prose] (keep #(block ctx %)) (blocks text)))
