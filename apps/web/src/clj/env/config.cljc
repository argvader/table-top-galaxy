(ns ^:dev/always env.config
  "The app's settings from resources/config.edn, read by aero at build time (env.shadow/load-env) and inlined into
   the bundle. ^:dev/always keeps shadow-cljs from caching this namespace, so a new profile or env var always lands."
  #?(:cljs (:require-macros [env.config :as env])))

(def config
  "The settings map for the build's profile."
  #?(:cljs (env/get-config-map)
     :clj  {}))

#?(:clj
   (defmacro get-config-map
     "The config map at compile time, set by env.core/set-env!."
     []
     config))
