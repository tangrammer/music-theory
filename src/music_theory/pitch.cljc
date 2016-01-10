(ns music-theory.pitch
  #?(:cljs (:require-macros music-theory.pitch)))

(defrecord Note [number])

(def ^:private intervals
  {"C" 0, "D" 2, "E" 4, "F" 5, "G" 7, "A" 9, "B" 11})

(defn ->note
  "Creates a Note record, which represents a note as an unbounded MIDI note
   number, from a string or keyword describing the note in scientific pitch
   notation, i.e. a letter and (optionally) any number of sharps and flats.

   e.g. C#5, Dbb4, E0"
  [x]
  (let [s (name x)
        [letter accs octave] (rest (re-matches #"([A-G])([#b]*)(-?\d+)" s))]
    (if (and letter accs octave)
      (let [octave (#?(:clj  Integer/parseInt
                       :cljs js/Number)
                    octave)
            base-note (+ (intervals letter) (* octave 12) 12)]
        (->Note (reduce (fn [note-number accidental]
                          (case accidental
                            \# (inc note-number)
                            \b (dec note-number)))
                        base-note
                        accs)))
      (throw (new #?(:clj  Exception
                     :cljs js/Error)
                  "Invalid note format.")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *reference-pitch* 440)
(def ^:dynamic *tuning-system* :equal)
(def ^:dynamic *tonic* nil)
(def ^:dynamic *scale-type* :major)

(defn set-reference-pitch!
  "Changes the reference pitch, which is the frequency of A4. (default: 440)"
  [freq]
  #?(:clj  (alter-var-root #'*reference-pitch* (constantly freq))
     :cljs (set! *reference-pitch* freq)))

#?(:clj
(defmacro with-reference-pitch
  "Executes the body, with *reference-pitch* bound to a given frequency."
  [freq & body]
  `(binding [*reference-pitch* ~freq]
     ~@body)))

(defn set-key!
  "Sets the key, which is required by some tuning systems in order to calculate
   the frequency of a note in Hz."
  ([tonic]
    (set-key! tonic :major))
  ([tonic scale-type]
    #?(:clj  (alter-var-root #'*tonic* (constantly tonic))
       :cljs (set! *tonic* tonic))
    #?(:clj  (alter-var-root #'*scale-type* (constantly scale-type))
       :cljs (set! *scale-type* scale-type))))

#?(:clj
(defmacro with-key
  "Executes the body, with *tonic* and *scale-type* bound to those provided."
  [tonic scale-type & body]
  `(binding [*tonic*      ~tonic
             *scale-type* ~scale-type]
     ~@body)))

(defn set-tuning-system!
  "Changes the tuning system. (default: :equal)"
  [system]
  #?(:clj  (alter-var-root #'*tuning-system* (constantly system))
     :cljs (set! *tuning-system* system)))

#?(:clj
(defmacro with-tuning-system
  "Executes the body, with *tuning-system* bound to a given tuning system.

   Some tuning systems need to be aware of what key you're in. This can be
   done via `set-key!` or via the `with-key` macro."
  [tuning & body]
  `(binding [*tuning-system* ~tuning]
     ~@body)))

#?(:clj
(defmacro with-tuning
  "Executes the body, binding *tuning-system* and/or *reference-pitch* to a
   particular tuning system and/or reference pitch.

   The first argument can be either a number (representing a reference pitch),
   a keyword (representing a tuning system), or a collection containing both."
  [x & body]
  `(let [[rp# ts#] (cond
                     (number? ~x)  [~x *tuning-system*]
                     (keyword? ~x) [*reference-pitch* ~x]
                     (coll? ~x) [(or (first (filter number? ~x))
                                     *reference-pitch*)
                                 (or (first (filter keyword? ~x))
                                     *tuning-system*)])]
     (binding [*reference-pitch* rp#
               *tuning-system* ts#]
       ~@body))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn midi->hz
  "Converts a MIDI note (0-127) to its frequency in Hz.

   Reference pitch is A440 by default. To calculate pitch based on an alternate
   reference pitch (e.g. A430), bind *reference-pitch* to the frequency of A4."
  [midi-note]
  {:pre  [(integer? midi-note)]
   :post [(not (neg? %))]}
  (case *tuning-system*
    :equal
    (* *reference-pitch* (Math/pow 2 (/ (- midi-note 69) 12.0)))))

(defn hz->midi
  "Converts a frequency in Hz to the closest MIDI note.

   Reference pitch is A440 by default. To calculate pitch based on an alternate
   reference pitch (e.g. A430), bind *reference-pitch* to the frequency of A4."
  [freq]
  {:pre  [(number? freq) (pos? freq)]
   :post [(not (neg? %))]}
  (case *tuning-system*
    :equal
    (letfn [(log2 [n] (/ (Math/log n) (Math/log 2)))]
      (Math/round (+ 69 (* 12 (log2 (/ freq *reference-pitch*))))))))

(defn note->midi
  "Converts a note in the form of a string or keyword (e.g. C#4, :Db5, A2) into
   the corresponding MIDI note number.

   Throws an assertion error if the note is outside the range of MIDI notes
   (0-127)."
  [note]
  {:post [(<= 0 % 127)]}
  (:number (->note note)))

(defn note->hz
  "Converts a note in the form of a string or keyword (e.g. C#4, :Db5, A2) into
   its frequency in hz.

   Reference pitch is A440 by default. To calculate pitch based on an alternate
   reference pitch (e.g. A430), bind *reference-pitch* to the frequency of A4."
  [note]
  {:post [(not (neg? %))]}
  (-> (->note note) :number midi->hz))
