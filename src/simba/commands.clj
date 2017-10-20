(ns simba.commands
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]))

(defn required-string [s]
  (< 0 (count s)))

(def cli-options
  [["-i" "--input-queue SQS_URN" "Input SQS URN"
    :parse-fn str
    :validate [required-string "Input sqs urn required"]]
   ["-w" "--worker-definition FILE_PATH" "Worker definition yaml file"
    :parse-fn str
    :validate [required-string "Worker description file required"]]
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
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
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

(defn run [start & args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]

    (if exit-message
      (exit (if ok? 0 1) exit-message)

      (case action
        "start" (start options)))))
