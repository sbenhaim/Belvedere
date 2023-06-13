(ns Scyan.server
  (:require [reitit.ring :as ring]
            [reitit.coercion.malli]
            ;; [reitit.openapi :as openapi]
            [reitit.ring.malli]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
    ;       [reitit.ring.middleware.dev :as dev]
    ;       [reitit.ring.spec :as spec]
    ;       [spec-tools.spell :as spell]
            [ring.adapter.jetty :as jetty]
            [muuntaja.core :as m]
            [clojure.java.io :as io]
            [malli.util :as mu]))

#_(def app
  (ring/ring-handler
    (ring/router
      [["/swagger.json"
        {:get {:no-doc true
               :swagger {:info {:title "my-api"
                                :description "swagger docs with [malli](https://github.com/metosin/malli) and reitit-ring"
                                :version "0.0.1"}
                         ;; used in /secure APIs below
                         :securityDefinitions {"auth" {:type :apiKey
                                                       :in :header
                                                       :name "Example-Api-Key"}}
                         :tags [{:name "files", :description "file api"}
                                {:name "math", :description "math api"}]}
               :handler (swagger/create-swagger-handler)}}]
       ["/openapi.json"
        {:get {:no-doc true
               :openapi {:info {:title "my-api"
                                :description "openapi3 docs with [malli](https://github.com/metosin/malli) and reitit-ring"
                                :version "0.0.1"}
                         ;; used in /secure APIs below
                         :components {:securitySchemes {"auth" {:type :apiKey
                                                                :in :header
                                                                :name "Example-Api-Key"}}}}
               :handler (openapi/create-openapi-handler)}}]

       ["/files"
        {:tags ["files"]}

        ["/upload"
         {:post {:summary "upload a file"
                 :parameters {:multipart [:map [:file reitit.ring.malli/temp-file-part]]}
                 :responses {200 {:body [:map [:name string?] [:size int?]]}}
                 :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                            {:status 200
                             :body {:name (:filename file)
                                    :size (:size file)}})}}]

        ["/download"
         {:get {:summary "downloads a file"
                :swagger {:produces ["image/png"]}
                :responses {200 {:description "an image"
                                 :content {"image/png" any?}}}
                :handler (fn [_]
                           {:status 200
                            :headers {"Content-Type" "image/png"}
                            :body (-> "reitit.png"
                                      (io/resource)
                                      (io/input-stream))})}}]]

       ["/math"
        {:tags ["math"]}

        ["/plus"
         {:get {:summary "plus with malli query parameters"
                :parameters {:query [:map
                                     [:x
                                      {:title "X parameter"
                                       :description "Description for X parameter"
                                       :json-schema/default 42}
                                      int?]
                                     [:y int?]]}
                :responses {200 {:body [:map [:total int?]]}}
                :handler (fn [{{{:keys [x y]} :query} :parameters}]
                           {:status 200
                            :body {:total (+ x y)}})}
          :post {:summary "plus with malli body parameters"
                 :parameters {:body [:map
                                     [:x
                                      {:title "X parameter"
                                       :description "Description for X parameter"
                                       :json-schema/default 42}
                                      int?]
                                     [:y int?]]}
                 ;; OpenAPI3 named examples for request & response
                 :openapi {:requestBody
                           {:content
                            {"application/json"
                             {:examples {"add-one-one" {:summary "1+1"
                                                        :value {:x 1 :y 1}}
                                         "add-one-two" {:summary "1+2"
                                                        :value {:x 1 :y 2}}}}}}
                           :responses
                           {200
                            {:content
                             {"application/json"
                              {:examples {"two" {:summary "2"
                                                 :value {:total 2}}
                                          "three" {:summary "3"
                                                   :value {:total 3}}}}}}}}
                 :responses {200 {:body [:map [:total int?]]}}
                 :handler (fn [{{{:keys [x y]} :body} :parameters}]
                            {:status 200
                             :body {:total (+ x y)}})}}]]

       ["/secure"
        {:tags ["secure"]
         :openapi {:security [{"auth" []}]}
         :swagger {:security [{"auth" []}]}}
        ["/get"
         {:get {:summary "endpoint authenticated with a header"
                :responses {200 {:body [:map [:secret :string]]}
                            401 {:body [:map [:error :string]]}}
                :handler (fn [request]
                           ;; In a real app authentication would be handled by middleware
                           (if (= "secret" (get-in request [:headers "example-api-key"]))
                             {:status 200
                              :body {:secret "I am a marmot"}}
                             {:status 401
                              :body {:error "unauthorized"}}))}}]]]

      {;;:reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
       ;;:validate spec/validate ;; enable spec validation for route data
       ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
       :exception pretty/exception
       :data {:coercion (reitit.coercion.malli/create
                          {;; set of keys to include in error messages
                           :error-keys #{#_:type :coercion :in :schema :value :errors :humanized #_:transformed}
                           ;; schema identity function (default: close all map schemas)
                           :compile mu/closed-schema
                           ;; strip-extra-keys (effects only predefined transformers)
                           :strip-extra-keys true
                           ;; add/set default values
                           :default-values true
                           ;; malli options
                           :options nil})
              :muuntaja m/instance
              :middleware [;; swagger & openapi
                           swagger/swagger-feature
                           openapi/openapi-feature
                           ;; query-params & form-params
                           parameters/parameters-middleware
                           ;; content-negotiation
                           muuntaja/format-negotiate-middleware
                           ;; encoding response body
                           muuntaja/format-response-middleware
                           ;; exception handling
                           exception/exception-middleware
                           ;; decoding request body
                           muuntaja/format-request-middleware
                           ;; coercing response bodys
                           coercion/coerce-response-middleware
                           ;; coercing request parameters
                           coercion/coerce-request-middleware
                           ;; multipart
                           multipart/multipart-middleware]}})
    (ring/routes
      (swagger-ui/create-swagger-ui-handler
        {:path "/"
         :config {:validatorUrl nil
                  :urls [{:name "swagger", :url "swagger.json"}
                         {:name "openapi", :url "openapi.json"}]
                  :urls.primaryName "openapi"
                  :operationsSorter "alpha"}})
      (ring/create-default-handler))))

(defn handler [_]
  {:status 200
   :body "Hello World"})

(def app
  (ring/ring-handler
   (ring/router
    ["/" {:get handler}])))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false})
  (println "server running in port 3000"))

(comment
  (start))
