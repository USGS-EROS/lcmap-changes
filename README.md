![Build Status](https://travis-ci.org/USGS-EROS/lcmap-changes.svg?branch=develop)

# LCMAP-Changes
HTTP endpoint for LCMAP change detection.

## Features
* adds/enables/disables algorithms in the system
* automatically configures rabbitmq based on algorithm name
* listens on HTTP for algorithm result requests
* automatically schedules results to be produced if they do not exist
* captures and persists algorithm results from backend production

## Usage
#### Get Change Results
```bash
  # HTTP GET hostname:port/<algorithm-and-version>/x/y?refresh=true|false
  #
  user@machine:~$ http GET http://localhost:5778/results/pyccd-beta1/123/456
  ```
  If there are results available the return status is HTTP 200 with the following response body:
  ```json
  {
    "tile_x": 32,
    "tile_y": 128,
    "algorithm": "pyccd-beta1",
    "x": 123,
    "y": 456,
    "result": "{msgpacked-algorithm-result-structure}",
    "result_md5": "33caa90904b2295132d105bec3135e4c",
    "result_ok": true,
    "result_produced": "2017-01-01-17:57:33Z",
    "tile_update_requested": "2017-01-01-17:57:31Z",
    "inputs_url": "http://localhost:5678/landsat/tiles?x=123&y=456&acquired=2015-01-01/2017-01-01&ubid=LANDSAT_5/TM/sr_band1&ubid=LANDSAT_5/TM/sr_band2&ubid=LANDSAT_5/TM/sr_band3&ubid=LANDSAT_5/TM/sr_band4&ubid=LANDSAT_5/TM/sr_band5&ubid=LANDSAT_5/TM/sr_band7",
    "inputs_md5": "189e725f4587b679740f0f7783745056"   
   }
  ```

  If no results are available production is automatically scheduled and HTTP 202 is returned along with a minimal body.
   ```json
  {
    "tile_x": 32,
    "tile_y": 128,
    "algorithm": "pyccd-beta1",
    "x": 123,
    "y": 456,
    "tile_update_requested": "2017-01-01-17:57:31Z",
    "inputs_url": "http://localhost:5678/landsat/tiles?x=123&y=456&acquired=2015-01-01/2017-01-01&ubid=LANDSAT_5/TM/sr_band1&ubid=LANDSAT_5/TM/sr_band2&ubid=LANDSAT_5/TM/sr_band3&ubid=LANDSAT_5/TM/sr_band4&ubid=LANDSAT_5/TM/sr_band5&ubid=LANDSAT_5/TM/sr_band7",
   }
  ```
  Successive calls return HTTP 202 until a result is available.

  Production may be rescheduled by specifying ```?refresh=true``` on the querystring.  Existing change results will be replaced once tile updates complete.

  If the request could not be fulfilled, HTTP 422 is returned with an explanation.
  ```bash
  user@machine:~$ http GET http://localhost:5778/results/pyccd-beta1/123/456

  HTTP/1.1 422 Unprocessable Entity
  Content-Length: 117
  Content-Type: application/json
  Date: Wed, 01 Feb 2017 21:05:59 GMT
  Server: Jetty(9.2.10.v20150310)

  {
      "algorithm": "pyccd-beta1",
      "algorithm-available": false,
      "refresh": false,
      "source-data-available": true,
      "x": 123,
      "y": 456
  }
  ```

#### List available algorithms
  ```bash
  # HTTP GET hostname:port/changes/algorithms
  #
user@machine:~$ http GET http://localhost:5778/algorithms
HTTP/1.1 200 OK
Content-Length: 212
Content-Type: application/json
Date: Wed, 01 Feb 2017 21:20:39 GMT
Server: Jetty(9.2.10.v20150310)

[
    {
        "algorithm": "pyccd-beta1",
        "enabled": true,
        "inputs_url_template": "http://localhost:5678/landsat/tiles?x={{x}}&y={{y}}&acquired=2012-01-03-17:33:10Z/{{now}}&ubid=LANDSAT_5/TM/sr_band1&ubid=LANDSAT_5/TM/sr_band2"
    }
]
  ```

#### Show just one algorithm
```bash
user@machine:~$  http GET http://localhost:5778/algorithm/pyccd-beta1
HTTP/1.1 200 OK
Content-Length: 210
Content-Type: application/json
Date: Wed, 01 Feb 2017 21:19:44 GMT
Server: Jetty(9.2.10.v20150310)

{
    "algorithm": "pyccd-beta1",
    "enabled": true,
    "inputs_url_template": "http://localhost:5678/landsat/tiles?x={{x}}&y={{y}}&acquired=2012-01-03-17:33:10Z/{{now}}&ubid=LANDSAT_5/TM/sr_band1&ubid=LANDSAT_5/TM/sr_band2"
}
```

#### Add/Update an algorithm
  ```bash
  # HTTP PUT hostname:port/changes/algorithm/<algorithm-name-and-version>
  # {
  #   "enabled": true|false,
  #   "ubid_query":"ElasticSearch syntax query for ubid tags",
  #   "tiles_url":"url to retrieve inputs, either file or network.  Mustache syntax accepted"
  # }
  #
  # Several mustache template variables are available.  They are:
  # {{x}} = integer - requested x coordinate
  # {{y}} = integer - requested y coordinate
  # {{algorithm}} = string - requested algorithm and version
  # {{now}} - string - ISO8601 timestamp
  #

  user@machine:~$ http PUT http://localhost:5778/algorithm/pyccd-beta1
                       enabled:=true  inputs_url_template='http://localhost:5678/landsat/tiles?x={{x}}&y={{y}}&acquired=2012-01-03-17:33:10/{{now}}&ubid=LANDSAT_5/TM/sr_band1&ubid=LANDSAT_5/TM/sr_band2'

  Content-Length: 210
  Content-Type: application/json
  Date: Wed, 01 Feb 2017 21:15:09 GMT
  Server: Jetty(9.2.10.v20150310)

  {
      "algorithm": "pyccd-beta1",
      "enabled": true,
      "inputs_url_template": "http://localhost:5678/landsat/tiles?x={{x}}&y={{y}}&acquired=2012-01-03-17:33:10Z/{{now}}&ubid=LANDSAT_5/TM/sr_band1&ubid=LANDSAT_5/TM/sr_band2"
  }
  ```

## Installation
Not yet on clojars.  A Docker image is available: ```docker pull usgseros/lcmap-changes```

## Running (WIP)

### Dev profile
```
user@machine:~/projects/lcmap/lcmap-changes$ make docker-deps-up-nodaemon
# In another tab
user@machine:~/projects/lcmap/lcmap-changes$ lein repl
user=> (require '[lcmap.clownfish.setup.initialize :as initialize])  
user=> (initialize/cassandra environment)
user=> (start)
```

### Ops profile (WIP)
* Set environment variables per Configuration section
* ```lein run```
* Alternative, ```lein uberjar``` then ```java -jar target/jar-file-name.jar```

## Configuration
LCMAP-Changes is configurable with the following environment variables.  This
is only necessary when not running in the lein dev or lein test profiles as the
environment is internally defined in those cases.

| Variable | Description |
| --- | --- |
| `CLOWNFISH_HTTP_PORT` | LCMAP-Changes HTTP Server Listen Port |
| `CLOWNFISH_RABBIT_HOST` | RabbitMQ Host |
| `CLOWNFISH_RABBIT_PORT` | RabbitMQ Port |
| `CLOWNFISH_DB_KEYSPACE` | Cassandra keyspace for LCMAP-Changes |
| `CLOWNFISH_DB_CONTACT_POINTS` | Space seperated list of hostname:port of Cassandra servers |
| `CLOWNFISH_DB_USERNAME` | Cassandra username |
| `CLOWNFISH_DB_PASSWORD` | Cassandra password |
| `CLOWNFISH_EXCHANGE` | Exchange for LCMAP-Changes to publish messages |
| `CLOWNFISH_QUEUE` | Queue for LCMAP-Changes to listen for messages |
| `CLOWNFISH_TILE_SPECS_URL` | URL where all tile specs can be loaded from. |

## Integrating With
Workers can be tied in with LCMAP-Changes to fulfil work tickets generated when an algorithm result has not yet been executed and stored.  An AMQP broker with persistent messaging is used to achieve loose coupling and reliable communication between the LCMAP-Changes server and workers.

Actual exchanges and queues are unimportant to this specification, as LCMAP-Changes requires those to be provided as environment variables.

### Work Tickets - Sent by LCMAP-Changes to LCMAP-Change-Worker
##### Content-Type:  ```application/x-msgpack```
##### Routing-Key:   ```CONFIGURED ALGORITHM NAME```
##### Body:
```
{"tile_x": Integer,
 "tile_y": Integer,
 "algorithm": "String",
 "x": Integer,
 "y": Integer,
 "tile_update_requested": "ISO8601 Datetime String",
 "inputs_url": "HTTP(s) url for obtaining inputs, String"}
 ```

### Algorithm Results - Sent by LCMAP-Change-Worker to LCMAP-Changes
##### Content-Type:  ```application/x-msgpack```
##### Routing-Key:   ```CONFIGURED ALGORITHM NAME```
##### Body:
```
{"algorithm": "String",
 "x": Integer,
 "y": Integer,
 "result": "msgpack'd algorithm results",
 "result_md5": "MD5 String of the algorithm result",
 "result_ok": Boolean,
 "result_produced" "ISO8601 Datetime String"}
 ```
