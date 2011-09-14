(ns naptime.test.core
  (:use [naptime.core])
  (:use [clojure.test]))

(deftest test-add-job
  (let [job #(identity nil)
        jobs (add-job {} 1000 job)]
    (is (= job (first (get jobs 1000))))))

(deftest test-jobs-for
  (let [job #(identity nil)
        jobs (add-job {} 1000 job)]
    (is (= job (first (jobs-for jobs 1000))))))
