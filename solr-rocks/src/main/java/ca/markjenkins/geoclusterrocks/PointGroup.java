package ca.markjenkins.geoclusterrocks;

import ca.markjenkins.geoclusterrocks.GeoSearch;

import java.util.ArrayList;

import org.geojson.FeatureCollection;
import org.geojson.Feature;
import org.geojson.Point;

import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrDocument;

import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;

public class PointGroup {
    ArrayList<Feature> points;
    int force_cluster_threshold;
    Feature cluster_feature;
    Point center;

    public PointGroup(SolrDocumentList docs, int set_force_cluster_threshold,
		      boolean stats_enabled, String hash_prefix,
		      Point new_center){
	points = new ArrayList<Feature>();
	force_cluster_threshold = set_force_cluster_threshold;
	cluster_feature = null;
	center = new_center;

	long docs_num_found = docs.getNumFound();
	if (docs_num_found >= 1 &&
	    docs_num_found <= force_cluster_threshold){
	    for (SolrDocument doc: docs){
		Feature f = new Feature();

		f.setProperty(GeoSearch.POPUP_CONTENT_FEATURE_PROPERTY,
			      (String) doc.getFirstValue("name") );
		f.setProperty(GeoSearch.ORG_ID_FEATURE_PROPERTY,
			      (String) doc.getFirstValue("org_id") );
		String location = (String)doc.getFirstValue("location");
		String[] location_parts = location.split(", ");
		Point p = new Point(
				    Double.parseDouble(location_parts[1]),
				    Double.parseDouble(location_parts[0]) );
		f.setGeometry(p);
		points.add(f);
	    }

	}
	else if (docs_num_found > 1) {
	    cluster_feature = new Feature();
	    cluster_feature.setProperty(
	        GeoSearch.CLUSTER_COUNT_FEATURE_PROPERTY,
			  docs.getNumFound() );
	    if (!stats_enabled){
		LatLong lat_long = GeoHash.decodeHash(hash_prefix);
		cluster_feature.setGeometry(new Point( lat_long.getLon(),
						       lat_long.getLat()
						       ) );
	    }
	}
    }

    public boolean empty(){
	return points.size() == 0 && cluster_feature == null;
    }

    public boolean single_point(){
	if (points.size() == 1){
	    assert(cluster_feature == null );
	    return true;
	}
	else
	    return false;
    }

    public Feature get_single_point(){
	assert(single_point());
	return points.get(0);
    }

    public boolean cluster_collection(){
	if (cluster_feature!=null){
	    assert(points.size() == 0);
	    return true;
	}
	else
	    return false;
	    
    }

    public Feature get_cluster_feature(){
	assert(cluster_collection());
	return cluster_feature;
    }

    public boolean grouped_points_collection(){
	if (points.size() > 1){
	    assert(cluster_feature == null );
	    return true;
	}
	else if (points.size() == 1)
	    assert( single_point() );
	else
	    assert( cluster_collection() );

	return false;
    }

    public ArrayList<Feature> get_points(){
	assert(grouped_points_collection());
	return points;
    }

    public Point getGeometry(){
	Point return_point = null;

	if (single_point()){
	    return_point = (Point) points.get(0).getGeometry();
	    assert( null != return_point );
	}
	else if (grouped_points_collection()){
	    if (center == null){
		// fixme, should be always statistically derived
		return_point =  (Point) points.get(0).getGeometry();
		assert(null != return_point);
	    }
	    else
		return_point = center;
	}
	else if (cluster_collection()){
	    return_point =  (Point) (cluster_feature.getGeometry());
	    assert( null != return_point );
	}
	else
	    assert(false);

	return return_point;
    }

    public long getPointCount(){
	if (cluster_collection())
	    return cluster_feature.getProperty
		(GeoSearch.CLUSTER_COUNT_FEATURE_PROPERTY);
	else if ( ! empty() )
	    return points.size();
	else{
	    assert(false);
	    return -1;
	}
    }

   /* Derived from
     * http://cgit.drupalcode.org/geocluster/tree/includes/GeoclusterHelper.inc
     */
    static Point getFactoredCenter
	(Point p1, Point p2, long count1, long count2){
	assert (null != p1);
	assert( null != p2);
	assert( null != p1.getCoordinates() );
	assert( null != p2.getCoordinates() );
	double lon =
	    p1.getCoordinates().getLongitude()*count1 +
	    p2.getCoordinates().getLongitude()*count2;
	double lat =
	    p1.getCoordinates().getLatitude()*count1 +
	    p2.getCoordinates().getLatitude()*count2;
	long totalFactor = count1+count2;
	return
	    new Point(lon / totalFactor, lat / totalFactor);
    }

    /* Will also change otherPG as a performance optimization, be sure to
       stop using otherPG after calling

     Inspired by addCluster() in
     http://cgit.drupalcode.org/geocluster/tree/modules/geocluster_solr/
     plugins/algorithm/SolrGeohashGeoclusterAlgorithm.inc

     */
    public void mergeIn(PointGroup otherPG){
	long orig_count = -1;
	long other_orig_count = otherPG.getPointCount();
	Point orig_geometry = getGeometry();
	Point other_orig_geometry = otherPG.getGeometry();

	if (cluster_collection()){
	    orig_count = cluster_feature.getProperty
		(GeoSearch.CLUSTER_COUNT_FEATURE_PROPERTY);
	}
	else if ( !empty() ){
	    orig_count = points.size();

	    if (otherPG.cluster_collection()){
		// optimizations vs
		// cluster_feature = new Feature();
		// cluster_feature.setProperty...
		// the big assumption here is that otherPG is no longer used
		cluster_feature = otherPG.cluster_feature;
		points.clear();
		assert(cluster_collection());
		getGeometry();
		// have to set geography and center
	    }
	    else {
		assert( ! otherPG.empty() );	
		if ((orig_count + other_orig_count) > force_cluster_threshold){
		    cluster_feature = new Feature();
		    center = null;
		    points.clear();
		    assert(cluster_collection());
		}
		else
		    for (Feature f: otherPG.points)
			points.add(f);
	    }
	}
	else
	    assert (false);

	assert( orig_count >= 1 && other_orig_count >= 1 );

	Point new_center = getFactoredCenter(orig_geometry,
					     other_orig_geometry,
					     orig_count,
					     other_orig_count);
	// if we are/have-become a cluster, set up the count and center
	if (cluster_collection()){
	    cluster_feature.setProperty
		(GeoSearch.CLUSTER_COUNT_FEATURE_PROPERTY,
		 orig_count + other_orig_count);
	    cluster_feature.setGeometry(new_center);
	    center = null;
	}
	else if (!empty())
	    center = new_center;
	else
	    assert(false);
    }
}

