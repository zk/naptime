# Naptime

Naptime is an application that calls http endpoints at periodic intervals.

## Usage

Environment Variables:

* `MONGO_DB`
* `MONGO_HOST`
* `MONGO_PORT`
* `MONGO_USER` (optional)
* `MONGO_PASSWORD` (optional)
* `WORKER_MAX_CAPACITY`
* `WEB_MAX_THREADS`
* `WEB_WORKER_MAX_CAPACITY` -- The web process also runs a worker, set
  the web process' worker max capacity here
* `WORKER_SLEEP_TIME`

## Stack

* Clojure
* Heroku
* MongoDB

## Architecture

### Scheduling Endpoints

The endpoint and period are recieved from the user, and a next-update absolute time value is calculated based on currrent time and period:

    next execution time = current time + period

This information is then stored like so in the `:jobs` collection:

    {:endpoint "http://heroku.com"
     :period 5000 ; (ms)
     :next-update 1316480549459}


### Running Endpoints

A worker continuously pulls the next job to be executed (where current
time > next update), atomically locks the entry so that no other
workers can grab it, and makes an http request against the endpoint.

The next execution time is calculated at the beginning of this
process.  The next execution time is then updated on the job, and the
job is unlocked, available for another worker to process.


### Updating / Deleting Endpoints

Standard operations against the database.


## Scaling Naptime

Naptime should scale nicely.  Simply add more workers when load gets
too high (see load charts on naptime webapp index page).


## TODO

* Set timeout on http request.
* Using an async http client should allow a higher request cap.
* Human readable input format (10 seconds / 10 s vs 10000).
* Log data should be cleaned on input (ie don't log a start lag of 1
  bil ms).
* Cleaner separation between presentation / persistence.
* Human readable worker names.
* Make database interaction less brittle (have to remember to :upsert?
  false).
* Allow view of historical data for deleted endpoints.
* Better error handling (even though heroku will restart crashed
  workers).
* Refactor for testing.


## License

Copyright (C) 2011 Zachary Kim

Distributed under the Eclipse Public License, the same as Clojure.
