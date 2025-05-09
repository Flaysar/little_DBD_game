(ns maniac-game-server.core
  (:import [java.net ServerSocket Socket])
  (:require [clojure.string :as str]
            [clojure.data.json :as json]))

(defonce game-state
  (atom {
    :map ["################################"
          "#     #                        #"
          "#     #                        #"
          "#     #                        #"
          "#     #               ##########"
          "#                              #"
          "#                              #"
          "#                              #"
          "#                              #"
          "#             #                #"
          "#             #                #"
          "#             #                #"
          "#             #         ########"
          "#######       #                #"
          "#             #                #"
          "################################"]
    :players {}
    :boxes [{:x 3 :y 3 :dropped? false}]
  }))

(defn add-player [state player-id type]
  (assoc-in state [:players player-id] 
    {:type type 
     :x 1
     :y 1
     :speed (if (= type :maniac) 2 1)}))

(defn move-player [state player-id direction]
  (let [[dx dy] ({"w" [0 -1], "s" [0 1], "a" [-1 0], "d" [1 0]} direction)
        player (get-in state [:players player-id])
        map-rows (:map state)
        max-y (dec (count map-rows))
        max-x (dec (count (first map-rows)))
        new-x (max 0 (min (+ (:x player) dx) max-x))
        new-y (max 0 (min (+ (:y player) dy) max-y))]
    (if (and player
             (not= \# (get-in map-rows [new-y new-x])))
      (assoc-in state [:players player-id] 
        (assoc player :x new-x :y new-y))
      state)))

(defn clear-player [state player-id]
  (let [player (get-in state [:players player-id])
        {:keys [x y]} player
        map-rows (:map state)
        row (nth map-rows y)
        updated-row (str (subs row 0 x) \space (subs row (inc x)))]
    (assoc state :map (assoc map-rows y updated-row))))

(defn update-map [state player-id]
  (let [player (get-in state [:players player-id])
        {:keys [x y type]} player
        symbol (if (= type :maniac) "M" "V")
        map-rows (:map state)
        row (nth map-rows y)
        updated-row (str (subs row 0 x) symbol (subs row (inc x)))]
    (assoc state :map (assoc map-rows y updated-row))))

(defn game-over? [state]
  (let [players (vals (:players state))
        maniac (first (filter #(= (:type %) :maniac) players))
        victims (filter #(= (:type %) :victim) players)]
    (some #(and (= (:x %) (:x maniac)) (= (:y %) (:y maniac))) victims)))

(defn get-visible-map [state player-id]
  (let [player (get-in state [:players player-id])
        type (:type player)
        map-rows (:map state)
        all-players (:players state)]
    (if (= type :victim)
      map-rows
      (reduce
        (fn [m [id p]]
          (if (and (not= id player-id) (= (:type p) :victim))
            (let [x (:x p), y (:y p)
                  row (nth m y)
                  updated-row (str (subs row 0 x) \space (subs row (inc x)))]
              (assoc m y updated-row))
            m))
        map-rows
        all-players))))

(defn process-command [state cmd player-id]
  (let [action (str/trim cmd)]
    (case action
      ("w" "s" "a" "d")
      (let [new-state (-> state
                          (clear-player player-id)
                          (move-player player-id action)
                          (update-map player-id))]
        (if (game-over? new-state)
          (update new-state :map #(conj % "GAME OVER"))
          new-state))
      state)))

(defn handle-client [socket player-id]
  (with-open [in (java.io.BufferedReader. (java.io.InputStreamReader. (.getInputStream socket)))
              out (java.io.PrintWriter. (.getOutputStream socket) true)]
    ;; Читаем роль от клиента
    (let [role-str (.readLine in)
          type (case role-str
                 "maniac" :maniac
                 "victim" :victim
                 :victim)]
      (swap! game-state add-player player-id type)

      ;; Основной цикл
      (loop []
        (when-let [cmd (.readLine in)]
          (let [new-state (swap! game-state process-command cmd player-id)
                view-map (get-visible-map new-state player-id)]
            (.println out (json/write-str {:map view-map}))
            (recur)))))))

(defn start-server [port]
  (println "Server started on port" port)
  (with-open [server-socket (ServerSocket. port)]
    (loop []
      (let [client-socket (.accept server-socket)]
        (future 
          (handle-client client-socket (str (java.util.UUID/randomUUID))))
        (recur)))))

(defn -main [& args]
  (start-server 12345))
