package ca.markjenkins.geoclusterrocks;

import com.spatial4j.core.io.GeohashUtils;

public class Clustering {
    // this is pretty much strait lifted from
    // http://cgit.drupalcode.org/geocluster/tree/includes/GeoclusterHelper.inc
    public static final int ZOOMS = 30+1;
    // // Meters per pixel.
    // $maxResolution = 156543.03390625;
    //static final int TILE_SIZE = 256;
    //maxResolution = GEOFIELD_KILOMETERS * 1000 / $tile_size;
    public static final double MAX_RESOLUTION = 156.412 * 1000;

    public static final int[] geohash_lengths_for_zooms;
    static {
	geohash_lengths_for_zooms = new int[ZOOMS];
	for( int zoom=0; zoom < ZOOMS; zoom++ ){
	    geohash_lengths_for_zooms[zoom] =
		lengthFromDistance(MAX_RESOLUTION / Math.pow(2, zoom) );
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

}