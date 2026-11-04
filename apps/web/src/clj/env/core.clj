(ns env.core
  "Reads resources/config.edn with aero for a profile (:local, :dev, :prod) and sets env.config/config."
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [env.config]))

(defn set-env! [app-config]
  (alter-var-root #'env.config/config (constantly app-config))
  app-config)

(defn read-env [release-flag]
  (-> (io/resource "config.edn")
      (aero/read-config {:profile (keyword release-flag) :resolver aero/resource-resolver})
      (assoc :release-flag (name release-flag))))
