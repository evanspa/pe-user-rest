# pe-user-rest

[![Build Status](https://travis-ci.org/evanspa/pe-user-rest.svg)](https://travis-ci.org/evanspa/pe-user-rest)

A Clojure library encapsulating an abstraction modeling a user within a REST
API.  pe-user-rest is a good jumping off point if you need to develop a web app
or web service that needs to support the concept of user accounts; including
user account creation, modification and authentication (both password and
token-based).

pe-user-rest essentially exposes the functionality of [pe-user-core](https://github.com/evanspa/pe-user-core) as a REST API
using [Liberator](http://clojure-liberator.github.io/liberator/).

pe-user-rest is part of the [pe-* Clojure Library Suite](#pe--clojure-library-suite).

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**
- [Documentation](#documentation)
- [Installation](#installation)
- [pe-* Clojure Library Suite](#pe--clojure-library-suite)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Documentation

* [API Docs](http://evanspa.github.com/pe-user-rest)

## Installation

pe-user-rest is available from Clojars.  Add the following dependency to your
`project.clj` file:

```
[pe-user-rest "0.0.10"]
```

## pe-* Clojure Library Suite
The pe-* Clojure library suite is a set of Clojure libraries to aid in the
development of Clojure and Java based applications.
*(Each library is available on Clojars.)*
+ **[pe-core-utils](https://github.com/evanspa/pe-core-utils)**: provides a set
of various collection-related, date-related and other helpers functions.
+ **[pe-jdbc-utils](https://github.com/evanspa/pe-jdbc-utils)**: provides
  a set of helper functions for working with JDBC.
+ **[pe-datomic-utils](https://github.com/evanspa/pe-datomic-utils)**: provides
  a set of helper functions for working with [Datomic](https://www.datomic.com).
+ **[pe-datomic-testutils](https://github.com/evanspa/pe-datomic-testutils)**: provides
  a set of helper functions to aid in unit testing Datomic-enabled functions.
+ **[pe-user-core](https://github.com/evanspa/pe-user-core)**: provides
  a set of functions for modeling a generic user, leveraging PostgreSQL as a
  backend store.
+ **[pe-user-testutils](https://github.com/evanspa/pe-user-testutils)**: a set of helper functions to aid in unit testing
code that depends on the functionality of the pe-user-* libraries
([pe-user-core](https://github.com/evanspa/pe-user-core) and pe-user-rest).
+ **[pe-apptxn-core](https://github.com/evanspa/pe-apptxn-core)**: provides a
  set of functions implementing the server-side core data layer of the
  PEAppTransaction Logging Framework.
+ **[pe-rest-utils](https://github.com/evanspa/pe-rest-utils)**: provides a set
  of functions for building easy-to-version hypermedia REST services (built on
  top of [Liberator](http://clojure-liberator.github.io/liberator/)).
+ **[pe-rest-testutils](https://github.com/evanspa/pe-rest-testutils)**: provides
  a set of helper functions for unit testing web services.
+ **pe-user-rest**: this library.
+ **[pe-apptxn-restsupport](https://github.com/evanspa/pe-apptxn-restsupport)**:
  provides a set of functions implementing the server-side REST layer of the
  PEAppTransaction Logging Framework.
