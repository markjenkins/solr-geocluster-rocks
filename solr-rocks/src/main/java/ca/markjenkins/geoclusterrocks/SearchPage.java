package ca.markjenkins.geoclusterrocks;

import com.google.common.base.Throwables;
import com.spatial4j.core.context.jts.JtsSpatialContext;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchPage extends WebPage
{
  public static final ExecutorService pool = Executors.newCachedThreadPool();

  static Logger log = LoggerFactory.getLogger( SearchPage.class );
  // Dirty Dirty Dirty Hack...
  //static final SpatialPrefixTree grid = new QuadPrefixTree( -180, 180, -90-180, 90, 16 );
  static final SpatialPrefixTree grid = new GeohashPrefixTree(JtsSpatialContext.GEO,GeohashPrefixTree.getMaxLevelsPossible());
  static final SolrServer solr;
  static {
    solr = new HttpSolrServer( "http://localhost:8080/solr" );
  }

  final IModel<Query> query = new Model<Query>( new Query() );
  final IModel<QueryResponse> queryResponse;
  final IModel<String> error = new Model<String>( null );
  final IModel<Long> elapsed = new Model<Long>( null );

  public SearchPage(final PageParameters parameters)
  {
    queryResponse = new LoadableDetachableModel<QueryResponse>() {
      @Override
      protected QueryResponse load() {
        long now = System.currentTimeMillis();
        QueryResponse rsp = null;
        error.setObject( null );
        try {
          rsp = solr.query( query.getObject().toSolrQuery( 100 ) );
        }
        catch (SolrServerException ex) {
          Throwable t = ex.getCause();
          if( t == null ) {
            t = ex;
          }
          log.warn( "unable to execute query", ex );
          error.setObject( Throwables.getStackTraceAsString(t) );
        }
        catch (Throwable ex) {
          log.warn( "unable to execute query", ex );
          error.setObject( Throwables.getStackTraceAsString(ex) );
        }
        elapsed.setObject( System.currentTimeMillis()-now );
        return rsp;
      }
    };
  }
}