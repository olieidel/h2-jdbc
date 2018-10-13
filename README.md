``` clojure
[io.eidel/h2-jdbc "1.0.0"]
```

# h2-jdbc

Extend JDBC protocols for the H2 Database to return Java 8
objects. Similar to [clj-time.jdbc], only specific for H2 and
`java.time`.

## Usage

Require `h2-jdbc.core` somewhere in your project like so:

``` clojure
;; somewhere in your code
(require 'h2-jdbc.core)

;; or at the top of your ns
(ns foobar.core
  (:require h2-jdbc.core))
```

Done!

## Wait, what?

You'll run into some surprises when using the H2 database from
Clojure, especially when using the `DATE`, `TIME`, `TIMESTAMP` and
`TIMESTAMP WITH TIME ZONE` data types.

Let's set up an example database and have a look.

## Example Setup

``` clojure
;; require JDBC
(require '[clojure.java.jdbc :as jdbc])

;; define an H2 in-memory db spec
(def  db-spec
  "The database spec, needed for connecting to the H2 DB. Let's use an
  in-memory DB for our purposes here."
  {:classname   "org.h2.Driver"
   ;; use an in-memory db
   :subprotocol "h2:mem"
   ;; set `DB_CLOSE_DELAY`, otherwise it disappears after each query
   :subname     "test_db;DB_CLOSE_DELAY=-1"
   :user        "sa"
   :password    ""})
```

### Timestamp with Time Zone

Now let's create a very simple table with only one column. The data
type shall be `TIMESTAMP WITH TIME ZONE`:

``` clojure
(jdbc/execute!
  db-spec
  "CREATE TABLE timestamp_table (timestamp TIMESTAMP WITH TIME ZONE)")
;; => [0]
;; ^ this pretty much means "success"
```

Now, let's insert a value. For that, we have to import Java 8's
`OffsetDateTime` first. We'll insert the current `OffsetDateTime`
value by calling `OffsetDateTime.now()`. This works because H2
supports Java 8 time objects:

``` clojure
(import [java.time OffsetDateTime])

(jdbc/insert!
  db-spec
  "timestamp_table"
  {:timestamp (OffsetDateTime/now)})
;; => (nil)
;; ^ "success"
```

Now, let's read it from the db by querying it:

``` clojure
(-> (jdbc/query db-spec "SELECT * FROM timestamp_table")
    first
    :timestamp)
;; => #object[org.h2.api.TimestampWithTimeZone 0x569567ed "2018-10-13 21:28:54.401+02"]
;; huh? what's this?
```

Wait, what? We inserted a `java.time.OffsetDateTime` and now we get an
obscure `org.h2.api.TimestampWithTimeZone`?

This makes things difficult. How do we convert that back to something
usable? In this case, we'd like to have an `OffsetDateTime` again.

Turns out it's not entirely trivial and solving this took me more than
an evening, so let me save yours - `h2-jdbc` to the rescue!

``` clojure
;; before:
(-> (jdbc/query db-spec "SELECT * FROM timestamp_table")
    first
    :timestamp)
;; => #object[org.h2.api.TimestampWithTimeZone 0x569567ed "2018-10-13 21:28:54.401+02"]
;; now that's not very useful

;; now, let's require h2-jdbc:
(require 'h2-jdbc.core)

;; after:
(-> (jdbc/query db-spec "SELECT * FROM timestamp_table")
    first
    :timestamp)
;; => #object[java.time.OffsetDateTime 0x10abf87d "2018-10-13T21:28:54.401+02:00"]
;; much better!
```

Nice! We're getting back an `OffsetDateTime` because `h2-jdbc` extends
the relevant `clojure.java.jdbc` protocol for us.

That's all there is! It's that simple. All the other data types are
also supported, check out the table below.

## H2 Data Types

Here's a list of the other H2 data types which are handled by
`h2-jdbc`. Note that `h2-jdbc` only modifies the behaviour when
*reading* from the database as H2 already handles *writing* to the
database with `java.time` objects.

| H2 Data Type | Returned Java Object | Returned Java Object with `h2-jdbc` |
| ------------ | -------------------- | ----------------------------------- |
| `DATE`       | `java.sql.Date`      | `java.time.LocalDate`               |
| `TIME`       | `java.sql.Time`      | `java.time.LocalTime`               |
| `TIMESTAMP`  | `java.sql.Timestamp` | `java.time.LocalDateTime`           |
| `TIMESTAMP WITH TIME ZONE` |`org.h2.api.TimestampWithTimeZone` | `java.time.OffsetDateTime` |

## Notes

This library has implicit dependencies (`org.clojure/java.jdbc` and
`com.h2database/h2`) which aren't explicitly listed in `project.clj`
to avoid version conflicts.

Your project must therefore include these.

## License

Distributed under the [MIT License].

Copyright © 2018 Oliver Eidel


<!-- Links -->

[clj-time.jdbc]: https://github.com/clj-time/clj-time/blob/master/src/clj_time/jdbc.clj
[MIT license]: https://raw.githubusercontent.com/olieidel/h2-jdbc/master/LICENSE
