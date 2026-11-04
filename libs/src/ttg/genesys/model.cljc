(ns ttg.genesys.model
  "Specs for Genesys content entries (archetypes, careers, talents, ...).
   Every entry carries :source so the app can cite the book and page it came from."
  (:require [clojure.spec.alpha :as s]))

(def characteristics [:brawn :agility :intellect :cunning :willpower :presence])

(s/def ::id keyword?)
(s/def ::name (s/and string? seq))
(s/def ::text string?)
(s/def ::book #{"crb" "epg" "sotb" "sotb-guns" "woa" "house" "custom"})
(s/def ::page pos-int?)
(s/def ::doc string?)
;; Book entries cite a page; house rules cite the section of docs/house-rules.md they came from.
;; Entries a GM writes in the library editor are "custom" and cite nothing.
(s/def ::source (s/and (s/keys :req-un [::book] :opt-un [::page ::doc]) #(or (:page %) (:doc %) (= "custom" (:book %)))))

(s/def ::rating (s/int-in 1 7))
(s/def ::characteristics (s/map-of (set characteristics) ::rating :count 6))
(s/def ::base nat-int?)
(s/def ::wounds ::base)
(s/def ::strain ::base)
(s/def ::xp nat-int?)
(s/def ::ability (s/keys :req-un [::name ::text]))
(s/def ::abilities (s/coll-of ::ability :kind vector?))
(s/def ::starting-skills string?)
(s/def ::ranks pos-int?)
(s/def ::choose pos-int?)
(s/def ::non-career boolean?)
(s/def :ttg.genesys.model.free-skills/skills (s/coll-of string? :kind vector?))
(s/def ::free-skills (s/keys :req-un [::ranks] :opt-un [::choose ::non-career :ttg.genesys.model.free-skills/skills]))
(s/def ::max-price pos-int?)
(s/def ::free-item (s/keys :req-un [::group ::max-price]))
(s/def ::archetype
  (s/keys :req-un [::id ::name ::characteristics ::wounds ::strain ::xp ::abilities ::source]
          :opt-un [::starting-skills ::text ::free-skills ::free-item]))

(s/def ::characteristic (set characteristics))
(s/def ::category #{"Social" "General" "Knowledge" "Combat" "Magic"})
(s/def ::variant-of keyword?)
(s/def ::use-when (s/coll-of string? :kind vector?))
(s/def ::avoid-when (s/coll-of string? :kind vector?))
(s/def ::skill-entry (s/keys :req-un [::id ::name ::characteristic ::category ::source] :opt-un [::text ::variant-of ::use-when ::avoid-when]))

(s/def ::skills (s/coll-of ::name :kind vector? :count 8))
(s/def ::useful-talents (s/coll-of (s/keys :req-un [::id ::name ::tier]) :kind vector?))
(s/def ::career (s/keys :req-un [::id ::name ::skills ::source] :opt-un [::text ::useful-talents]))

(s/def ::tier (s/int-in 1 6))
(s/def ::activation string?)
(s/def ::ranked boolean?)
(s/def ::settings string?)
(s/def ::talent (s/keys :req-un [::id ::name ::tier ::activation ::ranked ::text ::source] :opt-un [::settings]))

(s/def ::kind #{:secret :obligation})
(s/def ::roll (s/and (s/tuple (s/int-in 1 101) (s/int-in 1 101)) (fn [[lo hi]] (<= lo hi))))
(s/def ::obligation-type (s/keys :req-un [::id ::name ::kind ::roll ::text ::source]))

;; ---------- Equipment ----------
(s/def ::setting string?)
(s/def ::group string?)
(s/def ::rating pos-int?)
(s/def ::quality (s/keys :req-un [::name] :opt-un [::rating]))
(s/def ::qualities (s/coll-of ::quality :kind vector?))
(s/def ::encumbrance (s/nilable int?))
(s/def ::price (s/nilable nat-int?))
(s/def ::rarity (s/nilable (s/int-in 0 11)))
(s/def ::restricted boolean?)
(s/def ::crit (s/nilable nat-int?))
(s/def ::damage (s/nilable nat-int?))
(s/def ::damage-bonus nat-int?)
(s/def ::adds-brawn boolean?)
(s/def ::encumbrance-bonus pos-int?)
(s/def ::hard-points nat-int?)
(s/def ::range string?)
(s/def ::skill string?)
(s/def ::defense nat-int?)
(s/def ::soak nat-int?)
(s/def ::weapon (s/and (s/keys :req-un [::id ::name ::setting ::skill ::crit ::range ::qualities ::price ::rarity ::source]
                               :opt-un [::damage ::damage-bonus ::adds-brawn ::encumbrance ::group ::restricted ::hard-points ::text])
                       #(or (contains? % :damage) (contains? % :damage-bonus))))
(s/def ::armor (s/keys :req-un [::id ::name ::setting ::defense ::soak ::price ::rarity ::source] :opt-un [::encumbrance ::restricted ::hard-points ::group ::text]))
(s/def ::attachment (s/keys :req-un [::id ::name ::setting ::price ::rarity ::source] :opt-un [::hard-points ::group ::restricted]))
(s/def ::gear (s/keys :req-un [::id ::name ::setting ::price ::rarity ::source] :opt-un [::encumbrance ::encumbrance-bonus ::damage ::group ::restricted ::text]))

;; ---------- Item qualities and critical injuries ----------
(s/def ::item-quality (s/keys :req-un [::id ::name ::activation ::ranked ::text ::source]))
(s/def ::severity (s/int-in 0 5))
(s/def ::crit-roll (s/tuple pos-int? (s/nilable pos-int?)))
(s/def ::crit-injury (s/and (s/keys :req-un [::id ::name ::severity ::text ::source]) #(s/valid? ::crit-roll (:roll %))))

;; ---------- Motivations ----------
(s/def :ttg.genesys.model.motivation/type #{"Desire" "Fear" "Strength" "Flaw"})
(s/def ::motivation (s/keys :req-un [::id :ttg.genesys.model.motivation/type ::name ::text ::source]))

;; A setting's allowed-talent list entry: where the talent is defined, not the talent itself.
(s/def ::defined-in (s/keys :req-un [::book] :opt-un [::page]))
(s/def ::talent-ref (s/keys :req-un [::id ::name ::tier ::ranked ::defined-in ::source]))

(s/def ::skill-ref (s/keys :req-un [::id ::name ::characteristic ::category ::defined-in ::source]))

(s/def ::side #{:positive :negative})
(s/def ::cost map?)
(s/def ::spend-result (s/keys :req-un [::id ::name ::side ::cost ::source]))

(s/def :ttg.genesys.model.network/kind #{:action :maneuver :rule :ice :icebreaker})
(s/def ::strength nat-int?)
(s/def ::available-to (s/coll-of string? :kind vector?))
(s/def ::difficulty string?)
(s/def ::network-entry (s/and (s/keys :req-un [::id ::name :ttg.genesys.model.network/kind ::text ::source])
                              #(or (#{:action :maneuver :rule} (:kind %)) (s/valid? ::strength (:strength %)))))
(s/def :ttg.genesys.model.network-result/table #{:spend :trace})
(s/def ::network-result (s/keys :req-un [::id ::name :ttg.genesys.model.network-result/table ::source]))

(s/def :ttg.genesys.model.section/title string?)
(s/def ::sections (s/coll-of (s/keys :req-un [:ttg.genesys.model.section/title ::text]) :kind vector?))
(s/def ::faction (s/keys :req-un [::id ::name ::text ::sections ::source]))

(s/def :ttg.genesys.model.adversary/type #{"minion" "rival" "nemesis"})
(s/def :ttg.genesys.model.adversary/characteristics (s/map-of string? nat-int? :count 6))
(s/def ::soak (s/nilable nat-int?))
(s/def ::adversary (s/keys :req-un [::id ::name :ttg.genesys.model.adversary/type :ttg.genesys.model.adversary/characteristics ::soak ::source]))

(s/def ::silhouette int?)
(s/def ::control-skill string?)
(s/def ::vehicle (s/keys :req-un [::id ::name ::silhouette ::control-skill ::source]))

(s/def ::tone (s/keys :req-un [::id ::name ::sections ::source]))

(def entry-specs {:tones ::tone :vehicles ::vehicle :adversaries ::adversary :factions ::faction :network ::network-entry :network-results ::network-result :spend-results ::spend-result :skill-list ::skill-ref :talent-list ::talent-ref :motivations ::motivation :item-qualities ::item-quality :crits ::crit-injury :attachments ::attachment :weapons ::weapon :armor ::armor :gear ::gear :skills ::skill-entry :archetypes ::archetype :careers ::career :talents ::talent :obligation-types ::obligation-type})
