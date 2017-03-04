(ns puppetlabs.trapperkeeper.services.webserver.normalized-uri-helpers-test
  (:import (javax.servlet.http HttpServletRequest))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.normalized-uri-helpers
             :as normalized-uri-helpers]))

(defn normalize-uri-path-for-string
  [uri]
  (normalized-uri-helpers/normalize-uri-path
   (reify HttpServletRequest
     (getRequestURI [_] uri))))

(deftest normalize-uris-with-no-special-characters-tests
  (testing "uris with no special characters are preserved after normalization"
    (is (= "" (normalize-uri-path-for-string "")))
    (is (= "/" (normalize-uri-path-for-string "/")))
    (is (= "/foo" (normalize-uri-path-for-string "/foo")))
    (is (= "/foo/bar" (normalize-uri-path-for-string "/foo/bar")))))

(deftest normalize-uris-with-plus-signs-in-path-segments-tests
  (testing (str "non-percent encoded plus signs in uri path segments are "
                "preserved after normalization")
    (is (= "/foo+bar" (normalize-uri-path-for-string "/foo+bar")))
    (is (= "/foo/bar+baz+bim"
           (normalize-uri-path-for-string "/foo/bar+baz+bim"))))
  (testing (str "percent encoded plus signs in uri path segments are "
                "properly decoded after normalization")
    (is (= "/foo+bar" (normalize-uri-path-for-string "/foo%2Bbar")))
    (is (= "/foo/bar+baz+bim"
           (normalize-uri-path-for-string "/foo/bar%2Bbaz%2Bb%69m")))))

(deftest normalize-uris-with-params-in-path-segments-tests
  (testing (str "non-percent encoded parameters in uri path segments are "
                "chopped off after normalization")
    (is (= "/foo" (normalize-uri-path-for-string "/foo;foo=chump")))
    (is (= "/foo/bar/baz" (normalize-uri-path-for-string
                       "/foo/bar;bar=chocolate/baz;baz=bim"))))
  (testing (str "percent-encoded parameters in uri path segments are properly "
                "decoded after normalization")
    (is (= "/foo;foo=chump" (normalize-uri-path-for-string
                               "/foo%3Bfoo=chump")))
    (is (= "/foo/bar;bar=chocolate/baz;baz=bim"
           (normalize-uri-path-for-string
            "/foo/bar%3Bbar=chocolate/baz%3Bbaz=b%69m")))))

(deftest normalize-uris-with-percent-encoded-characters-tests
  (testing (str "percent-encoded characters are properly decoded after "
                "normalization")
    (is (= "/foo" (normalize-uri-path-for-string "/%66oo")))
    (is (= "/foo/b a r" (normalize-uri-path-for-string
                         "/foo%2f%62%20a%20%72"))))
  (testing "malformed percent-encoded character throws exception"
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/%b%a%r")))))

(deftest normalize-uris-with-relative-paths-tests
  (testing "non percent-encoded relative paths are normalized as an error"
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string ".")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string "..")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string "/.")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/..")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/.")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/..")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/./bar")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/../bar"))))
  (testing "percent-encoded relative paths are normalized as an error"
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "%2E")))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "%2E%2E") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/%2E") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/%2E%2E") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/%2E") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/%2E%2E") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/%2E/bar") ))
    (is (thrown? IllegalArgumentException (normalize-uri-path-for-string
                                           "/foo/%2E%2E/bar") )))
  (testing (str "period characters not representing relative paths are not "
                "normalized as an error")
    (is (= "/foo/.bar" (normalize-uri-path-for-string "/foo/.bar")))
    (is (= "/foo/..bar" (normalize-uri-path-for-string "/foo/..bar")))
    (is (= "/foo/bar." (normalize-uri-path-for-string "/foo/bar.")))
    (is (= "/foo/bar.." (normalize-uri-path-for-string "/foo/bar..")))
    (is (= "/foo/bar.../baz" (normalize-uri-path-for-string "/foo/bar.../baz")))
    (is (= "/foo/.bar" (normalize-uri-path-for-string "/foo/%2Ebar")))
    (is (= "/foo/..bar" (normalize-uri-path-for-string "/foo/%2E%2Ebar")))
    (is (= "/foo/bar." (normalize-uri-path-for-string "/foo/bar%2E")))
    (is (= "/foo/bar.." (normalize-uri-path-for-string "/foo/bar%2E%2E")))
    (is (= "/foo/bar/.../baz" (normalize-uri-path-for-string
                               "/foo/bar/%2E%2E%2E/baz")))))

(deftest normalize-uri-with-overlong-utf8-chars-tests
  (testing (str "utf-8 characters with overlong encodings are substituted "
                "with replacement characters")
    ;; %C0%AE is an overlong (leading zero-padded, two byte) encoding of the
    ;; full stop (period) character.  The full stop character's minimal
    ;; encoding in UTF-8 is %2E.  These tests validate that the normalization
    ;; process substitutes replacement characters for the original characters
    ;; in the string - as opposed to decoding the character back to the
    ;; same character that the minimal form would decode to.
    ;;
    ;; From section 3 of RFC 3629 (https://tools.ietf.org/html/rfc3629):
    ;;> Implementations of the decoding algorithm above MUST protect against
    ;;> decoding invalid sequences.  For instance, a naive implementation may
    ;;> decode the overlong UTF-8 sequence C0 80 into the character U+0000,
    ;;> or the surrogate pair ED A1 8C ED BE B4 into U+233B4.  Decoding
    ;;> invalid sequences may have security consequences or cause other
    ;;> problems.
    (is (= "-64-82" (normalize-uri-path-for-string "%C0%AE")))
    (is (= "/foo/-64-82/-64-82" (normalize-uri-path-for-string "/foo/%C0%AE/%C0%AE")))))

(deftest normalize-uris-with-redundant-slashes-tests
  (testing "uris with redundant slashes are removed"
    (is (= "/" (normalize-uri-path-for-string "//")))
    (is (= "/foo" (normalize-uri-path-for-string "//foo")))
    (is (= "/foo" (normalize-uri-path-for-string "///foo")))
    (is (= "/foo/bar" (normalize-uri-path-for-string "/foo//bar")))
    (is (= "/foo/bar/" (normalize-uri-path-for-string "/foo//bar///")))))

(deftest normalize-uris-with-percent-decoding-and-slash-removal
  (testing (str "uris with both percent-encoded characters and redundant "
                "slashes are properly normalized")
    (is (= "/foo/b a r" (normalize-uri-path-for-string
                         "/%66oo%2f%2f%2f%62%20a r")))))
