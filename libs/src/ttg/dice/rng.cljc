(ns ttg.dice.rng
  "Random numbers for dice as lazy seqs of unsigned 32-bit ints. `seeded` gives the same seq on the JVM and in Node,
   so a roll can be replayed anywhere from its seed; `secure` is for real rolls."
  #?(:clj (:import (java.security SecureRandom))))

(defn- u32 [x] #?(:clj (bit-and x 0xFFFFFFFF) :cljs (unsigned-bit-shift-right x 0)))

;; Math.imul on the JVM: the low 32 bits of a wrapping 64-bit multiply are the same bits.
(defn- mul32 [a b]
  #?(:clj (u32 (unchecked-multiply (long a) (long b))) :cljs (u32 (js/Math.imul a b))))

(defn- mulberry32
  "Output for one state of mulberry32 (a small, fast PRNG with a 32-bit state)."
  [a]
  (let [t (mul32 (u32 (bit-xor a (unsigned-bit-shift-right a 15))) (bit-or a 1))
        t (u32 (bit-xor (u32 (+ t (mul32 (u32 (bit-xor t (unsigned-bit-shift-right t 7))) (bit-or t 61)))) t))]
    (u32 (bit-xor t (unsigned-bit-shift-right t 14)))))

(defn seeded
  "Deterministic seq of uint32s from an integer seed."
  [seed]
  (map mulberry32 (rest (iterate #(u32 (+ % 0x6D2B79F5)) (u32 seed)))))

#?(:clj (def ^:private ^SecureRandom secure-random (SecureRandom.)))

(defn- secure-u32 []
  #?(:clj (u32 (.nextInt secure-random))
     :cljs (aget (js/crypto.getRandomValues (js/Uint32Array. 1)) 0)))

(defn secure
  "Unpredictable seq of uint32s from the platform's cryptographic RNG."
  []
  (repeatedly secure-u32))

(defn pick
  "Scales a uint32 to an index in [0, n). Exact in both runtimes: u * n stays under 2^53."
  [u n]
  (long (quot (* u n) 4294967296)))
