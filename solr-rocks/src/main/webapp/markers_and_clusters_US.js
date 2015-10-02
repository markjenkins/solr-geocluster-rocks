// @license magnet:?xt=urn:btih:1f739d935676111cfff4b4693e3816e664797050&dn=gpl-3.0.txt GPL-v3-or-Later
// Copyright ParIT Worker Co-operative
// Author Mark Jenkins

mapbox_accessToken = 'pk.eyJ1Ijoic29saWRhcml0eWVjb25vbXkiLCJhIjoiMTExNWFkZDMyNTJhYWJiMGMwYzEzMzAyNTMyNWI4YjgifQ.p5zIXgZV-CaHRksQ_MiMPw'

var ICONS = [
    "finance-small.png",
    "food-small.png",
    "goods-small.png",
    "governance-small.png",
    "housing-small.png",
    "learn-small.png",
    "education-small.png"
]

function icon_group_marker_for_feature(feature, latlng){
    return L.marker(
	latlng,
	{icon: L.icon( {iconUrl: '/images/nsfus_solidarity/' +
			ICONS[feature.properties.icon_group_id]
		       })
	} );
}

function show_continental_US_map(div_name){
    var atrib = 
	"<a href='https://www.mapbox.com/about/maps/' " +
	    "target='_blank'>&copy; Mapbox &copy; " +
	    "OpenStreetMap</a> <a class='mapbox-improve-map' " +
	    "href='https://www.mapbox.com/map-feedback/' " +
	    "target='_blank'>Improve this map</a>'"

    // create a map in the "map" div, set the view to a given place and zoom
    var map = L.map(div_name, {
			attributionControl: true,
			zoomControl: false
		    } );
    map.attributionControl.setPrefix(
	'<a href="http://leafletjs.com" '
	+ 'title="A JS library for interactive maps">Leaflet</a>');

    var logo_control = L.Control.extend({
        options: { position: 'bottomright' },
        onAdd: function (map) {
        var container = L.DomUtil.create('div');
        container.innerHTML =
		'<a href="http://mapbox.com/" class="mapbox-logo" ' +
		'target="_blank">MapBox</a>';
    
        return container;
        }
    });

    map.addControl( new logo_control() );

    L.control.zoom({position: 'topright'}).addTo(map);

    var cu_control = L.control({position: 'topright'});
    cu_control.onAdd = function (map) {
        var div = L.DomUtil.create('div');
        div.innerHTML =
	    '<form><input id="cu_control" type="checkbox" checked/>' + 
	    'Include credit unions</form>';
	return div;
    };
    cu_control.addTo(map);

    // fit within the boundaries of the 48 US states
    // http://en.wikipedia.org/wiki/Extreme_points_of_the_United_States
    // Southern point used is Western Dry Rocks, Florida
    // Western point used is Umatilla Reef, Washinton
    // Northern point used is Northwest Angle, Minnesota
    // Eastern point used is Sail Rock, Maine
    map.fitBounds( [
		       [24.446667, -124.785], // south-west
		       [49.384472, -66.947028] ], // north-east
		   {padding: [20, 20]}
		 );

    var markerGroup = new L.LayerGroup();
    markerGroup.addTo(map);

    var tile_layer = L.tileLayer(
	'http://{s}.tiles.mapbox.com/v4/solidarityeconomy.d591ea8d' +
	'/{z}/{x}/{y}.png?access_token={accessToken}',
	{
	    maxZoom: 18, // is this needed?
	    attribution: atrib,
	    accessToken: mapbox_accessToken
	}
    );

    tile_layer.addTo(map);

    var spider_popup = new L.Popup();

    var geojson_layer_options = {
	pointToLayer: function (feature, latlng) {
	    if (feature.properties.clusterCount) {
		return L.marker(latlng,
				{icon: cluster_icon_create(
				     feature.properties.clusterCount) });
	    }
	    else if (feature.properties.icon_group_id){
		return icon_group_marker_for_feature(feature, latlng);
	    }
	    else {
		return L.marker(latlng);
	    }
	},

	onEachFeature: function(feature, layer) {
	    // does this feature have a property named popupContent?
	    if (feature.properties && feature.properties.popupContent
		&& ! feature.properties.clusterCount ){
		    layer.bindPopup('<a href="/organizations/' +
				    feature.properties.org_id +
				   '/">' +
				   feature.properties.popupContent +
				   '</a>' );
		}
	    else {
		layer.on('click', function(e) {
			     if (map._popup) {
				 map._popup._source.closePopup();
			     }
			     // Zoom and pan to clicked item.
			     map.panTo(layer.getLatLng());
			     map.zoomIn();
			 });
	    } // else
	}
    };


    function display_map(){
        var type_exclusion_queries = '';
	if ( ! document.getElementById("cu_control").checked ){
            type_exclusion_queries = '&ignore_types=Credit Unions';
	}

	jQuery.getJSON("geosearch?bounds=" +
		  map.getBounds().toBBoxString() +
		  "&zoom=" + map.getZoom() +
		  type_exclusion_queries,
		  function(data, status, jqXHR){
		      var geojson_layer = L.geoJson(
			  false,
			  geojson_layer_options
		      );
		      geojson_layer.addData(data['clusters']);
		      geojson_layer.addData(data['single_points']);
		      markerGroup.clearLayers();
		      markerGroup.addLayer(geojson_layer);

		      for(var i=0; i<data['grouped_points'].length; i++){
			  var oms = new OverlappingMarkerSpiderfier(
			      map, {keepSpiderfied: true});
			  var spidergeojson_layer = L.geoJson(
			      false, {
				  pointToLayer: function(feature, latlng){
				      new_marker = icon_group_marker_for_feature(feature, latlng);
				      oms.addMarker(new_marker);
				      return new_marker;
				  },
			       	  onEachFeature: function(feature, layer){
				      layer.bindPopup(
					  '<a href="/organizations/' +
					  feature.properties.org_id +
					  '/">' +
					  feature.properties.popupContent +
					  '</a>' );
				  }
			      }
			  );

			  spidergeojson_layer.addData(
			      data['grouped_points'][i]);
			  markerGroup.addLayer(spidergeojson_layer);
		      }

		  }
		 );
    }

    function handle_map_move_end(e){
	if (map._popup){
	    map._popup._source.closePopup();
	}
	display_map();
    }

    function handle_cu_control_change(){
	display_map();
    }

    map.on('moveend', handle_map_move_end);
    document.getElementById("cu_control").addEventListener(
	"click", handle_cu_control_change, false);
    display_map();
}

// @license-end
