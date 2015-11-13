(ns groupify.ws-actions
  (:require [clostache.parser :refer :all]
            [clojure.data.json :as json]
            [immutant.web.async :as async]
            [groupify.player :as player]
            [groupify.log :refer :all]
            [groupify.util :as util]
            ))
(def no-response nil)
(def host_channel (atom nil))

(def immediate-dummy-data (slurp "resources/test_immediate_song_data.json"))
(def queue-dummy-data (slurp "resources/test_server_data.json"))

(defn generate-response [action data] {:action action :data data :identity "server"})

(defn handle-ping [data] (generate-response "pong" data))
(defn handle-pong [data] (generate-response "ping" data))

(def state-map {"paused" :paused
                "playing" :playing
                "stopped" :stopped})

(defn handle-set-state [data]
  "Set player state"
  (if-let [state (get state-map data nil)]
    (do (reset! player/player-state state)
        no-response)
    (generate-response "error" (render "invalid state: \"{{state}}\"" {:state data}))))

(defn handle-get-state [data]
  "Get current player state"
  (generate-response "tell-state" @player/player-state))

(defn handle-hello [data]
  "Host just came online."
  (generate-response "hello" "hello"))


(defn handle-debug-queue [data]
  "Send dummy data to host for queue"
  (async/send! @host_channel queue-dummy-data)
  nil)

(defn handle-debug-immediate [data]
  "Send dummy data to host for immediate song play"
  (async/send! @host_channel immediate-dummy-data)
  nil)



(defn handle-register [ch data]
  "Register newly online user"
  (util/atom-append player/client-channels ch)
  (let [user-data {:name (get data "name")}]
    (swap! player/usernames assoc ch user-data)
    (swap! player/queues assoc ch [])
    nil))

(defn handle-get-users [ch d]
  "Return a list of all online users"
  (json/write-str (map #(get @player/usernames %)
                       @player/client-channels)))

(defn handle-default [action data]
  "Error handling for invalid actions"
  (generate-response "error"
                     (render "\"{{action}}\" is not a valid action for identity." {:action action})))

; Implement Client-Server interactions next week
(defn handle-client [ch action data]
  "Handle events generated by client"
  (case action
    "dummy-queue" (handle-debug-queue data)
    "dummy-immediate" (handle-debug-immediate data)
    "register" (handle-register ch data)
    "get-users" (handle-get-users ch data)
    (handle-default action data)))

(defn get-ws-response-for [ch m]
  "Given a channel and a message, return a map containing the response data"
  (let [message (json/read-str m)
        data (get message "data")
        action (get message "action")
        ident (get message "identity")]
    (if (= ident "host")
      (case action
        "ping" (handle-ping data)
        "pong" (handle-pong data)

        "set-state" (handle-set-state data)
        "get-state" (handle-get-state data)

        "hello" (handle-hello data)

        (handle-default action data))
      (handle-client ch action data))))