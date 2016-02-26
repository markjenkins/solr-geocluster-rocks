package ca.markjenkins.geoclusterrocks;

import ca.markjenkins.geoclusterrocks.Clustering;
import ca.markjenkins.geoclusterrocks.PointGroup;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.handler.TextRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.util.string.StringValue;

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
import org.apache.solr.client.solrj.util.ClientUtils;


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
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.ArrayList;

public class GeoSearch extends WebPage {
    static final String POPUP_CONTENT_FEATURE_PROPERTY = "popupContent";
    static final String CLUSTER_COUNT_FEATURE_PROPERTY = "clusterCount";
    static final String ORG_ID_FEATURE_PROPERTY = "org_id";
    static final String ICON_GROUP_FEATURE_PROPERTY = "icon_group_id";

    static final int GROUP_SIZE_MAX_ZOOM = 80;

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

    static final String[] MATCH_FIELDS = {
	"type_name", "state_two_letter", "city", "zip", "country"};

    static final SolrServer solr;
    static {
	solr = new HttpSolrServer( "http://localhost:8080/solr" );
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
	(String bounds, int zoom,
	 String[] types_to_exclude,
	 boolean stats_enabled, int group_threshold,
	 Map<String, List<String>> match_criteria,
	 List<String> search_text_words,
	 String require_type
	 ){
	QueryResponse rsp = null;
	SolrQuery params = new SolrQuery();
	String bot_left_long;
	String bot_left_lat;
	String top_right_long;
	String top_right_lat;

	String query_string;
	if (bounds == null){
	    bot_left_long = "-180";
	    bot_left_lat = "-90";
	    top_right_long = "180";
	    top_right_lat = "90";
	    query_string = "*:*";
	}
	else {
	    String[] queryBounds = bounds.split(",");
	    bot_left_long = restrictLongitude(queryBounds[0]);
	    bot_left_lat = restrictLatitude(queryBounds[1]);
	    top_right_long = restrictLongitude(queryBounds[2]);
	    top_right_lat = restrictLatitude(queryBounds[3]);

	    query_string = "location:[" +
			    bot_left_lat + "," +
			    bot_left_long +
			    " TO " + 
			    top_right_lat + "," +
			    top_right_long + "]";
	}
	if ( null != types_to_exclude && types_to_exclude.length >= 1 ){
	    for (String exclude_type: types_to_exclude){
		query_string = query_string + 
		    " AND -type_name:\"" + exclude_type + "\"";
	    }
	}

	if ( null != require_type ){
	    query_string = query_string +
		" AND +type_name:\"" + require_type + "\"";
	}

	// this is going to need to be designed to co-operative with the
	// type filters above
	for (Map.Entry<String, List<String>> entry: match_criteria.entrySet()){
	    String sub_query = null;
	    String field_name = entry.getKey();
	    for (String criteria: entry.getValue()){
		if (null == sub_query){
		    sub_query = "+" + field_name + ":(";
		}
		else{
		    sub_query = sub_query + " ";
		}
		sub_query = sub_query + "\"" +
		    ClientUtils.escapeQueryChars(criteria) + "\"";
	    }
	    if (sub_query != null ){
		sub_query = sub_query + ")";

		query_string = query_string + " AND " + sub_query;
	    }

	}

	if (search_text_words.size() > 0){
	    query_string = query_string + " AND (";
	    String or_string = "";
	    boolean or_string_set = false;
	    for(String search_text : search_text_words){
		query_string = query_string + or_string +
		    " text:\"" + search_text + "\"";
		if (!or_string_set){
		    or_string = " OR ";
		    or_string_set = true;
		}
	    }
	    query_string = query_string + ")";
	}

	params.setQuery(query_string);

	int hash_len = Clustering.geohash_lengths_for_zooms[zoom];
	String hash_len_geohash_field = "geohash_" + hash_len;

	if (SOLR_RESPONSIBLE_SORT)
	    params.addSort(SortClause.asc(hash_len_geohash_field));

	params.setParam(GroupParams.GROUP, true);
	params.setRows(NUM_ROWS_ALLOWED);
	params.setParam(GroupParams.GROUP_LIMIT,
			String.valueOf(group_threshold) );
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
	 Map<String, PointGroup> geohash_groups){

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
	    Point statistical_point = new Point
		(long_stats.getVal(STATS_MEAN_FIELD),
		 ((NamedList<Double>)lat_entry.getValue())
		 .getVal(STATS_MEAN_FIELD) );
	    if (geohash_groups.containsKey(geohash_prefix) ){
		PointGroup pg = geohash_groups.get(geohash_prefix);
		if (pg.cluster_collection())
		    pg.get_cluster_feature().setGeometry(statistical_point);
		else if (pg.grouped_points_collection())
		    pg.center = statistical_point;
	    }
	
	}
    }

    static Map<String, PointGroup> load_in_sorted_geohash_groups
	(NamedList<Object> groups_f_field, boolean stats_enabled,
	 int max_group_size){
	Map<String, PointGroup> result = null;
	if (USE_TREE_OVER_LINKED_HASH)
	    result = new TreeMap<String, PointGroup>();
	else
	    result = new LinkedHashMap<String, PointGroup>();
	
	for (NamedList<Object> group:
		 (List<NamedList<Object>>)groups_f_field.get("groups") ){
	    SolrDocumentList docs =
		(SolrDocumentList)group.get("doclist");

	    String hash_prefix = (String)group.get("groupValue");

	    PointGroup points = new PointGroup(docs, max_group_size,
					       stats_enabled, hash_prefix,
					       null);
	    if (! points.empty() )
 		result.put(hash_prefix, points);
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

	int max_group_size = 1;
	if (zoom>=18)
	    max_group_size = GROUP_SIZE_MAX_ZOOM;

	String stats =
	    cy.getRequest().getQueryParameters()
	    .getParameterValue("stats").toString();
	boolean stats_enabled =
	    stats == null || stats.equals("null") || stats.equals("true");

	String[] types_to_ignore = null;
	IRequestParameters request_params =
	    cy.getRequest().getQueryParameters();
	if (null != request_params.getParameterValues("ignore_types")){
	    int num_types_to_ignore =
		request_params.getParameterValues("ignore_types").size();
	    types_to_ignore = new String[num_types_to_ignore];

	    int i =0;
	    for (StringValue type_to_ignore:
		     request_params.getParameterValues("ignore_types")){
		types_to_ignore[i] = type_to_ignore.toString();
		i++;
	    }
	}

	HashMap<String, List<String>> match_criteria =
	    new HashMap<String, List<String>>();

	for (String field_name: MATCH_FIELDS){
	    List<String> matches = new ArrayList<String>();
		if (null != request_params.getParameterValues(field_name)){
		    for(StringValue match_val:
			    request_params.getParameterValues(field_name)){
			matches.add( match_val.toString() );
		    }
		}
		match_criteria.put( field_name, matches );
	}

	List<String> text_searches = new ArrayList<String>();
	
	if (request_params.getParameterValue("search_text") != null ){
	    String search_text =
		request_params.getParameterValue("search_text").toString();
	    if (search_text != null )
		for (String search_text_word : search_text.split(" ")){
		    text_searches.add(search_text_word);
		}
	}

	String require_type = null;
	if ( null != request_params.getParameterValue("require_org_type") )
	    require_type = 
		request_params.getParameterValue("require_org_type").
		toString();

	QueryResponse rsp = query_locations_in_solr(
	    cy.getRequest().getQueryParameters().getParameterValue("bounds")
	    .toString(),
	    zoom,
	    types_to_ignore,
            stats_enabled,
	    max_group_size,
	    match_criteria,
	    text_searches,
	    require_type);

	if (rsp == null){
	    cy.scheduleRequestHandlerAfterCurrent
		( new TextRequestHandler("application/json", null, "{}" ) );
	    return;
	}
	
	NamedList<Object> solr_response = rsp.getResponse();

	// we know there is only one field we're grouping on, hence getVal(0)
	Map<String, PointGroup> geohash_groups =
	    load_in_sorted_geohash_groups
	    (  (NamedList<Object>)
	       ((NamedList<Object>)solr_response.get("grouped")).getVal(0),
	       stats_enabled,
	       max_group_size);

	if (stats_enabled){
	    applyClusterStatistics(solr_response, geohash_groups);
	}

	/** delete me when done debugging **/
	for (PointGroup pg: geohash_groups.values()){
	    assert( null != pg.getGeometry() );
	    if (pg.cluster_collection() )
		assert( null != pg.get_cluster_feature().getGeometry()  );
	}

	Clustering.clusterByNeighborCheck(geohash_groups, zoom);

	TreeMap output_tree = new TreeMap<String, FeatureCollection>();
	FeatureCollection single_points_collection = new FeatureCollection();
	ArrayList<FeatureCollection> grouped_points_collections = 
	    new ArrayList<FeatureCollection>();
	FeatureCollection clusters_collection = new FeatureCollection();
	for (PointGroup pg: geohash_groups.values()){
	    if (pg.single_point())
		single_points_collection.add(pg.get_single_point());
	    else if (pg.grouped_points_collection()){
		FeatureCollection grouped_points_collection = 
		    new FeatureCollection();
		grouped_points_collections.add(grouped_points_collection);
		for (Feature f: pg.get_points() )
		    grouped_points_collection.add(f);
	    }
	    else if (pg.cluster_collection())
		clusters_collection.add( pg.get_cluster_feature() );
	    else
		assert(false);
	}
	output_tree.put("single_points", single_points_collection);
	output_tree.put("grouped_points", grouped_points_collections);
	output_tree.put("clusters", clusters_collection);

	String json_output = "{}";
	try {
	    json_output = new ObjectMapper().writeValueAsString(output_tree);
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
