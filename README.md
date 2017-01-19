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
  {"result": "result_structure", 
   "result_md5": "abc123",
   "result_status": "the status", 
   "result_produced": "2017-01-01-17:57:33Z",
   "tile_x": 123,
   "tile_y": 456,
   "x": 678,
   "y": 901,
   "algorithm": "pyccd-beta1"}
  ```
  
  If there were no results available, they are automatica{lly scheduled for production
  and the return status is HTTP 202. A ticket is returned, which is the same structure 
  minus the ```result, result_md5, result_produced, and result_status``` fields.
  
  Successive calls will continue to return the ticket until a result is available.
  ```json 
  {}
  ```
  
  If results are available but additional source data is available, production may be triggered
  specifying ```?refresh=true```
  
### Installation:
TBD

### Configuration:
TBD

### Contributing:
TBD
