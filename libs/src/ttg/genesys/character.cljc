(ns ttg.genesys.character
  "The saved character. It lives in a jsonb column and travels as EDN, so it has to survive both: JSON turns keyword
   values into strings, and a map key like \"Ranged (Light)\" read back as a keyword can't be printed as EDN.
   So map keys are plain keywords (library ids), and everything else is a string, number, boolean, nil or vector.")

(defn- plain-key? [k]
  (and (keyword? k) (nil? (namespace k)) (some? (re-matches #"[A-Za-z0-9_-]+" (name k)))))

(defn json-safe?
  "True when x comes back unchanged after a trip through JSON with keyword keys."
  [x]
  (cond (map? x) (every? (fn [[k v]] (and (plain-key? k) (json-safe? v))) x)
        (vector? x) (every? json-safe? x)
        :else (or (nil? x) (string? x) (number? x) (boolean? x))))
