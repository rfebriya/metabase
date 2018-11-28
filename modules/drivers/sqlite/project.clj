(defproject metabase/sqlite-driver "1.0.0-SNAPSHOT"
  :min-lein-version "2.5.0"
  :dependencies [[org.xerial/sqlite-jdbc "3.21.0.1"]]
  :jvm-opts ["-XX:+IgnoreUnrecognizedVMOptions"
             "--add-modules=java.xml.bind"]
  :profiles {:provided {:dependencies [[metabase-core "1.0.0-SNAPSHOT"]]}
             :uberjar  {:aot           :all
                        :javac-options ["-target" "1.8", "-source" "1.8"]
                        :target-path   "target/%s"
                        :uberjar-name  "sqlite.metabase-driver.jar"}})
