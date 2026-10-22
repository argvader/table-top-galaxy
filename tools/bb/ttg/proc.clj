(ns ttg.proc
  "Background processes tracked by pid files under .local/pids."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def local-dir ".local")

(defn- pid-file [svc] (fs/path local-dir "pids" (str (name svc) ".pid")))
(defn log-file [svc] (str (fs/path local-dir "logs" (str (name svc) ".log"))))

(defn pid [svc]
  (let [f (pid-file svc)]
    (when (fs/exists? f)
      (parse-long (str/trim (slurp (str f)))))))

(defn alive? [pid-num]
  (boolean (some-> pid-num (java.lang.ProcessHandle/of) (.orElse nil) (.isAlive))))

(defn running? [svc] (alive? (pid svc)))

(defn start!
  "Starts cmd detached from this bb process, logging to .local/logs/<svc>.log."
  [svc cmd]
  (fs/create-dirs (fs/parent (pid-file svc)))
  (fs/create-dirs (fs/parent (log-file svc)))
  (let [log  (fs/file (log-file svc))
        proc (apply p/process {:out log :err log :in nil} "nohup" cmd)
        n    (.pid ^Process (:proc proc))]
    (spit (str (pid-file svc)) n)
    n))

(defn stop!
  "Stops svc if running. Safe to call when it is already stopped."
  [svc]
  (when-let [n (pid svc)]
    (when-let [h (.orElse (java.lang.ProcessHandle/of n) nil)]
      (.destroy h)
      (.get (.onExit h) 10 java.util.concurrent.TimeUnit/SECONDS))
    (fs/delete-if-exists (pid-file svc))
    n))
