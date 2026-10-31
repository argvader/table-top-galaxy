(ns ttg.api.table
  "The single DynamoDB table, as the services see it. Items are maps with :pk and :sk, optionally :gsi1pk/:gsi1sk
   for the per-user index (\"my characters\", \"my campaigns\"). The gateway implements this over DynamoDB; tests use
   ttg.api.memory. Every method returns a channel holding the result, or an error.")

(defprotocol Table
  (get-item [t pk sk] "The item, or nil.")
  (query [t pk sk-prefix] "Items under pk whose sk starts with sk-prefix, in sk order.")
  (query-index [t gsi1pk sk-prefix] "Items whose gsi1pk is gsi1pk and gsi1sk starts with sk-prefix.")
  (put-item! [t item opts] "Writes one item. opts {:new? true} fails with status 409 if the key is taken.")
  (put-items! [t items] "Writes many items (batched). Yields the count.")
  (delete-items! [t keys] "Deletes [{:pk :sk}] (batched). Missing items are fine. Yields the count."))

(defn now [] (.toISOString (js/Date.)))
