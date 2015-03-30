(ns pe-user-rest.apptxn)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; User-related Application Transaction Use Cases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def apptxn-user-create        0)
(def apptxn-user-login         1)
(def apptxn-user-edit          2)
(def apptxn-user-sync          3)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; User-related Use Case Events
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def apptxnlog-createuser-initiated                     0) ;recorded client-side
(def apptxnlog-createuser-canceled                      1) ;recorded client-side
(def apptxnlog-createuser-remote-attempted              2) ;recorded client-side
(def apptxnlog-createuser-remote-proc-started           3)
(def apptxnlog-createuser-remote-proc-done-err-occurred 4)
(def apptxnlog-createuser-remote-proc-done-success      5)
(def apptxnlog-createuser-remote-attempt-resp-received  6) ;recorded client-side

(def apptxnlog-login-initiated                          0) ;recorded client-side
(def apptxnlog-login-canceled                           1) ;recorded client-side
(def apptxnlog-login-remote-attempted                   2) ;recorded client-side
(def apptxnlog-login-remote-proc-started                3)
(def apptxnlog-login-remote-proc-done-err-occurred      4)
(def apptxnlog-login-remote-proc-done-invalid           5)
(def apptxnlog-login-remote-proc-done-success           6)
(def apptxnlog-login-remote-attempt-resp-received       7) ;recorded client-side

(def apptxnlog-edituser-initiated                       0) ;recorded client-side
(def apptxnlog-edituser-canceled                        1) ;recorded client-side
(def apptxnlog-edituser-remote-attempted                2) ;recorded client-side
(def apptxnlog-edituser-remote-proc-started             3)
(def apptxnlog-edituser-remote-proc-done-err-occurred   4)
(def apptxnlog-edituser-remote-proc-done-success        5)
(def apptxnlog-edituser-remote-attempt-resp-received    6) ;recorded client-side

(def apptxnlog-syncuser-initiated                       0) ;recorded client-side
(def apptxnlog-syncuser-remote-attempted                1) ;recorded client-side
(def apptxnlog-syncuser-remote-skipped-no-conn          2) ;recorded client-side
(def apptxnlog-syncuser-remote-proc-started             3)
(def apptxnlog-syncuser-remote-proc-done-err-occurred   4)
(def apptxnlog-syncuser-remote-proc-done-success        5)
(def apptxnlog-syncuser-remote-attempt-resp-received    6) ;recorded client-side
