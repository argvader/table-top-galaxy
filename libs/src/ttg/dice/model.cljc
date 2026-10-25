(ns ttg.dice.model
  "Specs for dice pools and roll results: the shapes that cross the wire between the roller, the API and the roll feed."
  (:require [clojure.spec.alpha :as s]
            [ttg.dice :as dice]))

(s/def ::die-type (set dice/die-types))
(s/def ::pool (s/map-of ::die-type nat-int?))

(s/def ::symbol #{:success :failure :advantage :threat :triumph :despair})
(s/def ::type ::die-type)
(s/def ::face nat-int?)
(s/def ::symbols (s/coll-of ::symbol :kind vector?))
(s/def ::die (s/and (s/keys :req-un [::type ::face ::symbols])
                    #(= (:symbols %) (get-in dice/faces [(:type %) (:face %)]))))
(s/def ::dice (s/coll-of ::die :kind vector?))

(s/def ::success? boolean?)
(s/def ::net-success int?)
(s/def ::net-advantage int?)
(s/def ::triumph nat-int?)
(s/def ::despair nat-int?)
(s/def ::result (s/keys :req-un [::success? ::net-success ::net-advantage ::triumph ::despair]))

(s/def ::roll (s/keys :req-un [::pool ::dice ::result]))
