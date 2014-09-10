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

public class GeoSearch extends WebPage {
    static final SolrServer solr;
    static {
	solr = new HttpSolrServer( "http://localhost:8080/solr" );
    }

    static Logger log_l4 = LoggerFactory.getLogger( GeoSearch.class );

    public GeoSearch(PageParameters pageParameters) {
	RequestCycle cy = getRequestCycle();

	QueryResponse rsp;
	SolrQuery params = new SolrQuery();
	String[] queryBounds =
	    cy.getRequest().getQueryParameters().getParameterValue("bounds")
	    .toString().split(",");
	params.setQuery("location:[" +
			queryBounds[1] + "," +
			queryBounds[0] +
			" TO " + 
			queryBounds[3] + "," +
			queryBounds[2] + "]");
	params.setFacet(true);
	params.addFacetField("location");
	params.setFacetLimit(200);

	try {
	    rsp = solr.query( params );
	}
	catch (SolrServerException ex) {
	    log_l4.warn( "unable to execute query", ex );
	    return;
	}

	FeatureCollection fc = new FeatureCollection();

	NamedList<Object> solr_response = rsp.getResponse();
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