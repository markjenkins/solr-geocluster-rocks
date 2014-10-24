package ca.markjenkins.geoclusterrocks;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.handler.TextRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.cycle.RequestCycle;

import org.geojson.FeatureCollection;
import org.geojson.Feature;
import org.geojson.Point;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.spatial4j.core.io.GeohashUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.SolrQuery;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.GroupParams;

import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.SortedMap;
import java.util.Map.Entry;
import java.util.List;

public class GeoSearch extends WebPage {
    static final SolrServer solr;
    static {
	solr = new HttpSolrServer( "http://localhost:8080/solr" );
    }

    static final String GROUP_LIMIT = "1";

    // this is pretty much strait lifted from
    // http://cgit.drupalcode.org/geocluster/tree/includes/GeoclusterHelper.inc
    static final int ZOOMS = 30+1;
    // // Meters per pixel.
    // $maxResolution = 156543.03390625;
    //static final int TILE_SIZE = 256;
    //maxResolution = GEOFIELD_KILOMETERS * 1000 / $tile_size;
    static final double MAX_RESOLUTION = 156.412 * 1000;

    static final double[] resolutions;
    // as the following illustrates, a "final" array isn't so final...
    // in some situations this can be a security problem
    static {
	resolutions = new double[ZOOMS];
	for( int zoom=0; zoom < ZOOMS; zoom++ ){
	    resolutions[zoom] = MAX_RESOLUTION / Math.pow(2, zoom);
	}
    }

    static final int GEOCLUSTER_DEFAULT_DISTANCE = 65;

    static final double RAD_TO_DEGREES = 180 / Math.PI;
    static final int EARTH_AREA = 6378137;
    /**
     * Convert from meters (Spherical Mercator) to degrees (EPSG:4326).
     *
     * This is based on
http://cgit.drupalcode.org/geocluster/tree/includes/GeoclusterHelper.inc
     * 
     * which is based on
https://github.com/mapbox/clustr/blob/gh-pages/src/clustr.js
     *
     *  also see
http://dev.openlayers.org/docs/files/OpenLayers/Layer/SphericalMercator-js.html
https://github.com/openlayers/openlayers/blob/master/lib/OpenLayers/Projection.js#L278
    *
    * @return (lon, lat)
    */
    static double[] backwardMercator(double x, double y) {
	double[] result = new double[2];
	result[0] = x * RAD_TO_DEGREES / EARTH_AREA;
	result[1] =
	    ((Math.PI * 0.5) - 2.0 *
	     Math.atan(Math.exp(-y / EARTH_AREA))
	     ) * RAD_TO_DEGREES;
	return result;
    }
    /**
     * Calculate geohash length for clustering by a specified distance
     * in pixels. This is based on lengthFromDistance() from 
http://cgit.drupalcode.org/geocluster/tree/includes/GeohashHelper.inc
     */
    static int lengthFromDistance(double resolution) {
        double cluster_distance_meters =
	    GEOCLUSTER_DEFAULT_DISTANCE * resolution;
        double x = cluster_distance_meters;
	double y = cluster_distance_meters;
	double[] width_height = backwardMercator(x, y);

        int hashLen =
            GeohashUtils.lookupHashLenForWidthHeight(width_height[0],
						     width_height[1] );
        return hashLen +1;
    }

    static Logger log_l4 = LoggerFactory.getLogger( GeoSearch.class );

    public static String restrictLatitude(String latitude){
	try {
	    double latitude_double = Double.parseDouble(latitude);
	    if (-90 > latitude_double)
		return "-90";
	    else if (latitude_double > 90 )
		return "90";
	    else
		return latitude;
	}
	catch (NumberFormatException e){
	    if (latitude.charAt(0) == '-')
		return "-90";
	    else
		return "90";
	}
    }

    public static String restrictLongitude(String longitude){
	try {
	    double longitude_double = Double.parseDouble(longitude);
	    if (-180 > longitude_double)
		return "-180";
	    else if (longitude_double > 90 )
		return "180";
	    else
		return longitude;
	}
	catch (NumberFormatException e){
	    if (longitude.charAt(0) == '-')
		return "-180";
	    else
		return "180";
	}
    }

    public static QueryResponse query_locations_in_solr
	(String bounds, int zoom ){
	QueryResponse rsp = null;
	SolrQuery params = new SolrQuery();
	String bot_left_long;
	String bot_left_lat;
	String top_right_long;
	String top_right_lat;

	if (bounds == null){
	    bot_left_long = "-180";
	    bot_left_lat = "-90";
	    top_right_long = "180";
	    top_right_lat = "90";
	    params.setQuery("*:*");
	}
	else {
	    String[] queryBounds = bounds.split(",");
	    bot_left_long = restrictLongitude(queryBounds[0]);
	    bot_left_lat = restrictLatitude(queryBounds[1]);
	    top_right_long = restrictLongitude(queryBounds[2]);
	    top_right_lat = restrictLatitude(queryBounds[3]);

	    params.setQuery("location:[" +
			    bot_left_lat + "," +
			    bot_left_long +
			    " TO " + 
			    top_right_lat + "," +
			    top_right_long + "]");
	}

	int hash_len = lengthFromDistance(resolutions[zoom]);
	if (hash_len < 1)
	    hash_len = 1;

	params.setParam(GroupParams.GROUP, true);
	// this is silly, should take 32 to the power of hash_len
	// or not.. that makes a huge number when hash_len is 12...
	// perhaps there is some theoritical upper limit on the number
	// or we just set 
	Set<String> real_prefix_hashes = GeoHash.coverBoundingBox
	    (Double.parseDouble(top_right_lat), // topLeftLat
	     Double.parseDouble(bot_left_long), // topLeftLon
	     Double.parseDouble(bot_left_lat), // bottomRightLat
	     Double.parseDouble(top_right_long), // bottomRightLon
	     hash_len).getHashes();
	params.setRows(real_prefix_hashes.size());
	params.setParam(GroupParams.GROUP_LIMIT, GROUP_LIMIT);
	params.setParam(GroupParams.GROUP_FIELD, "geohash_" + hash_len);

	try {
	    rsp = solr.query( params );
	}
	catch (SolrServerException ex) {
	    log_l4.warn( "unable to execute query", ex );
	    return rsp;
	}

	return rsp;
    }

    public GeoSearch(PageParameters pageParameters) {
	RequestCycle cy = getRequestCycle();

	int zoom;
	try {
	    zoom = Integer.parseInt
		( cy.getRequest().getQueryParameters()
		  .getParameterValue("zoom").toString() );
	}
	catch (NumberFormatException e){
	    zoom = 0;
	}

	QueryResponse rsp = query_locations_in_solr(
	    cy.getRequest().getQueryParameters().getParameterValue("bounds")
	    .toString(),
	    zoom );

	if (rsp == null){
	    cy.scheduleRequestHandlerAfterCurrent
		( new TextRequestHandler("application/json", null, "{}" ) );
	    return;
	}
	
	FeatureCollection fc = new FeatureCollection();

	NamedList<Object> solr_response = rsp.getResponse();
	NamedList<Object> groupedPart =
	    (NamedList<Object>)solr_response.get("grouped");
	
	// this loop is stupid, we know there is only one field we're
	// grouping on...
	for (Entry<String, Object> group_field_entry:
		 groupedPart ){
	    for (NamedList<Object> group:
		     ( (NamedList<List<NamedList<Object>>>)
		       group_field_entry.getValue() )
		     .get("groups") ){
		
		SolrDocumentList docs =
		    (SolrDocumentList)group.get("doclist");

		if (docs.getNumFound() == 1 ){
		    SolrDocument doc = docs.get(0);
		    Feature f = new Feature();
		    f.setProperty("popupContent",
				  (String) doc.getFirstValue("name") );
		    String location = (String)doc.getFirstValue("location");
		    String[] location_parts = location.split(", ");
		    Point p = new Point(
					Double.parseDouble(location_parts[1]),
					Double.parseDouble(location_parts[0]) );
		    f.setGeometry(p);
		    fc.add(f);
		}
		else if (docs.getNumFound() > 1) {
		    String hash_prefix = (String)group.get("groupValue");
		    log_l4.info(hash_prefix);
		    Feature f = new Feature();
		    f.setProperty("clusterCount", docs.getNumFound() );
		    LatLong lat_long = GeoHash.decodeHash(hash_prefix);
		    f.setGeometry(new Point( lat_long.getLon(),
					     lat_long.getLat()
					     ) );
		    fc.add(f);
		}
	    }
	}
	String json_output = "{}";
	try {
	    json_output = new ObjectMapper().writeValueAsString(fc);
	}
	catch (JsonProcessingException e) {
	    // we're forced to catch this, but I don't think we'll ever see it
	    log_l4.error("JsonProblem we would never expect", e);
	}


	cy.scheduleRequestHandlerAfterCurrent
	    ( new TextRequestHandler("application/json", null,
				     json_output ) );
    }
}
