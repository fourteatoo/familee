(defproject io.github.fourteatoo/familee "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.3"]
                 [clojure.java-time "1.4.3"]
                 [cheshire "6.1.0"]
                 [org.clojure/tools.logging "1.3.0"]
                 [spootnik/unilog "0.7.32"]
                 [org.clojure/tools.cli "1.2.245"]
                 [diehard "0.12.0"]
                 [cprop "0.1.21"]
                 [camel-snake-kebab "0.4.3"]
                 [nrepl "1.5.1"]
                 [mount "0.1.23"]
                 [clj-http "3.13.1"]
                 [ring/ring-codec "1.3.0"]
                 [com.github.steffan-westcott/clj-otel-api "0.2.10"]
                 [com.github.seancorfield/next.jdbc "1.3.1070"]
                 [org.xerial/sqlite-jdbc "3.51.0.0"]
                 [org.clj-commons/digest "1.4.100"]
                 [clojure-ini "0.0.2"]
                 [org.lz4/lz4-java "1.8.0"]]
  :main ^:skip-aot fourteatoo.familee.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:plugins [[lein-codox "0.10.8"]
                             [lein-cloverage "1.2.4"]]
                   :resource-paths ["dev-resources" "resources"]}}
  :lein-release {:deploy-via :clojars})
