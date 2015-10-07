var lat = 44.5403;
var lon = -78.5463;
var iconBase = 'https://fernando-rodriguez.github.io/FindMyDroid/img/';
var svcUrl = 'https://findmydroid-1086.appspot.com';
var marker;
var map;
var pos;
var qstring;
var isConnected = false;
var isDisconnected = false;
var disconnectDiv;

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

function endSession()
{
	map.controls[google.maps.ControlPosition.TOP_RIGHT].pop(disconnectDiv);
	$.ajax({ url: svcUrl + '?action=end_session&cookie=' + qstring["cookie"] });
	pos = new google.maps.LatLng(34.5403, -100.5463);
	map.setCenter(pos);
	map.setZoom(2);
	marker.setMap(null);
	isDisconnected = true;
}

function updateMap() 
{
	if (isDisconnected == false)
	{
		$.ajax({
			url: svcUrl + "?action=get_location&cookie=" + qstring["cookie"]
		})
		.then(function (data) 
		{
			if (data.result != "OK")
			{
				//setTimeout(updateMap, 3000);
			}
			else if (data.latitude != lat || data.longitude != lon)
			{
				lat = data.latitude;
				lon = data.longitude;
				pos = new google.maps.LatLng(lat, lon);
				map.setCenter(pos);
				map.setZoom(18);
				marker.setPosition(pos);
				marker.setMap(map);
			}
			if (isConnected == false)
			{
				isConnected = true;
				map.controls[google.maps.ControlPosition.TOP_RIGHT].push(disconnectDiv);
			}
		});
	}
	setTimeout(updateMap, 3000);
}

function initialize()
{
	pos = new google.maps.LatLng(34.5403, -100.5463);
	var mapCanvas = document.getElementById('findmydroid');
	var mapOptions = 
	{
		center: pos,
		zoom: 2,
		mapTypeId: google.maps.MapTypeId.ROADMAP,
		mapTypeControl: false,
		streetViewControl: false,
		scrollwheel: false,
		draggable: false,
		zoomControl: false,
		disableDefaultUI: true
	}
	map = new google.maps.Map(mapCanvas, mapOptions);
	marker = new google.maps.Marker({
		position: pos,
		map: null,
		icon: iconBase + 'droid.png'
	});


	var ctlText = document.createElement('div');
	ctlText.style.color = 'rgb(25,25,25)';
	ctlText.style.fontFamily = 'Roboto,Arial,sans-serif';
	ctlText.style.fontSize = '12px';
	ctlText.style.lineHeight = '28px';
	ctlText.style.paddingLeft = '5px';
	ctlText.style.paddingRight = '5px';
	ctlText.innerHTML = 'Disconnect';

	var border = document.createElement('div');
	border.style.backgroundColor = '#fff';
	border.style.border = '1px solid #fff';
	border.style.cursor = 'pointer';
	border.style.marginTop = '10px';
	border.style.marginRight = '10px';
	border.style.textAlign = 'center';
	border.title = 'Click to disconnect';
	border.appendChild(ctlText);
	border.addEventListener('mouseover', function() {
		border.style.backgroundColor = '#ccc';
	});
	border.addEventListener('mouseout', function() {
		border.style.backgroundColor = '#fff';
	});
	border.addEventListener('click', endSession);

	disconnectDiv = document.createElement('div');
	disconnectDiv.index = 1;
	disconnectDiv.appendChild(border);

	if (qstring["cookie"] != null)
		setTimeout(updateMap, 5000);
}

google.maps.event.addDomListener(window, 'load', initialize);

var delayjs = (function(){
  var timer = 0;
  return function(callback, ms){
    clearTimeout (timer);
    timer = setTimeout(callback, ms);
  };
})();

$(window).resize(function () {
	delayjs(function () {
		if (map != null)
			map.setCenter(pos);
	}, 500);
});

