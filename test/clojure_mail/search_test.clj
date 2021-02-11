(ns clojure-mail.search-test
  (:require [clojure.test :refer :all]
            [clojure-mail.folder :as folder]
            :reload))

(defprotocol FolderSearch (search [this ^jakarta.mail.search.SearchTerm st]))
(def search-stub (reify FolderSearch (search [this st] st)))

(defn h-day-of-week
  [dt]
  (let [c (java.util.Calendar/getInstance)]
    (.setTime c dt)
    (.get c java.util.Calendar/DAY_OF_WEEK)))

(deftest search-terms
  (testing "default: string parameter searches within body and subject"
    (let [q (folder/search search-stub "query")]
      (is (= (.getPattern (first (.getTerms q))) "query"))
      (is (= (.getPattern (second (.getTerms q))) "query"))
      (is (= (type (second (.getTerms q))) jakarta.mail.search.BodyTerm))))

  (doall (map #(testing (str "message part condition " %)
                 (let [st (folder/build-search-terms (list % "query"))]
                   (is (= (.getPattern st) "query")))) [:body :subject :from]))

  (doall (map #(testing (str "message part condition " %)
                 (let [st (folder/build-search-terms (list % "foo@example.com"))]
                   (is (= (.getRecipientType st) (folder/to-recipient-type %)))
                   (is (= (.getPattern st) "foo@example.com")))) [:to :cc :bcc]))

  (testing "search value can be an array which is or-red"
    (let [q (folder/search search-stub :body ["foo" "bar"])]
      (is (= (type q) jakarta.mail.search.OrTerm))
      (is (= (.getPattern (first (.getTerms q))) "foo"))
      (is (= (.getPattern (second (.getTerms q))) "bar"))))

  (testing "date support"
    (doall (map
             #(let [q (folder/search search-stub :sent-before %)]
                (is (= (type q) jakarta.mail.search.SentDateTerm))
                (is (= (.. q (getDate) (getYear)) 116))     ; since 1900
                (is (= (.getComparison q) jakarta.mail.search.ComparisonTerm/LE)))
             ["2016.01.01" "2016-01-01" "2016-01-01 12:00:00" "2016.01.01 12:00"]))

    (doall (map
             #(let [q (folder/search search-stub :received-on %)]
                (is (= (type q) jakarta.mail.search.ReceivedDateTerm))
                (is (= (h-day-of-week (.getDate q)) (.get (java.util.Calendar/getInstance) java.util.Calendar/DAY_OF_WEEK)))
                (is (= (.getComparison q) jakarta.mail.search.ComparisonTerm/EQ)))
             [:today]))

    (let [q (folder/search search-stub :received-on :yesterday)
          d (java.util.Calendar/getInstance)]
      (.add d java.util.Calendar/DAY_OF_WEEK -1)
      (is (= (h-day-of-week (.getDate q)) (.get d java.util.Calendar/DAY_OF_WEEK)))))

  (testing "multiple criteria"
    (let [q (folder/search search-stub :body "foo" :received-on :today)]
      (is (= (type q) jakarta.mail.search.AndTerm))
      (is (= (.getPattern (first (.getTerms q))) "foo"))
      (is (= (type (second (.getTerms q))) jakarta.mail.search.ReceivedDateTerm))))

  (testing "multiple criteria, vec is or-red"
    (let [q (folder/search search-stub [:body "foo" :received-on :today])]
      (is (= (type q) jakarta.mail.search.OrTerm))
      (is (= (.getPattern (first (.getTerms q))) "foo"))
      (is (= (type (second (.getTerms q))) jakarta.mail.search.ReceivedDateTerm))))

  (testing "header support"
    (let [q (folder/search search-stub :header "name" "value")]
      (is (= (type q) jakarta.mail.search.HeaderTerm)))
    (let [q (folder/search search-stub :header ["name1" "value1" "name2" "value2"])]
      (is (= (type q) jakarta.mail.search.OrTerm))
      (is (= (type (first (.getTerms q))) jakarta.mail.search.HeaderTerm))))

  (testing "flag support"
    (are [flag set?]
      (let [q (folder/search search-stub flag)]
        (is (= set? (.getTestSet q)))
        (is (= jakarta.mail.search.FlagTerm (type q))))
      :answered? true :-answered? false
      :deleted? true :-deleted? false
      :flagged? true :-flagged? false
      :draft? true :-draft? false
      :recent? true :-recent? false
      :seen? true :-seen? false)))




