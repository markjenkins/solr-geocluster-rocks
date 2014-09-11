#!/usr/bin/env python

from sys import argv
from xml.sax.saxutils import escape


with open('US.txt') as f:
    print "<add>"
    for i, line in enumerate(f,1):
        line_splits = line.split('\t')
        name_of_location = line_splits[1]
        print """<doc>
<field name="id">%s</field>
<field name="name">%s</field>
<field name="location">%s, %s</field>
</doc>""" % (line_splits[0], escape(name_of_location), line_splits[4], line_splits[5] )
        if len(argv) > 1 and i >= int(argv[1]):
            break
    print "</add>"
