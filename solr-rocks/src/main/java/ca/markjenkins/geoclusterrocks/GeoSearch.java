package ca.markjenkins.geoclusterrocks;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.handler.TextRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import org.geojson.FeatureCollection;
import org.geojson.Feature;
import org.geojson.Point;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeoSearch extends WebPage {

    static Logger log_l4 = LoggerFactory.getLogger( GeoSearch.class );

    public GeoSearch(PageParameters pageParameters) {

	FeatureCollection fc = new FeatureCollection();
	Feature f = new Feature();
	f.setProperty("clusterCount", 5);
	f.setGeometry(new Point(-98.583333, 39.833333) );
	fc.add(f);

	Feature f2 = new Feature();
	f2.setProperty("popupContent", "Coors Field" );
	f2.setGeometry(new Point(-104.99404191970824, 39.756213909328125) );
	fc.add(f2);

	String json_output = "{}";
	try {
	    json_output = new ObjectMapper().writeValueAsString(fc);
	}
	catch (JsonProcessingException e) {
	    // we're forced to catch this, but I don't think we'll ever see it
	    log_l4.error("JsonProblem we would never expect", e);
	}

	getRequestCycle().scheduleRequestHandlerAfterCurrent
	    ( new TextRequestHandler("application/json", null, json_output ) );
    }
}