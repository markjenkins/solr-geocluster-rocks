package ca.markjenkins.geoclusterrocks;

import ca.markjenkins.geoclusterrocks.Clustering;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.handler.TextRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.cycle.RequestCycle;

import org.geojson.FeatureCollection;
import org.geojson.Feature;
import org.geojson.Point;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.params.StatsParams;

import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;

import java.util.Set;
import java.util.Map;
import java.util.Map.Entry;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.Iterator;

public class GeoSearch extends WebPage {
    static final String POPUP_CONTENT_FEATURE_PROPERTY = "popupContent";
    static final String CLUSTER_COUNT_FEATURE_PROPERTY = "clusterCount";

    // maybe we can load both of these variables from a configuration some day?
    // 
    // Do we want to make Solr responsible for sorting the Geohash groups?
    static final boolean SOLR_RESPONSIBLE_SORT = false;
    // if so, we don't need to use a TreeMap to sort the info as it comes in
    // and we can used LinkedHashMap instead, which should perform better
    // (it will use more RAM)
    // change this to false if you want to use a TreeMap even with
    // the above still set to true... red black trees may perform
    // very nicely with the inputs already ordered
    // Or maybe we should just pre-allocate an array and run quicksort
    // or whatever like Drupal geocluster does...
    // whoa, wait a second.. don't we need random access for inserting the
    // stats and doing removal on merge?
    static final boolean USE_TREE_OVER_LINKED_HASH = !SOLR_RESPONSIBLE_SORT;

    static final int STATS_MEAN_FIELD = 6;

    // we don't want the user to  request a huge piece of the earth with
    // a strong zoom in or else it will be too many rows to handle and a
    // kind of denial of service
    // The number of rows we need to get back are the number of geohash
    // prefixes we're looking at, so this doesn't depend on the number of
    // items in the database directly, it depends on the number of unique
    // geohashes with one item that the combination of zoom and bounds
    // implies
    //
    // Ultimately this is a limit on the size of monitor someone can use to
    // display a full map
    // Imperically, we found that we didnt have results larger than
    // 200,000 geohash prefixes with 1797x1562 resolution,
    // and the full US data so we figured 300,000 would be a fine limit
    //
    // notably, search_api_solr that the Drupal Geocluster module /
    // (our inspiration) has a limit of 1,000,000 ...
    static final int NUM_ROWS_ALLOWED = 300000;

    static final SolrServer solr;
    static {
	solr = new HttpSolrServer( "http://localhost:8080/solr" );
    }

    static final String GROUP_LIMIT = "1";

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
	(String bounds, int zoom, boolean stats_enabled){
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

	int hash_len = Clustering.geohash_lengths_for_zooms[zoom];
	String hash_len_geohash_field = "geohash_" + hash_len;

	if (SOLR_RESPONSIBLE_SORT)
	    params.addSort(SortClause.asc(hash_len_geohash_field));

	params.setParam(GroupParams.GROUP, true);
	params.setRows(NUM_ROWS_ALLOWED);
	params.setParam(GroupParams.GROUP_LIMIT, GROUP_LIMIT);
	params.setParam(GroupParams.GROUP_FIELD, hash_len_geohash_field);

	if (stats_enabled){
	    params.setParam(StatsParams.STATS, true);
	    params.setParam(StatsParams.STATS_FIELD, "longitude", "latitude");
	    params.setParam(StatsParams.STATS_FACET, hash_len_geohash_field);
	}

	try {
	    rsp = solr.query( params );
	}
	catch (SolrServerException ex) {
	    log_l4.warn( "unable to execute query", ex );
	    return rsp;
	}

	return rsp;
    }
	    

    static void applyClusterStatistics
	(NamedList<Object> solr_response,
	 Map<String, Feature> geohash_groups){

	// for each statistic, there is only one field (geohash_?)
	// we have facetted on, hence the getVal(0)
	NamedList<Object> stat_latitudes = (NamedList<Object>)
	    ( (NamedList<Object> )
	      solr_response.findRecursive("stats", "stats_fields",
					  "latitude", "facets") ).getVal(0);
	NamedList<Object> stat_longitudes = (NamedList<Object>)
	    ( (NamedList<Object>)
	      solr_response.findRecursive("stats", "stats_fields",
					  "longitude", "facets")).getVal(0);

	Iterator<Entry<String, Object>> lat_iter =
	    stat_latitudes.iterator();
	Iterator<Entry<String, Object>> long_iter =
	    stat_longitudes.iterator();

	while (lat_iter.hasNext() && long_iter.hasNext()){
	    Entry<String, Object> lat_entry = lat_iter.next();
	    Entry<String, Object> long_entry = long_iter.next();

	    // this is the crucial assumption here, that our two
	    // statistics are provided in the same hash order!
	    assert( lat_entry.getKey() == long_entry.getKey() );

	    NamedList<Double> long_stats =
		(NamedList<Double>)long_entry.getValue();
	    assert( long_stats.getName(STATS_MEAN_FIELD).equals("mean") );

	    String geohash_prefix = lat_entry.getKey();
	    if (geohash_groups.containsKey(geohash_prefix) ){
		Feature f = geohash_groups.get(geohash_prefix);
		if (f.getProperty(CLUSTER_COUNT_FEATURE_PROPERTY) != null){
		    f.setGeometry
			( new Point
			  (long_stats.getVal(STATS_MEAN_FIELD),
			   ((NamedList<Double>)lat_entry.getValue())
			   .getVal(STATS_MEAN_FIELD) ) );
		}
	    }
	
	}
    }

    static Map<String, Feature> load_in_sorted_geohash_groups
	(NamedList<Object> groups_f_field, boolean stats_enabled){
	Map<String, Feature> result = null;
	if (USE_TREE_OVER_LINKED_HASH)
	    result = new TreeMap<String, Feature>();
	else
	    result = new LinkedHashMap<String, Feature>();
	
	for (NamedList<Object> group:
		 (List<NamedList<Object>>)groups_f_field.get("groups") ){
	    SolrDocumentList docs =
		(SolrDocumentList)group.get("doclist");

	    String hash_prefix = (String)group.get("groupValue");
	    Feature f = new Feature();
	    long docs_num_found = docs.getNumFound();
	    if (docs_num_found == 1 ){
		SolrDocument doc = docs.get(0);
		f.setProperty(POPUP_CONTENT_FEATURE_PROPERTY,
			      (String) doc.getFirstValue("name") );
		String location = (String)doc.getFirstValue("location");
		String[] location_parts = location.split(", ");
		Point p = new Point(
				    Double.parseDouble(location_parts[1]),
				    Double.parseDouble(location_parts[0]) );
		f.setGeometry(p);
	    }
	    else if (docs_num_found > 1) {
		f.setProperty(CLUSTER_COUNT_FEATURE_PROPERTY,
			      docs.getNumFound() );
		if (!stats_enabled){
		    LatLong lat_long = GeoHash.decodeHash(hash_prefix);
		    f.setGeometry(new Point( lat_long.getLon(),
					     lat_long.getLat()
					     ) );
		}
	    }
	    else
		continue; // just ignore hash prefix with 0 documents, move on
	    result.put(hash_prefix, f);
	}

	return result;
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
	    log_l4.error(e.toString());
	    zoom = 0;
	}

	String stats =
	    cy.getRequest().getQueryParameters()
	    .getParameterValue("stats").toString();
	boolean stats_enabled =
	    stats == null || stats.equals("null") || stats.equals("true");

	QueryResponse rsp = query_locations_in_solr(
	    cy.getRequest().getQueryParameters().getParameterValue("bounds")
	    .toString(),
	    zoom,
            stats_enabled );

	if (rsp == null){
	    cy.scheduleRequestHandlerAfterCurrent
		( new TextRequestHandler("application/json", null, "{}" ) );
	    return;
	}
	
	NamedList<Object> solr_response = rsp.getResponse();

	// we know there is only one field we're grouping on, hence getVal(0)
	Map<String, Feature> geohash_groups =
	    load_in_sorted_geohash_groups
	    (  (NamedList<Object>)
	       ((NamedList<Object>)solr_response.get("grouped")).getVal(0),
	       stats_enabled );

	if (stats_enabled){
	    applyClusterStatistics(solr_response, geohash_groups);
	}

	Clustering.clusterByNeighborCheck(geohash_groups, zoom);

	FeatureCollection fc = new FeatureCollection();
	for (Feature f: geohash_groups.values())
	    fc.add(f);

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
