package ca.markjenkins.geoclusterrocks;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.handler.TextRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;

public class GeoSearch extends WebPage {

    private static int i=1;
    public GeoSearch(PageParameters pageParameters) {
	i+=1;
	getRequestCycle().scheduleRequestHandlerAfterCurrent
	    ( new TextRequestHandler("application/json", null,
				     "{\"jsonKey\":" + i + "}" ) );
    }
}