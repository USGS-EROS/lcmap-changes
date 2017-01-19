[![Build Status](https://travis-ci.org/USGS-EROS/lcmap-landsat.svg?branch=develop)](https://travis-ci.org/USGS-EROS/lcmap-changes).
# lcmap-changes
HTTP endpoint for LCMAP change detection.

### Usage:
```bash
  # HTTP GET hostname:port/change/<algorithm-and-version>/x/y?refresh=true|false
  #
  user@machine:~$ http http://localhost:5678/changes/pyccd-beta1/123/456
  ```
  If there are results available for the algorithm/x/y combo, return status is HTTP 200
  with the following response body:
  ```json
  {
    "tile_x": 32,
    "tile_y": 128,
    "algorithm": "pyccd-beta1",
    "x": 123,
    "y": 456,
    "result": "{algorithm-result-structure}", 
    "result_md5": "33caa90904b2295132d105bec3135e4c",
    "result_status": "the status", 
    "result_produced": "2017-01-01-17:57:33Z",
    "tile_update_requested": "2017-01-01-17:57:31Z",
    "tile_update_began": "2017-01-01-17:57:32Z",
    "tile_update_ended": "2017-01-01-17:57:32Z",
    "inputs_url": "http://localhost:5678/landsat/tiles?x=123&y=456&acquired=2015-01-01/2017-01-01&ubid=LANDSAT_5/TM/sr_band1&ubid=LANDSAT_5/TM/sr_band2&ubid=LANDSAT_5/TM/sr_band3&ubid=LANDSAT_5/TM/sr_band4&ubid=LANDSAT_5/TM/sr_band5&ubid=LANDSAT_5/TM/sr_band7",
    "inputs_md5": "189e725f4587b679740f0f7783745056"   
   }
  ```
  
  If there were no results available, they are automatically scheduled for production
  and the return status is HTTP 202. A ticket is returned, which is the same structure 
  minus the ```result, result_md5, result_produced, and result_status``` fields.
   ```json
  {
    "tile_x": 32,
    "tile_y": 128,
    "algorithm": "pyccd-beta1",
    "x": 123,
    "y": 456,
    "tile_update_requested": "2017-01-01-17:57:31Z",
    "tile_update_began": null,
    "tile_update_ended": null,
    "inputs_url": "http://localhost:5678/landsat/tiles?x=123&y=456&acquired=2015-01-01/2017-01-01&ubid=LANDSAT_5/TM/sr_band1&ubid=LANDSAT_5/TM/sr_band2&ubid=LANDSAT_5/TM/sr_band3&ubid=LANDSAT_5/TM/sr_band4&ubid=LANDSAT_5/TM/sr_band5&ubid=LANDSAT_5/TM/sr_band7", 
   }
  ```
  Successive calls will continue to return the ticket with HTTP 202 until a result is available.
  
  If additional source data is available production may be rescheduled by specifying ```?refresh=true``` in the querystring.
  Existing change results will be replaced when the tile updates complete.
  
### Installation:
TBD

### Configuration:
TBD

### Contributing:
TBD
