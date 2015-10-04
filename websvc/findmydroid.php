<?php

header('Content-type: application/json');
define('GCLOUD', false);

if (GCLOUD)
{
	define('HAVE_FLOCK', false);
	define('TEMPDIR', 'gs://findmydroid-1086.appspot.com/');
	define('FILEMODE_X', 'w');
}
else
{
	define('HAVE_FLOCK', true);
	define('TEMPDIR', '/tmp/');
	define('FILEMODE_X', 'x');
}

function gen_cookie()
{
	$chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
	$chars_len = strlen($chars) - 1;
	$cookie = '';
	for ($i = 0; $i < 16; $i++) 
	{
		$cookie .= $chars[rand(0, $chars_len)];
	}
	return $cookie;
}

function get_cookie_filename($cookie)
{
	return TEMPDIR . $cookie;
}

function begin_session()
{
	# TODO: We need to log the IP address in order
	# to prevent DoS attack

	$retries = 0;
	do
	{
		do
		{
			$cookie = gen_cookie();
			$filename = get_cookie_filename($cookie);
		}
		while (file_exists($filename));
		$f = fopen($filename, FILEMODE_X);
		$retries++;
	}
	while (!$f && $retries < 4);

	if (!$f)
	{
		$result["result"] = "FAIL";
		$result["reason"] = "Cannot open file: " . $filename;
	}
	else if (!HAVE_FLOCK || flock($f, LOCK_EX))
	{
		fwrite($f, "0.00\n0.00\n");
		fclose($f);
		$result["result"] = "OK";
		$result["cookie"] = $cookie;
	}
	else
	{
		fclose($f);
		$result["result"] = "FAIL";
		$result["reason"] = "Cannot lock file.";
	}
	return $result;
}

function set_location($cookie, $latitude, $longitude)
{
	$filename = get_cookie_filename($cookie);
	if (!file_exists($filename))
	{
		$result["result"] = "FAIL";
		$result["reason"] = "File does not exist.";
		return $result;
	}
	else
	{
		$f = fopen($filename, 'w');
		if ($f == FALSE)
		{
			$result["result"] = "FAIL";
			$result["reason"] = "Invalid cookie";
		}
		else if (!HAVE_FLOCK || flock($f, LOCK_EX))
		{
			fwrite($f, $latitude . "\n");
			fwrite($f, $longitude . "\n");
			fclose($f);
			$result["result"] = "OK";
		}
		else
		{
			fclose($f);
			$result["result"] = "FAIL";
			$result["reason"] = "Could not lock cookie.";
		}
	}
	return $result;
}

function get_location($cookie)
{
	$filename = get_cookie_filename($cookie);
	if (!file_exists($filename))
	{
		$result["result"] = "FAIL";
		$result["reason"] = "Invalid session cookie";
	}
	else
	{
		$f = fopen($filename, 'r');
		if ($f == FALSE)
		{
			$result["result"] = "FAIL";
			$result["reason"] = "Could not open file";
		}
		else if (!HAVE_FLOCK || flock($f, LOCK_EX))
		{
			$result["result"] = "OK";
			$result["latitude"] = trim(fgets($f));
			$result["longitude"] = trim(fgets($f));
			fclose($f);
		}
		else
		{
			fclose($f);
			$result["result"] = "FAIL";
			$result["reason"] = "Could not lock file";
		}
	}
	return $result;
}

function end_session($cookie)
{
	$filename = get_cookie_filename($cookie);
	if (file_exists($filename))
	{
		unlink($filename);
		$result["result"] = "OK";
	}
	else
	{
		$result["result"] = "FAIL";
		$result["reason"] = "Invalid session cookie";
	}
	return $result;
}

if (isset($_REQUEST["action"]))
{
	if ($_REQUEST["action"] == "begin_session")
	{
		echo json_encode(begin_session());
		exit();
	}
	else if ($_REQUEST["action"] == "set_location")
	{
		if (isset($_REQUEST["cookie"]) &&
			isset($_REQUEST["latitude"]) && 
			isset($_REQUEST["longitude"]))
		{
			echo json_encode(set_location(
				$_REQUEST["cookie"],
				$_REQUEST["latitude"],
				$_REQUEST["longitude"]));
			exit();
		}		
	}
	else if ($_REQUEST["action"] == "get_location")
	{
		if (isset($_REQUEST["cookie"]))
		{
			echo json_encode(get_location($_REQUEST["cookie"]));
			exit();
		}
	}
	else if ($_REQUEST["action"] == "end_session")
	{
		if (isset($_REQUEST["cookie"]))
		{
			echo json_encode(end_session($_REQUEST["cookie"]));
			exit();
		}
	}
}

$result["result"] = "FAIL";
$result["reason"] = "Invalid request";
echo json_encode($result);

?>
