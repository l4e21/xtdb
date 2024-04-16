(ns drivers.python-test
  (:require [clojure.test :refer [deftest use-fixtures is]]
            [test-harness.test-utils :as tu]
            [clojure.java.shell :refer [sh]]))

(def project-root (str @tu/root-path "/lang/python/"))

(defn poetry-install [f]
  ;; Install dependencies
  (let [out (sh "poetry" "install" :dir project-root)]
    (println (:out out)))
  (f))

(use-fixtures :once poetry-install)
(use-fixtures :each tu/with-system)

(deftest python-test
  (let [out (sh "poetry"
                "run" "pytest"
                ; TODO: Figure out where to put this
                ; (str "--junitxml=" report-path)
                :dir project-root)]
    (is (= 0 (:exit out))
        (:out out))))
