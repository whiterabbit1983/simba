(ns simba.commands
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]

            [com.climate.squeedo.sqs-consumer
             :refer [stop-consumer]]
            [taoensso.timbre :as log]

            [simba.utils :as utils]))

(def cli-options
  [["-i" "--input-queue SQS_URN" "Input SQS URN"
    :parse-fn str
    :missing "Input sqs urn required"]

   ["-w" "--worker-definition FILE_PATH" "Worker definition yaml file"
    :parse-fn str
    :missing "Worker description file required"]

   ["-s" "--secret SECRET" "hmac secret"
    :parse-fn str 
    :missing "hmac secret required"]

   ["-f" "--refresh-interval SECONDS" "Polling interval in seconds"
    :parse-fn #(Double/parseDouble %)
    :default 5]

   ["-r" "--aws-region REGION" "AWS region for sqs"
    :parse-fn str
    :default "us-east-1"]

   ["-n" "--sns-topic SNS_URN" "SNS topic URN for publishing backpressure and task state change updates"
    :default nil
    :default-desc "<SNS URN>"]

   ["-v" "--verbose"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Simba -- SQS based task router that handles backpressure automatically"
        ""
        "Options:"

        options-summary]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args [args]
  (let [parse-result (parse-opts args cli-options)
        {:keys [options arguments errors summary]} parse-result]

    (cond
     (:help options)  ;; => Print options summary
     {:exit-message (usage summary) :ok? true}

     errors  ;; => Print errors
     {:exit-message (error-msg errors)}

     :else  ;; => Start service with opts
     {:action "start" :options options})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn run [start args]
  (let [result (validate-args args)
        {:keys [action options exit-message ok?]} result
        verbose? (:verbose options)
        refresh (:refresh-interval options)]
    (log/set-level!
     (if verbose? :debug :warn))

    (if exit-message
      (exit (if ok? 0 1) exit-message)

      (case action
        "start"
        (do
          (log/info "Starting consumer")
          (let [consumer (start options)]
            (utils/before-shutdown stop-consumer consumer)

            ;; Wait for tasks
            (while true
              (do
                (log/debug "Sleeping for" refresh "seconds")
                (Thread/sleep (* refresh 1000))))))))))
