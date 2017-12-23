(ns chourave.event-bus-test
  (:require
    [chourave.event-bus :as event-bus]
    [clj-async-test.core]
    [clojure.core.async :as async]
    [clojure.test :refer [deftest is testing]]))

(deftest test-listener
  (let [subject (atom [])
        ch (async/chan)
        dummy-ch (async/chan)
        stop (@#'event-bus/listener (async/mult ch) #(swap! subject conj %))]
    (async/put! ch 1)
    (is (eventually (= [1] @subject)) "channel data gets send to the listener")
    (async/put! ch 2)
    (is (eventually (= [1 2] @subject)) "the listener gets subsequent elements")
    (async/put! stop :stop)
    (async/go
      (async/>! dummy-ch \a)
      (async/>! ch 3)
      (async/>! dummy-ch \b))
    (is (= \a (async/<!! dummy-ch)) "Give listener a chance to consume stop before we send the 3")
    (is (= \b (async/<!! dummy-ch)) "Give listener to consume the 3 if it was ever going to")
    (is (= [1 2] @subject) "Listener does not get called after stop")))

(defn- push-data
  [q msg]
  (conj q (:?data msg)))

(deftest test-pub-sub
  (let [event-bus (event-bus/new-event-bus :id)
        subject (atom [])
        control (atom [])
        subject-token (event-bus/subscribe! event-bus :subject #(swap! subject push-data %))
        control-token (event-bus/subscribe! event-bus :control #(swap! control push-data %))]

    (testing "We don’t receive a notification just because we subscribed"
      (is (empty? @subject))
      (is (empty? @control)))

    (event-bus/publish! event-bus {:id :subject, :?data 1})
    (is (eventually (= [1] @subject)) "Subscribers receive published events")
    (is (= [] @control) "Subscribers don’t receive events they haven’t subscribed to")

    (event-bus/unsubscribe! event-bus subject-token)
    (event-bus/publish! event-bus {:id :subject, :?data 2})
    (event-bus/publish! event-bus {:id :control, :?data 3})

    ; The following line also intended to leave some time for a spurious 2 to propagate into subject
    (is (eventually (= [3] @control)) "Unsubscribing subject does not affect control")
    (is (= [1] @subject) "When unsubscribed, we de not receive further events")

    (testing "Unsubscribing again is a no-op"
      (is (not (event-bus/unsubscribe! event-bus subject-token))))))
