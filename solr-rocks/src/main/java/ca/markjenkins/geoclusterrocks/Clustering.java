package ca.markjenkins.geoclusterrocks;

import ca.markjenkins.geoclusterrocks.GeoSearch;

import com.spatial4j.core.io.GeohashUtils;
import org.apache.lucene.util.SloppyMath;

import org.geojson.Feature;
import org.geojson.Point;
import org.geojson.LngLatAlt;

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashSet;
import java.util.Iterator;

import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.Direction;

public class Clustering {
    // this is pretty much strait lifted from
    // http://cgit.drupalcode.org/geocluster/tree/includes/GeoclusterHelper.inc
    public static final int ZOOMS = 30+1;
    
    public static final int PIXELS_PER_TILE = 256;

    public static final double EARTH_DIAMETER = SloppyMath.earthDiameter(0.0);
    public static final int METERS_PER_KM = 1000;

    // this is the most meters per pixel, what you get when viewing with world
    // with zoom=0
    // circumference = 2*PI*r = PI * diameter
    //
    // http://wiki.openstreetmap.org/wiki/Zoom_levels
    public static final double MAX_RESOLUTION =
	Math.PI * EARTH_DIAMETER * METERS_PER_KM / PIXELS_PER_TILE;

    public static final double[] resolutions;
    public static final int[] geohash_lengths_for_zooms;
    // as the following illustrates, a "final" array isn't so final...
    // in some situations this can be a security problem
    static {
	resolutions = new double[ZOOMS];
	geohash_lengths_for_zooms = new int[ZOOMS];
	for( int zoom=0; zoom < ZOOMS; zoom++ ){
	    // when zoom is 0, 2 to the power of 0 = 1,
	    // and this is MAX_RESOLUTION
	    resolutions[zoom] = MAX_RESOLUTION / Math.pow(2, zoom);
	    geohash_lengths_for_zooms[zoom] =
		lengthFromDistance(resolutions[zoom]);
	}
    }

    public static final int GEOCLUSTER_DEFAULT_DISTANCE = 65;

    public static final double RAD_TO_DEGREES = 180 / Math.PI;
    public static final int EARTH_AREA = 6378137;
    /**
     * Convert from meters (Spherical Mercator) to degrees (EPSG:4326).
     *
     * This is based on
http://cgit.drupalcode.org/geocluster/tree/includes/GeoclusterHelper.inc
     * 
     * which is based on
https://github.com/mapbox/clustr/blob/gh-pages/src/clustr.js
     *
     *  also see
http://dev.openlayers.org/docs/files/OpenLayers/Layer/SphericalMercator-js.html
https://github.com/openlayers/openlayers/blob/master/lib/OpenLayers/Projection.js#L278
    *
    * @return (lon, lat)
    */
    public static double[] backwardMercator(double x, double y) {
	double[] result = new double[2];
	result[0] = x * RAD_TO_DEGREES / EARTH_AREA;
	result[1] =
	    ((Math.PI * 0.5) - 2.0 *
	     Math.atan(Math.exp(-y / EARTH_AREA))
	     ) * RAD_TO_DEGREES;
	return result;
    }
    /**
     * Calculate geohash length for clustering by a specified distance
     * in pixels. This is based on lengthFromDistance() from 
http://cgit.drupalcode.org/geocluster/tree/includes/GeohashHelper.inc
     */
    public static int lengthFromDistance(double resolution) {
	// this is the number of meters we'd like to keep our markers apart
	double cluster_distance_meters =
	    GEOCLUSTER_DEFAULT_DISTANCE * resolution;
	double x = cluster_distance_meters;
	double y = cluster_distance_meters;
	double[] width_height = backwardMercator(x, y);

	int hashLen =
	    GeohashUtils.lookupHashLenForWidthHeight(width_height[0],
						     width_height[1] );
	return hashLen;
    }

    /**
     * Dumb implementation to incorporate pixel variation with latitude
     * on the mercator projection.
     *
     * @todo: use a valid implementation instead of the current guessing.
     *
     * Current implementation is based on the observation:
     * lat = 0 => output is correct
     * lat = 48 => output is 223 pixels distance instead of 335 in reality.
     *
     * derived from pixel_correction() from
     http://cgit.drupalcode.org/geocluster/tree/includes/GeoclusterHelper.inc
     */
    public static double pixel_correction(double lat) {
	return 1 + (335.0 / 223.271875276 - 1) * (Math.abs(lat) / 47.9899);
    }

    /**
     * Calculate the distance between two given points in pixels.
     *
     * This depends on the resolution (zoom level) they are viewed in.
     *
     * derived from distance_pixels() from 
http://cgit.drupalcode.org/geocluster/tree/includes/GeoclusterHelper.inc
     */
    public static double distance_pixels(Point a, Point b, double resolution){
	LngLatAlt a_coords = a.getCoordinates();
	LngLatAlt b_coords = b.getCoordinates();
	double a_latitude = a_coords.getLatitude();
	double distance = SloppyMath.haversin(a_latitude,
					      a_coords.getLongitude(),
					      b_coords.getLatitude(),
					      b_coords.getLongitude() );
	// instead of doing this correciton, I think there are more
	// sophisticated algorithms we can use for this distance to
	// pixel conversion that don't have this problem because we
	// feed them latitude to begin with
	return distance / resolution * pixel_correction(a_latitude);
    }

    public static boolean shouldCluster(Point a, Point b, double resolution) {
	// Calculate distance.
	return distance_pixels(a, b, resolution)
	    <= GEOCLUSTER_DEFAULT_DISTANCE;
    }
    /* Derived from
     * http://cgit.drupalcode.org/geocluster/tree/includes/GeohashHelper.inc
     */
    public static String[] getTopRightNeighbors(String geohash) {
	// Return only top-right neighbors according to the structure of geohash.
	String[] neighbors = new String[4];
	String top = GeoHash.top(geohash);
	neighbors[0] = GeoHash.left(top);
	neighbors[1] = top;
	neighbors[2] = GeoHash.right(top);
	neighbors[3] = GeoHash.right(geohash);
	return neighbors;
    }

    /* Derived from
     * http://cgit.drupalcode.org/geocluster/tree/includes/GeoclusterHelper.inc
     */
    static Point getFactoredCenter
	(Point p1, Point p2, long count1, long count2){
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

    /**
     * Cluster two given rows.
     *
     * Derived from addCluster() in
     http://cgit.drupalcode.org/geocluster/tree/modules/geocluster_solr/plugins/algorithm/SolrGeohashGeoclusterAlgorithm.inc
     *
     */
    static void addCluster
	(Feature item, Feature other_item, String other_item_hash,
	 Map<String, Feature> geohash_groups){

	// this is one thing we're not doing, retaining any memory of
	// what we've clustered together... might be useful to do a feature
	// setProperty of a list of both ids and names at some point to
	// allow browser side to break small valued clusters down by clicking
	// (useful when things have same location)
	// $result1['geocluster_ids'] .= ',' . $result2['geocluster_ids'];
	Object otherItemClusterCount =
	    other_item.getProperty(GeoSearch.CLUSTER_COUNT_FEATURE_PROPERTY);
	long raise_count_by = 1;
	if (otherItemClusterCount != null)
	    raise_count_by = (Long) otherItemClusterCount;

	Object itemClusterCount =
	    item.getProperty(GeoSearch.CLUSTER_COUNT_FEATURE_PROPERTY);
	long newItemCount = 1+raise_count_by;
	if (itemClusterCount!=null)
	    newItemCount = (Long)itemClusterCount+raise_count_by;
	item.setProperty(GeoSearch.CLUSTER_COUNT_FEATURE_PROPERTY,
			 newItemCount);

	item.setGeometry( getFactoredCenter((Point) item.getGeometry(),
					    (Point) other_item.getGeometry(),
					    newItemCount, raise_count_by) );
    }

    /**
     * Create final clusters by checking for overlapping neighbors.
     *
     * Derived from
http://cgit.drupalcode.org/geocluster/tree/plugins/algorithm/GeohashGeoclusterAlgorithm.inc
     */
    static void clusterByNeighborCheck
	(Map<String, Feature> geohash_groups, int zoom) {
	
	double resolution = resolutions[zoom];

	// whoa, big assumption here, we're assuming because geohash_groups
	// is actually a TreeMap or LinkedHashMap that we're getting keys in
	// order here....
	// otherwise we have to do some detecting and casting to work with
	// both cases...
	//
	// Plus our performance is suffering for it, we're having to maintain
	// a set of hashes already merged to ignore and them removeing them
	// after instead of modifying the set while we work it over in one
	// pass... surely the more deep apis of TreeMaps and LinkedHashMap
	// would allow us to iterate and remove at the same time
	HashSet<String> already_removed_hashes = new HashSet<String>();
	for (String item_hash: geohash_groups.keySet() ){

	    // ignore hash already "removed"
	    if(already_removed_hashes.contains(item_hash))
		continue;

	    Feature item = geohash_groups.get(item_hash);
	    Point geometry = (Point) item.getGeometry();
	    
	    // Check top right neighbor hashes for overlapping points.
	    // Top-right is enough because by the way geohash is structured,
	    // future geohashes are always top, topright or right
	    String[] neighbours = getTopRightNeighbors(item_hash);
	    String all_neighbours = "";
	    String removed = "";
	    for (int i=0; i<neighbours.length; i++){
		String other_hash = neighbours[i];
		if (already_removed_hashes.contains(other_hash))
		    continue;
		all_neighbours += " " + other_hash;
		Feature other_item = geohash_groups.get(other_hash);
		if (other_item != null){
		    Point other_geometry = (Point) other_item.getGeometry();

		    if (shouldCluster(geometry, other_geometry, resolution)) {
			addCluster(item, other_item, other_hash,
				   geohash_groups);
			already_removed_hashes.add(other_hash);
			removed += " " + other_hash;
		    }
		}
	    }
	}
	// now we do the actual removing
	for (String remove_hash: already_removed_hashes)
	    geohash_groups.remove(remove_hash);
    }

}
