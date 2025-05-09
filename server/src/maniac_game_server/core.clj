(ns maniac-game-server.core
  (:import [java.net ServerSocket]
           [java.util UUID])
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
    :boxes []
    :alive-victims 0
    :caught-victims 0
    :total-victims 0
    :game-over nil
    :start-time (System/currentTimeMillis)
    :duration-ms (* 2 60 1000) ; 2 минуты
  }))

(defn add-player [state player-id type]
  (let [new-player {:type type :x 1 :y 1 :speed (if (= type :maniac) 2 1)}]
    (-> state
        (assoc-in [:players player-id] new-player)
        ((fn [s]
           (if (= type :victim)
             (-> s
                 (update :alive-victims inc)
                 (update :total-victims inc))
             s))))))

(defn move-player [state player-id direction]
  (let [[dx dy] ({"w" [0 -1], "s" [0 1], "a" [-1 0], "d" [1 0]} direction)
        player (get-in state [:players player-id])
        map-rows (:map state)
        max-y (dec (count map-rows))
        max-x (dec (count (first map-rows)))
        new-x (max 0 (min (+ (:x player) dx) max-x))
        new-y (max 0 (min (+ (:y player) dy) max-y))]
    (if (and player (not= \# (get-in map-rows [new-y new-x])))
      (assoc-in state [:players player-id] (assoc player :x new-x :y new-y))
      state)))

(defn clear-player [state player-id]
  (let [player (get-in state [:players player-id])
        {:keys [x y]} player
        row (get-in state [:map y])
        updated-row (str (subs row 0 x) \space (subs row (inc x)))]
    (assoc-in state [:map y] updated-row)))

(defn update-map [state player-id]
  (let [player (get-in state [:players player-id])
        {:keys [x y type]} player
        symbol (if (= type :maniac) "M" "V")
        row (get-in state [:map y])
        updated-row (str (subs row 0 x) symbol (subs row (inc x)))]
    (assoc-in state [:map y] updated-row)))

(defn check-collisions [state]
  (let [players (:players state)
        maniac (first (filter (fn [[_ p]] (= (:type p) :maniac)) players))
        victims (filter (fn [[_ p]] (= (:type p) :victim)) players)]
    (reduce (fn [s [vid v]]
              (if (and maniac
                       (= (:x v) (get-in maniac [1 :x]))
                       (= (:y v) (get-in maniac [1 :y])))
                (-> s
                    (update :caught-victims inc)
                    (update :alive-victims dec)
                    (update :players dissoc vid))
                s))
            state
            victims)))

(defn update-game-over [state]
  (let [t (System/currentTimeMillis)
        end (+ (:start-time state) (:duration-ms state))]
    (cond
      (and (zero? (:alive-victims state))
           (= (:caught-victims state) (:total-victims state)))
      (assoc state :game-over "МАНЬЯК ПОБЕДИЛ")

      (>= t end)
      (assoc state :game-over "ПОБЕДА ЖЕРТВ")

      :else state)))

(defn get-visible-map [state player-id]
  (let [player (get-in state [:players player-id])
        type (:type player)
        base-map (:map state)
        game-over-msg (:game-over state)]
    (cond
      game-over-msg
      (conj base-map game-over-msg "Нажмите Q для выхода")

      (nil? player)
      [(str "Вы были пойманы. Живых жертв: " (:alive-victims state))
       "Нажмите Q для выхода"]

      (= type :victim)
      base-map

      (= type :maniac)
      (let [hidden-map
            (reduce (fn [m [id p]]
                      (if (and (not= id player-id) (= (:type p) :victim))
                        (let [x (:x p), y (:y p)
                              row (nth m y)
                              updated-row (str (subs row 0 x) \space (subs row (inc x)))]
                          (assoc m y updated-row))
                        m))
                    base-map
                    (:players state))]
        (conj hidden-map (str "Поймано жертв: " (:caught-victims state)))))))

(defn process-command [state cmd player-id]
  (if (= (:game-over state) nil)
    (let [action (str/trim cmd)
          moved-state (if (#{"w" "a" "s" "d"} action)
                        (-> state
                            (clear-player player-id)
                            (move-player player-id action)
                            (update-map player-id))
                        state)
          collided (check-collisions moved-state)]
      (update-game-over collided))
    state))

(defn handle-client [socket player-id]
  (with-open [in (java.io.BufferedReader. (java.io.InputStreamReader. (.getInputStream socket)))
              out (java.io.PrintWriter. (.getOutputStream socket) true)]
    (let [role-str (.readLine in)
          type (case role-str "maniac" :maniac "victim" :victim :victim)]
      (swap! game-state add-player player-id type)
      (loop []
        (when-let [cmd (.readLine in)]
          (let [new-state (swap! game-state process-command cmd player-id)
                view-map (get-visible-map new-state player-id)]
            (.println out (json/write-str {:map view-map}))
            (recur)))))))

(defn start-server [port]
  (println "Server started on port" port)
  (with-open [server (ServerSocket. port)]
    (loop []
      (let [sock (.accept server)]
        (future (handle-client sock (str (UUID/randomUUID))))
        (recur)))))

(defn -main [& args]
  (start-server 12345))
