#!/usr/bin/env python3
import argparse
import requests
import logging
import sys

__format = '%(asctime)s %(module)-10s::%(funcName)-20s - [%(lineno)-3d]%(message)s'
logging.basicConfig(stream=sys.stdout,
                    level=logging.INFO,
                    format=__format,
                    datefmt='%Y-%m-%d %H:%M:%S')

logger = logging.getLogger(__name__)

def get_chip_specs(host, port):
    """ Returns all chip specs from the named host and port for pyccd"""
    query = ''.join(['(((red OR blue OR green OR swir1 OR swir2 OR nir) AND sr)', ' ',
                      'OR (toa AND thermal AND NOT tirs2)', ' ',
                      'OR (cfmask AND NOT conf))', ' ',
                      #'AND NOT LANDSAT_8'])
                      ])
    chip_specs=''.join(['http://', host, ':', port, '/landsat/chip-specs?q=', query])
    logger.debug("chip_specs url: {}".format(chip_specs))
    return requests.get(chip_specs).json()

def get_ubids(chip_specs):
    """ Return all ubids from supplied chip-specs """
    return [ts['ubid'] for ts in chip_specs]

def url_template(ubids, start_date, end_date='{{now}}', host='localhost', port='80'):
    """ Returns the inputs url template to be fed into algorithms configuration """
    # TODO: gonna have to deal with the context path being different for local vs deployed
    #       /landsat here, probably / locally
    base = ''.join(['http://', host, ':', port,
                    '/landsat/chips?x={{x}}&y={{y}}',
                    '&acquired=', start_date, '/', end_date])
    ubids = ''.join(['&ubid={}'.format(u) for u in ubids])
    return ''.join([base, ubids])

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--host",  action="store",  help="host for lcmap-landsat api")
    parser.add_argument("--port",  action="store",  help="port for lcmap-landsat api", default="80")
    parser.add_argument("--start", action="store", help="start date for data query YYYY-MM-DD")
    parser.add_argument("--end",   action="store",   help="end date for data query YYYY-MM-DD", default="{{now}}")
    args = parser.parse_args()

    if len(sys.argv) < 2 or not (args.host and args.start):
        parser.print_usage()
        sys.exit(1)
    else:
        print(url_template(sorted(list(set(get_ubids(get_chip_specs(args.host, args.port))))),
                           args.start, args.end, args.host, args.port))
