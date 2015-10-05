var lat = 44.5403;
var lon= -78.5463;
var iconBase = 'http://fernando-rodriguez.github.io/FindMyDroid/images/';
var marker;
var map;
var pos;
var qstring;

(window.onpopstate = function () 
{
	var match,
		pl = /\+/g,  // Regex for replacing addition symbol with a space
		search = /([^&=]+)=?([^&]*)/g,
		decode = function (s)
		{
			return decodeURIComponent(s.replace(pl, " "));
		},
		query  = window.location.search.substring(1);

	qstring = {};
	while (match = search.exec(query))
		qstring[decode(match[1])] = decode(match[2]);
})();

function updateMap() 
{

	if (qstring["cookie"] == null)
		return;

	$.ajax({
		url: "http://findmydroid-1086.appspot.com/?action=get_location&cookie=" + qstring["cookie"]
	}).then(function (data) 
	{

		if (data.latitude == lat && data.longitude == lon)
			return;

		lat = data.latitude;
		lon = data.longitude;
		pos = new google.maps.LatLng(lat, lon);
		map.setCenter(pos);
		map.setZoom(18);
		marker.setPosition(pos);
		setTimeout(updateMap, 3000);
	});
}

function initialize()
{
	pos = new google.maps.LatLng(34.5403, -100.5463);
	var mapCanvas = document.getElementById('map');
	var mapOptions = 
	{
		center: pos,
		zoom: 2,
		mapTypeId: google.maps.MapTypeId.ROADMAP
	}
	map = new google.maps.Map(mapCanvas, mapOptions);
	marker = new google.maps.Marker({
		position: pos,
		map: map,
		icon: iconBase + 'droid.png'
	});
	setTimeout(updateMap, 10000);
}

google.maps.event.addDomListener(window, 'load', initialize);

