A demonstration of how to use Apache Solr for geoclustering. No actually clustering demonstrated yet.

Currently this is just a basic maven project that uses Jetty to start a web server with Wicket and Solr, with a basic schema for place names and locations. Outline structure for how to do all this maven/Jetty/Solr stuff taken from https://github.com/ryantxu/spatial-solr-sandbox

    $ cd solr-rocks
    $ mvn jetty:run
From a seperate shell

    $ cd solr-rocks/data
    $ curl http://localhost:8080/solr/update  -H 'Content-type:application/xml' \
    --data-binary @US_2000_entries.xml
    $ curl 'http://localhost:8080/solr/update?softCommit=true'

For a larger data set

    $ curl -o US.zip http://download.geonames.org/export/dump/US.zip
    $ unzip US.zip
    # Note, this uncompresses to 250 megabytes!
    $ curl http://localhost:8080/solr/update  -H 'Content-type:application/xml' \
    --data-binary @US.xml
    $ curl 'http://localhost:8080/solr/update?softCommit=true'

or to make your own smaller data set:

    $ python to_xml.py 5000 > US_5000_entries.xml
    $ curl http://localhost:8080/solr/update  -H 'Content-type:application/xml' \
    --data-binary @US_5000_entries.xml
    $ curl 'http://localhost:8080/solr/update?softCommit=true'

Make your queries to /solr/ as per solr docs
