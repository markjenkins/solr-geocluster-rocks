package ca.markjenkins.geoclusterrocks;

import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.target.coding.HybridUrlCodingStrategy;
import org.apache.wicket.util.file.File;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

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
  public Class<SearchPage> getHomePage()
	{
		return SearchPage.class;
	}

  @Override
  protected void init() {
    super.init();

    mount( new HybridUrlCodingStrategy( "search", SearchPage.class ) );
  }
}
