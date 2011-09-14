(ns naptime.test.core
  (:use [naptime.core])
  (:use [clojure.test]))

(deftest test-add-job
  (let [job (make-job "http://google.com" 1000)
        jobs (add-job {} job)]
    (is (= job (first (get jobs 1000))))))

(deftest test-remove-job
  (let [job (make-job "http://google.com" 1000)
        jobs (add-job {} job)
        jobs (remove-job jobs job)]
    (is (empty? (get jobs 1000)))))

(deftest test-jobs-for
  (let [job (make-job "http://google.com" 1000)
        jobs (add-job {} job)
        jobs (remove-job {} job)]
    (is (empty? (jobs-for jobs 1000)))))
