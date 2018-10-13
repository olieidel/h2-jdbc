(defproject io.eidel/h2-jdbc "0.1.0-SNAPSHOT"
  :author "Oliver Eidel <http://www.eidel.io>"
  :description "Java 8 data types for the H2 database via JDBC."
  :url "https://github.com/olieidel/h2-jdbc"
  :license {:name         "MIT License"
            :url          "https://opensource.org/licenses/MIT"
            :distribution :repo}

  :dependencies
  [[org.clojure/clojure "1.9.0"]]

  :profiles
  {:dev
   {:dependencies [[org.clojure/java.jdbc "0.7.8"]
                   [com.h2database/h2 "1.4.197"]]}}

  :plugins
  [[lein-ancient "0.6.15"]
   [lein-codox "0.10.4"]]

  :codox {:metadata {:doc/format :markdown}})
