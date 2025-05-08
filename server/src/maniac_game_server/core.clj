(ns maniac-game-server.core
  (:import [java.net ServerSocket Socket])
  (:require [clojure.string :as str]
            [clojure.data.json :as json]))

;; Атомарное состояние игры, содержащее:
;; - :map - двумерный массив строк, представляющий игровое поле
;; - :players - словарь игроков (ключ - ID, значение - параметры игрока)
;; - :boxes - список ящиков на карте
(defonce game-state
  (atom {
    :map ["##########"
          "#        #"
          "#   #    #"
          "##########"]
    :players {}
    :boxes [{:x 3 :y 3 :dropped? false}]
  }))

;; Добавляет нового игрока в состояние игры
;; Параметры:
;; - state: текущее состояние игры
;; - player-id: уникальный идентификатор игрока
;; - type: тип игрока (:maniac или :victim)
;; Возвращает новое состояние с добавленным игроком
(defn add-player [state player-id type]
  (assoc-in state [:players player-id] 
    {:type type 
     :x 1  ; начальная позиция X
     :y 1  ; начальная позиция Y
     :speed (if (= type :maniac) 2 1)}))  ; скорость зависит от типа

;; Перемещает игрока в указанном направлении с проверкой стен
;; Параметры:
;; - state: текущее состояние
;; - player-id: ID игрока
;; - direction: направление ("w", "s", "a", "d")
;; Возвращает новое состояние с обновленной позицией игрока
(defn move-player [state player-id direction]
  (let [[dx dy] ({"w" [0 -1], "s" [0 1], "a" [-1 0], "d" [1 0]} direction)
        player (get-in state [:players player-id])
        map-rows (:map state)
        max-y (dec (count map-rows))  ; максимальный Y (индексация с 0)
        max-x (dec (count (first map-rows)))  ; максимальный X
        new-x (max 0 (min (+ (:x player) dx) max-x))  ; новая X с границами
        new-y (max 0 (min (+ (:y player) dy) max-y))] ; новая Y с границами
    ;; Проверяем, что клетка не является стеной (#)
    (if (and player
             (not= \# (get-in map-rows [new-y new-x])))
      (assoc-in state [:players player-id] 
        (assoc player :x new-x :y new-y))
      state)))  ; если стена - возвращаем исходное состояние

;; Очищает символ игрока с карты (заменяет на пробел)
;; Параметры:
;; - state: текущее состояние
;; - player-id: ID игрока
;; Возвращает состояние с очищенной позицией игрока
(defn clear-player [state player-id]
  (let [player (get-in state [:players player-id])
        {:keys [x y]} player
        map-rows (:map state)
        row (nth map-rows y)
        updated-row (str (subs row 0 x) \space (subs row (inc x)))]
    (assoc state :map (assoc map-rows y updated-row))))

;; Обновляет карту, добавляя символ игрока на его текущую позицию
;; Параметры:
;; - state: текущее состояние
;; - player-id: ID игрока
;; Возвращает состояние с обновленной картой
(defn update-map [state player-id]
  (let [player (get-in state [:players player-id])
        {:keys [x y type]} player
        symbol (if (= type :maniac) "M" "V")  ; символ зависит от типа
        map-rows (:map state)
        row (nth map-rows y)
        updated-row (str (subs row 0 x) symbol (subs row (inc x)))]
    (assoc state :map (assoc map-rows y updated-row))))

;; Обрабатывает команду от игрока
;; Параметры:
;; - state: текущее состояние
;; - cmd: команда ("w", "s", "a", "d")
;; - player-id: ID игрока
;; Возвращает новое состояние после обработки команды
(defn process-command [state cmd player-id]
  (let [action (str/trim cmd)]  ; удаляем лишние пробелы
    (case action
      ;; Для движений: очищаем старую позицию, двигаем, обновляем карту
      ("w" "s" "a" "d") (-> state
                           (clear-player player-id)
                           (move-player player-id action)
                           (update-map player-id))
      ;; Неизвестная команда - возвращаем состояние без изменений
      state)))

;; Обрабатывает соединение с клиентом
;; Параметры:
;; - socket: сокет соединения с клиентом
;; - player-id: ID игрока
(defn handle-client [socket player-id]
  (with-open [in (java.io.BufferedReader. (java.io.InputStreamReader. (.getInputStream socket)))
              out (java.io.PrintWriter. (.getOutputStream socket) true)]
    ;; Добавляем игрока в состояние
    (swap! game-state add-player player-id :victim)
    ;; Основной цикл обработки команд
    (loop []
      (when-let [cmd (.readLine in)]  ; читаем команду от клиента
        (let [new-state (swap! game-state process-command cmd player-id)]
          ;; Отправляем обновленную карту клиенту в формате JSON
          (.println out (json/write-str {:map (:map new-state)}))
          (recur))))))  ; повторяем цикл

;; Запускает сервер на указанном порту
;; Параметры:
;; - port: номер порта
(defn start-server [port]
  (println "Server started on port" port)
  (with-open [server-socket (ServerSocket. port)]
    (loop []
      ;; Принимаем новое соединение
      (let [client-socket (.accept server-socket)]
        ;; Запускаем обработку клиента в отдельном потоке
        (future 
          (handle-client client-socket (str (java.util.UUID/randomUUID))))
        (recur)))))  ; возвращаемся к ожиданию новых соединений

;; Точка входа в программу
(defn -main [& args]
  (start-server 12345))  ; запускаем сервер на порту 12345