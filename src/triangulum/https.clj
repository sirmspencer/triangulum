(ns triangulum.https
  (:require [clojure.java.io    :as io]
            [clojure.java.shell :as sh]
            [clojure.string     :as str]
            [triangulum.cli     :refer [get-cli-options]]
            [triangulum.utils   :refer [parse-as-sh-cmd]]))

(def ^:private path-env (System/getenv "PATH"))

;; Helper functions

(defn- as-sudo?
  "Check if user is running as sudo."
  []
  (let [{:keys [out]} (sh/sh "id" "-u")]
    (= (str/trim out) "0")))

;; TODO consolidate sh-wrapper functions
(defn- sh-wrapper [dir env & commands]
  (io/make-parents (str dir "/dummy"))
  (sh/with-sh-dir dir
    (sh/with-sh-env (merge {:PATH path-env} env)
      (every?
       (fn [cmd]
         (println cmd)
         (let [{:keys [err out exit]} (apply sh/sh (parse-as-sh-cmd cmd))]
           (println "out: "   out)
           (println "error: " err)
           (= 0 exit)))
       commands))))

(defn- package-certificate
  "Packages a certbot pem file for the given domain."
  [domain certbot-dir]
  (sh-wrapper ".key"
              {}
              (str "openssl pkcs12"
                   " -export"
                   " -out keystore.pkcs12"
                   " -in " certbot-dir "/live/" domain "/fullchain.pem"
                   " -inkey " certbot-dir "/live/" domain "/privkey.pem"
                   " -passout pass:foobar")))

(defn- initial-certificate
  "Creates the initial certbot certificate for a given domain."
  [domain certbot-dir cert-only]
  (let [repo-path (.getAbsolutePath (io/file ""))
        sh-path   (.getAbsolutePath (io/file certbot-dir "renewal-hooks" "deploy" (str domain ".sh")))]
    (and (sh-wrapper "./"
                     {}
                     (str "certbot certonly"
                          " --quiet"
                          " --non-interactive"
                          " --agree-tos"
                          " -m support@sig-gis.com"
                          " --webroot"
                          " -w ./resources/public"
                          " -d " domain))

         (when-not cert-only
           ;; The initial certificates are created without the deploy hook to repackage the cert.
           ;; Create the hook, and then package then the first time.
           (nil? (spit sh-path
                       (str "#!/bin/sh"
                            "\ncd " repo-path
                            "\nclojure -M:https --package-cert -d " domain " -p " certbot-dir)))
           (sh-wrapper "./"
                       {}
                       (str "chmod +x " sh-path))
           (package-certificate domain certbot-dir)
           (println "\n*** Initialization complete ***"
                    "\nYou must now update the permissions for the key file with 'sudo chown -R user:group .key'")))))

(def ^:private cli-options
  {:domain    ["-d" "--domain DOMAIN" "Domain for certbot registration."]
   :path      ["-p" "--path PATH" "Alternative path for certbot installation."
               :default "/etc/letsencrypt"]
   :cert-only ["-o" "--cert-only" "Don't package certificate after initializing."]})

(def ^:private cli-actions
  {:certbot-init {:description "Initialize certbot."
                  :requires    [:domain]}
   :package-cert {:description "Package certbot certificate."
                  :requires    [:domain]}})

(defn -main
  "A set of tools for using certbot as the server certificate manager."
  [& args]
  (let [{:keys [action options]} (get-cli-options args cli-options cli-actions "https")
        {:keys [domain path cert-only]} options]
    (and action
         options
         (if (as-sudo?)
           (let [path (.getAbsolutePath (io/file path))]
             (case action
               :certbot-init (initial-certificate domain path cert-only)
               :package-cert (package-certificate domain path)))
           (println "You must run as sudo."))))
  (shutdown-agents))
