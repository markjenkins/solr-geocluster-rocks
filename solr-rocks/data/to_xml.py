#!/usr/bin/env python

from sys import argv
from xml.sax.saxutils import escape

# tested with r239 of http://code.google.com/p/python-geohash/
from geohash import encode

GEOHASH_ENCODE_LEVELS = 12

with open('US.txt') as f:
    print "<add>"
    for i, line in enumerate(f,1):
        line_splits = line.split('\t')
        name_of_location = line_splits[1]
        latitude, longitude = line_splits[4:5+1]
        print """<doc>
<field name="id">%s</field>
<field name="name">%s</field>
<field name="latitude">%s</field>
<field name="longitude">%s</field>
<field name="location">%s, %s</field>""" % (
            line_splits[0], escape(name_of_location),
            latitude, longitude,
            latitude, longitude )
        full_geohash = encode(float(latitude), float(longitude),
                              GEOHASH_ENCODE_LEVELS)
        for j in range(1, len(full_geohash) ):
            print """<field name="geohash_%s">%s</field>""" % (
                j, full_geohash[:j])

        print "</doc>"

        if len(argv) > 1 and i >= int(argv[1]):
            break
    print "</add>"
