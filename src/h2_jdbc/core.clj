(ns h2-jdbc.core
  "Overwrite the relevant jdbc protocols to ensure that Java 8 data
  types are returned.

  It is assumed that you're using Java 8 and `clojure.java.jdbc` is
  included as a dependency in your project."

  (:require [clojure.java.jdbc :as jdbc])
  (:import [java.time LocalDate LocalTime OffsetDateTime ZoneOffset]))


(defn- h2-timestamp-with-time-zone->offset-date-time
  "Convert a h2 `TimestampWithTimeZone` to a `java.time.OffsetDateTime`.

  This preserves the offset information and is probably the cleanest
  way to map `org.h2.api.TimestampWithTimeZone` to a `java.time` data
  structure.

  As the `h2-timestamp` doesn't give us the time in (hours, minutes,
  seconds) but instead in nanos since midnight, we have to create a
  temporary `LocalTime` instance to convert this to a usable time
  first. That in turn causes us to have to create a `LocalDate` and
  `ZoneOffset` instance to be passed into the constructor of
  `OffsetDateTime`. It could be interesting whether this becomes
  problematic on a larger scale because these temporary instances will
  have to be GC'ed."
  [^org.h2.api.TimestampWithTimeZone h2-timestamp]
  (OffsetDateTime/of
   (LocalDate/of (.getYear h2-timestamp)
                 (.getMonth h2-timestamp)
                 (.getDay h2-timestamp))
   (LocalTime/ofNanoOfDay (.getNanosSinceMidnight h2-timestamp))
   (ZoneOffset/ofTotalSeconds (* 60 (.getTimeZoneOffsetMins h2-timestamp)))))


;; -- Reading ------------------------------------------------------------------

;; extend the jdbc protocol so that values are automatically converted
;; to `java.time` instances on read from db

(extend-protocol jdbc/IResultSetReadColumn
  java.sql.Date
  (result-set-read-column [v _2 _3]
    (.toLocalDate v))

  java.sql.Time
  (result-set-read-column [v _2 _3]
    (.toLocalTime v))

  java.sql.Timestamp
  (result-set-read-column [v _2 _3]
    (.toLocalDateTime v))

  org.h2.api.TimestampWithTimeZone
  (result-set-read-column [v _2 _3]
    (h2-timestamp-with-time-zone->offset-date-time v)))


;; -- Writing ------------------------------------------------------------------

;; while clj-time has to implement this also for writing to the
;; db (https://github.com/clj-time/clj-time/blob/master/src/clj_time/jdbc.clj),
;; we don't have to to this for h2 - just insert `java.time` instances
;; and H2 handles it for us!


;; -- Unneeded, but useful stuff -----------------------------------------------

(comment
  (defn- ^:deprecated h2-timestamp-with-time-zone->instant
    "Convert a h2 `TimestampWithTimeZone` to a `java.time.Instant`.

  This will lose the offset information as an `Instant` doesn't have
  it! Only leaving this here as this was the first solution to the
  problem of converting `org.h2.api.TimestampWithTimeZone` to
  something useful."
    [^org.h2.api.TimestampWithTimeZone h2-timestamp]
    (.toInstant (DateTimeUtils/convertTimestampTimeZoneToTimestamp
                 (.getYMD h2-timestamp)
                 (.getNanosSinceMidnight h2-timestamp)
                 (.getTimeZoneOffsetMins h2-timestamp)))))
