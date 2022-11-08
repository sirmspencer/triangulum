(ns triangulum.response
  (:require [clojure.string    :as str]
            [triangulum.config :refer [get-config]]
            [triangulum.utils  :as utils]))

(defn data-response
  "Create a response object.
   Body is required. Status, type, and session are optional.
   When a type keyword is passed, the body is converted to that type,
   otherwise the body is converted to your config.edn's :server :response-type."
  [body & [params]]
  (utils/data-response body
                       (if (:type params)
                         params
                         (assoc params :type (get-config :server :response-type)))))

(defn json-response
  [body & [params]]
  (data-response body (assoc params :type :json)))

(defn edn-response
  [body & [params]]
  (data-response body (assoc params :type :edn)))

(defn transit-response
  [body & [params]]
  (data-response body (assoc params :type :transit)))

(defn no-cross-traffic? [{:strs [referer host]}]
  (and referer host (str/includes? referer host)))

(defn forbidden-response [_]
  (data-response "Forbidden" {:status 403}))
