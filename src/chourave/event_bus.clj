(ns chourave.event-bus
  "A bus where publishers can asynchronously send messages to subscribers on topics they
  express an interest in."
  (:require
    [clojure.core.async :as async]
    [taoensso.timbre :as timbre]))

(defprotocol EventBusSubscribe
  (subscribe! [event-bus topic handler])
  (unsubscribe! [event-bus token]))

(defprotocol EventBusPublish
  (publish! [event-bus event]))

(defn- listener
  [mult f]
  (let [ch (async/chan)
        stop (async/chan)]
    (async/tap mult ch)
    (async/go-loop []
      (if (async/alt!
            stop false
            ch ([msg]
                 (f msg)
                 true))
        (recur)
        (do
          (async/untap mult ch)
          (async/close! ch)
          (async/close! stop))))
    stop))

(defn- topic-mult!
  [{:keys [publication subscriptions]} topic]
  (if-let [mult (topic @subscriptions)]
    mult
    (let [ch (async/chan)
          mult (async/mult ch)]
      (async/sub publication topic ch)
      (swap! subscriptions assoc topic mult)
      mult)))

(defrecord AsyncEventBus [input-chan publication subscriptions]
  EventBusSubscribe
  (subscribe!
    [event-bus topic handler]
    (let [mult (topic-mult! event-bus topic)]
      (listener mult handler)))
  (unsubscribe! [_ token]
    (async/put! token ::stop))

  EventBusPublish
  (publish! [_ event]
    (async/put! input-chan event)))

(defn new-event-bus
  [topic-fn]
  (let [ch (async/chan)]
    (->AsyncEventBus ch (async/pub ch topic-fn) (atom {}))))
