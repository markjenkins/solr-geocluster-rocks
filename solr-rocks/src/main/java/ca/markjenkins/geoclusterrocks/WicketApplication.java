package ca.markjenkins.geoclusterrocks;

import org.apache.wicket.protocol.http.WebApplication;

/**
 * Application object for your web application. If you want to run this application without deploying, run the Start class.
 *
 * @see com.spatial4j.demo.StartDemo#main(String[])
 */
public class WicketApplication extends WebApplication
{
    /**
     * Constructor
     */
	public WicketApplication()
	{
	}

  @Override
  public Class<GeoSearch> getHomePage()
	{
		return GeoSearch.class;
	}

  @Override
  protected void init() {
    super.init();

  }
}
