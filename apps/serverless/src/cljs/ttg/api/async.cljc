(ns ttg.api.async
  "go-try / <? for core.async in the Lambdas: an error travels down the channel as a value and is rethrown by <?,
   so one try/catch at the handler covers every await beneath it."
  #?(:cljs (:require-macros [ttg.api.async]))
  #?(:cljs (:require [cljs.core.async :as a])))

#?(:cljs
   (defn throw-err [v]
     (if (instance? js/Error v) (throw v) v)))

#?(:cljs
   (defn promise->chan
     "A channel that gets the promise's value, or its rejection as an Error."
     [p]
     (let [ch (a/promise-chan)]
       (.then p #(a/put! ch (if (nil? %) ::nil %))
              #(a/put! ch (if (instance? js/Error %) % (ex-info "Promise rejected" {:reason %}))))
       ch)))

#?(:cljs
   (defn un-nil [v] (when-not (= ::nil v) v)))

#?(:clj
   (defmacro go-try
     "A go block that returns a caught error instead of losing it."
     [& body]
     `(cljs.core.async/go
        (try ~@body
             (catch :default e# e#)))))

#?(:clj
   (defmacro <?
     "Takes from ch inside go-try; rethrows if the value is an error."
     [ch]
     `(throw-err (cljs.core.async/<! ~ch))))

#?(:clj
   (defmacro <p?
     "Awaits a JS promise inside go-try; a rejection is rethrown."
     [p]
     `(un-nil (throw-err (cljs.core.async/<! (promise->chan ~p))))))
