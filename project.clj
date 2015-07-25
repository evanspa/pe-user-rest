(defproject pe-user-rest "0.0.29-SNAPSHOT"
  :description "A Clojure library encapsulating an abstraction modeling a user within a REST API."
  :url "https://github.com/evanspa/pe-user-rest"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-pprint "1.1.2"]
            [codox "0.8.10"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/data.json "0.2.5"]
                 [compojure "1.2.1"]
                 [liberator "0.12.2"]
                 [ch.qos.logback/logback-classic "1.0.13"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [clj-time "0.8.0"]
                 [pe-core-utils "0.0.11"]
                 [pe-user-core "0.1.25"]
                 [pe-rest-utils "0.0.24"]]
  :resource-paths ["resources"]
  :codox {:exclude [user]
          :src-dir-uri "https://github.com/evanspa/pe-user-rest/blob/0.0.28/"
          :src-linenum-anchor-prefix "L"}
  :profiles {:dev {:source-paths ["dev"]  ;ensures 'user.clj' gets auto-loaded
                   :plugins [[cider/cider-nrepl "0.10.0-SNAPSHOT"]
                             [lein-ring "0.8.13"]]
                   :resource-paths ["test-resources"]
                   :dependencies [[org.clojure/tools.namespace "0.2.7"]
                                  [org.clojure/java.classpath "0.2.2"]
                                  [org.clojure/data.json "0.2.5"]
                                  [org.clojure/tools.nrepl "0.2.7"]
                                  [org.postgresql/postgresql "9.4-1201-jdbc41"]
                                  [pe-rest-testutils "0.0.5"]
                                  [ring-server "0.3.1"]
                                  [ring-mock "0.1.5"]]}
             :test {:resource-paths ["test-resources"]}}
  :jvm-opts ["-Xmx1g" "-DPE_LOGS_DIR=logs"]
  :repositories [["releases" {:url "https://clojars.org/repo"
                              :creds :gpg}]])
