package ca.markjenkins.geoclusterrocks;

import com.spatial4j.core.io.GeohashUtils;
import org.geojson.Point;
import org.geojson.LngLatAlt;
import org.apache.lucene.util.SloppyMath;

public class Clustering {
    // this is pretty much strait lifted from
    // http://cgit.drupalcode.org/geocluster/tree/includes/GeoclusterHelper.inc
    public static final int ZOOMS = 30+1;
    // // Meters per pixel.
    // $maxResolution = 156543.03390625;
    //static final int TILE_SIZE = 256;
    //maxResolution = GEOFIELD_KILOMETERS * 1000 / $tile_size;
    public static final double MAX_RESOLUTION = 156.412 * 1000;

    public static final double[] resolutions;
    public static final int[] geohash_lengths_for_zooms;
    // as the following illustrates, a "final" array isn't so final...
    // in some situations this can be a security problem
    static {
	resolutions = new double[ZOOMS];
	geohash_lengths_for_zooms = new int[ZOOMS];
	for( int zoom=0; zoom < ZOOMS; zoom++ ){
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
	double cluster_distance_meters =
	    GEOCLUSTER_DEFAULT_DISTANCE * resolution;
	double x = cluster_distance_meters;
	double y = cluster_distance_meters;
	double[] width_height = backwardMercator(x, y);

	int hashLen =
	    GeohashUtils.lookupHashLenForWidthHeight(width_height[0],
						     width_height[1] );
	return hashLen +1;
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
}