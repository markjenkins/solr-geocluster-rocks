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

import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;

import java.util.HashSet;
import java.util.Set;


public class GeoSearch extends WebPage {
    static final SolrServer solr;
    static {
	solr = new HttpSolrServer( "http://localhost:8080/solr" );
    }

    static Logger log_l4 = LoggerFactory.getLogger( GeoSearch.class );

    public static QueryResponse query_locations_in_solr
	(String bounds, Set facet_queries ){
	QueryResponse rsp = null;
	SolrQuery params = new SolrQuery();
	String[] queryBounds = bounds.split(",");
	String bot_left_long = queryBounds[0];
	String bot_left_lat = queryBounds[1];
	String top_right_long = queryBounds[2];
	String top_right_lat = queryBounds[3];

	params.setQuery("location:[" +
			bot_left_lat + "," +
			bot_left_long +
			" TO " + 
			top_right_lat + "," +
			top_right_long + "]");
	params.setFacet(true);
	params.addFacetField("location");
	params.setFacetLimit(0);
	// fortunately going from a bottom left to top right system to a
	// top left to bottom right system only requires mental gymanstics
	Set<String> prefix_hashes = GeoHash.coverBoundingBoxMaxHashes
	    (Double.parseDouble(top_right_lat), // topLeftLat
	     Double.parseDouble(bot_left_long), // topLeftLon
	     Double.parseDouble(bot_left_lat), // bottomRightLat
	     Double.parseDouble(top_right_long), // bottomRightLon
	     100).getHashes();
	for (String geohash_prefix: prefix_hashes){
	    String q_string = "{!prefix f=location}" + geohash_prefix;
	    params.addFacetQuery(q_string);
	    facet_queries.add(geohash_prefix);
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

    public GeoSearch(PageParameters pageParameters) {
	RequestCycle cy = getRequestCycle();

	Set<String> facet_queries = new HashSet();
	QueryResponse rsp = query_locations_in_solr(
	    cy.getRequest().getQueryParameters().getParameterValue("bounds")
	    .toString(),
	    facet_queries );

	if (rsp == null)
	    return;
	
	FeatureCollection fc = new FeatureCollection();

	NamedList<Object> solr_response = rsp.getResponse();

	for (String facet_query: facet_queries){
	    int count = (Integer)solr_response.findRecursive
		("facet_counts", "facet_queries",
		 "{!prefix f=location}" + facet_query );
	    if ( count > 1 ){
	    Feature f = new Feature();
	    f.setProperty("clusterCount", count);
	    LatLong lat_long = GeoHash.decodeHash(facet_query);
	    f.setGeometry(new Point( lat_long.getLon(),
				     lat_long.getLat()
				     ) );
	    fc.add(f);
	    }
	}

	for (SolrDocument doc: 
		 (SolrDocumentList) solr_response.get("response") ){
	    Feature f = new Feature();
	    f.setProperty("popupContent",
			  (String) doc.getFirstValue("name") );
	    String location = (String)doc.getFirstValue("location");
	    String[] location_parts = location.split(", ");
	    f.setGeometry(new Point(
		Double.parseDouble(location_parts[1]),
		Double.parseDouble(location_parts[0]) ) );
	    fc.add(f);
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
	    ( new TextRequestHandler("application/json", null, json_output ) );
    }
}