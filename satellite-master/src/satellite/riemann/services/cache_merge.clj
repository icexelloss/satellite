;; Copyright 2015 TWO SIGMA OPEN SOURCE, LLC
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns satellite.riemann.services.cache-merge
  (:require [chime]
            [clj-time.core :as t]
            [clj-time.periodic :as periodic]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [riemann.service :refer (Service ServiceEquiv)]
            [satellite.time :as time]
            [satellite.whitelist :as whitelist]))

(defrecord CacheMergeService
           [curator zk-whitelist-path whitelist-syncer managed-syncer manual-syncer
            sync-period
            core merger]
  ServiceEquiv
  (equiv? [this other]
    (and (instance? CacheMergeService other)
         (= curator (:curator other))
         (= zk-whitelist-path (:zk-whitelist-path other))
         (= whitelist-syncer (:whitelist-syncer other))
         (= managed-syncer (:managed-syncer other))
         (= manual-syncer (:manual-syncer other))))
  Service
  (conflict? [this other]
    (and (instance? CacheMergeService other)
         (= curator (:curator other))
         (= zk-whitelist-path (:zk-whitelist-path other))
         (= whitelist-syncer (:whitelist-syncer other))
         (= managed-syncer (:managed-syncer other))
         (= manual-syncer (:manual-syncer other))))
  (reload! [this new-core]
    (reset! core new-core))
  (start! [this]
    (locking this
      (when-not (realized? merger)
        (let [whitelist-cache (:cache @whitelist-syncer)
              managed-cache (:cache @managed-syncer)
              manual-cache (:cache @manual-syncer)
              curator @(:curator curator)]
          (deliver merger
                   (chime/chime-at (periodic/periodic-seq (t/now) (t/millis sync-period))
                                   (fn [_]
                                     (whitelist/merge-whitelist-caches!
                                      curator zk-whitelist-path
                                      whitelist-cache
                                      managed-cache
                                      manual-cache))))))))
  (stop! [this]
    (locking this
      ((@merger)))))

(defn cache-merge-service
  [curator zk-whitelist-path whitelist-syncer managed-syncer manual-syncer
   sync-period]
  (CacheMergeService. curator zk-whitelist-path whitelist-syncer managed-syncer
                      manual-syncer sync-period
                      (atom nil) (promise)))
