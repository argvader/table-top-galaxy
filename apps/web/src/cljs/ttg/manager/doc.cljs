(ns ttg.manager.doc
  "The manager's document: pure changes to what the GM records. It's JSON on the server, so its values stay strings;
   only the map keys come back as keywords. The written prep arrives separately as content and seeds a new document."
  (:require [ttg.dice.rng :as rng]))

(defn- seed-s0 [{:keys [dials]}]
  {:done false :answers {} :alt {} :draws {} :truth {} :wrap {} :run {} :collateral {} :lv []
   :dials (into {} (map (juxt (comp keyword :id) :start)) dials)
   :ship {:painted "" :registry "" :hull "" :quirks ""}
   :bid {:backer "" :name "" :stake "" :promise "" :corp ""}})

(defn seed
  "A new campaign's document, before Session 0."
  [{:keys [npcs ledger] :as content}]
  {:v 1 :session 0 :act "ki" :sessions [] :threads {} :twist nil :seats []
   :npcs (mapv #(assoc % :met nil :notes "") npcs)
   :ledger (mapv (fn [l] (-> l (assoc :state "hidden" :revealedIn nil :reveal "")
                             (update :clues (fn [cs] (mapv #(hash-map :t % :s nil) cs)))))
                 ledger)
   :s0 (seed-s0 content)})

(defn fill-in
  "A saved document with anything added since it was written, or a new one if there isn't a usable one."
  [saved content]
  (let [fresh (seed content)]
    (if (= 1 (:v saved))
      (-> (merge fresh saved) (assoc :s0 (merge (:s0 fresh) (:s0 saved))))
      fresh)))

;; ---------- Session 0 ----------

(defn add-answer
  "Adds an empty answer by a PC; a crew answer starts out about the player on their left."
  [doc qid pc about]
  (update-in doc [:s0 :answers qid] (fnil conj []) (cond-> {:pc pc :text "" :follow "" :act ""} about (assoc :about about))))

(defn remove-answer [doc qid i]
  (let [left (into (subvec (get-in doc [:s0 :answers qid]) 0 i) (subvec (get-in doc [:s0 :answers qid]) (inc i)))]
    (if (seq left)
      (assoc-in doc [:s0 :answers qid] left)
      (update-in doc [:s0 :answers] dissoc qid))))

(defn- held
  "Personal questions out of the deck: drawn by someone else, or already answered."
  [{:keys [s0]} pc]
  (into (set (mapcat val (dissoc (:draws s0) (keyword pc))))
        (for [[qid answers] (:answers s0) :when (seq answers)] (name qid))))

(defn deck
  "The personal questions this PC could draw."
  [doc questions pc]
  (let [out (held doc pc)]
    (vec (remove out (map :id (filter #(= "personal" (:set %)) questions))))))

(defn roll
  "Rolls a PC a personal question from a uint32: a d20 that rolls again past any number that's taken, or that they
   just put back. With nothing left to roll, nothing changes."
  [doc questions pc [u]]
  (let [rolled (set (get-in doc [:s0 :draws (keyword pc)]))
        pool (vec (remove rolled (deck doc questions pc)))]
    (cond-> doc
      (seq pool) (assoc-in [:s0 :draws (keyword pc)] [(pool (rng/pick u (count pool)))]))))

(defn take-number
  "Gives a PC the personal question a physical d20 landed on, unless that number's taken."
  [doc questions pc n]
  (let [id (str "p" n)]
    (cond-> doc
      (some #{id} (deck doc questions pc)) (assoc-in [:s0 :draws (keyword pc)] [id]))))

(defn add-line [doc topic kind] (update-in doc [:s0 :lv] conj {:topic topic :kind kind}))

(defn remove-line [doc i]
  (update-in doc [:s0 :lv] (fn [lv] (into (subvec lv 0 i) (subvec lv (inc i))))))

;; ---------- Ledger ----------

(defn- at-id [coll id] (first (keep-indexed #(when (= id (:id %2)) %1) coll)))

(defn set-state
  "Moves a secret between hidden, hinted and revealed; revealing stamps the session."
  [{:keys [session ledger] :as doc} id state]
  (let [i (at-id ledger id) revealed? (= "revealed" state)]
    (-> doc
        (assoc-in [:ledger i :state] state)
        (update-in [:ledger i :revealedIn] #(when revealed? (or % session))))))

(defn plant-clue
  "Plants a clue in the current session, or takes it back. Planting one hints a hidden secret."
  [{:keys [session ledger] :as doc} id k]
  (let [i (at-id ledger id)
        planted? (get-in doc [:ledger i :clues k :s])
        hidden? (= "hidden" (get-in doc [:ledger i :state]))]
    (cond-> (assoc-in doc [:ledger i :clues k :s] (when-not planted? session))
      (and (not planted?) hidden?) (assoc-in [:ledger i :state] "hinted"))))

(defn add-clue [{:keys [ledger] :as doc} id text]
  (update-in doc [:ledger (at-id ledger id) :clues] conj {:t text :s nil}))

;; ---------- Sessions ----------

(defn plan-session
  "Adds a planned session under an act; the drawer opens on it by its number, which is returned as :n in the session."
  [{:keys [sessions] :as doc} act]
  (let [n (inc (reduce max 0 (map :n sessions)))]
    (update doc :sessions conj {:n n :act act :title "" :status "plan" :recap "" :prep "" :choices []})))

(defn start-session [doc n]
  (let [{:keys [act]} (first (filter #(= n (:n %)) (:sessions doc)))]
    (-> doc
        (update :sessions (fn [ss] (mapv #(cond (= n (:n %)) (assoc % :status "now")
                                                 (= "now" (:status %)) (assoc % :status "played")
                                                 :else %)
                                         ss)))
        (assoc :session n :act act)
        (assoc-in [:s0 :done] true))))

(defn end-session [doc n]
  (update doc :sessions (fn [ss] (mapv #(cond-> % (= n (:n %)) (assoc :status "played")) ss))))

(defn delete-session [doc n]
  (update doc :sessions (fn [ss] (filterv #(not= n (:n %)) ss))))

(defn add-choice [doc i text] (update-in doc [:sessions i :choices] conj text))

(defn remove-choice [doc i k]
  (update-in doc [:sessions i :choices] (fn [cs] (into (subvec cs 0 k) (subvec cs (inc k))))))

(defn cycle-thread [doc id]
  (update-in doc [:threads id] {nil "live" "plan" "live" "live" "done" "done" "plan"}))
