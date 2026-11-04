(ns env.shadow
  "shadow-cljs build hook: loads config.edn for the build's :ttg/profile (default :local, what bb dev wants) before
   compiling. Release builds pick their profile with --config-merge '{:ttg/profile :prod}' (ttg.quality/build-web!)."
  (:require [env.core :as env-core]))

(defn load-env
  {:shadow.build/stages #{:compile-prepare}}
  [{:shadow.build/keys [config] :as build-state}]
  (let [app-env (env-core/set-env! (env-core/read-env (:ttg/profile config :local)))]
    (assoc-in build-state [:compiler-options :external-config ::env] app-env)))
