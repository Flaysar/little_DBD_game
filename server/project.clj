(defproject maniac-game-server "0.1.0-SNAPSHOT"
  :description "Maniac Game Server"
  :dependencies [[org.clojure/data.json "2.4.0"]
                 [org.clojure/clojure "1.10.3"]]
  :main maniac-game-server.core  ; <- Это ключевая строка!
  :aot [maniac-game-server.core])