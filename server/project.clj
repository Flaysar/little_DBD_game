(defproject maniac-game-server "0.1.0-SNAPSHOT"
  :description "Maniac Game Server"
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/data.json "2.4.0"]]
  :main maniac-game-server.core
  :aot [maniac-game-server.core]
  :repl-options {:init-ns maniac-game-server.core})
