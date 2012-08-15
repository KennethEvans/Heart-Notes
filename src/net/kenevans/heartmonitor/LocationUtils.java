//Copyright (c) 2011 Kenneth Evans
//
//Permission is hereby granted, free of charge, to any person obtaining
//a copy of this software and associated documentation files (the
//"Software"), to deal in the Software without restriction, including
//without limitation the rights to use, copy, modify, merge, publish,
//distribute, sublicense, and/or sell copies of the Software, and to
//permit persons to whom the Software is furnished to do so, subject to
//the following conditions:
//
//The above copyright notice and this permission notice shall be included
//in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
//MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
//IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
//CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
//TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.kenevans.heartmonitor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Locale;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

/**
 * A class to provide location as well as location-based address and weather
 * information
 * 
 */
public class LocationUtils implements IConstants {

	/**
	 * Gets the temperature from the current location.
	 * 
	 * @param context
	 * @return
	 */
	public static String getTempFromLocation(Context context) {
		Log.d(TAG, "LocationUtils " + ".getTempFromLocation: ");
		Location location = findLocation(context);
		Log.d(TAG, "  location=" + location);
		if (location == null) {
			return "Location NA";
		}
		String addr = getAddressFromLocation(context, location.getLatitude(),
				location.getLongitude());
		Log.d(TAG, "  addr=" + addr);
		if (addr == null) {
			return "Address NA";
		}
		String temp = getTemperatureFromAddress(addr);
		Log.d(TAG, "  temp=" + temp);
		if (temp == null) {
			return "NA";
		}
		return temp;
	}

	/**
	 * Gets the temperature and humidity from the current location.
	 * 
	 * @param context
	 * @return String[2] as {temperature, humidity} or null on failure.
	 */
	public static String[] getTemperatureHumidityFromLocation(Context context) {
		Log.d(TAG, "LocationUtils " + ".getTempHumidityFromLocation: ");
		Location location = findLocation(context);
		Log.d(TAG, "  location=" + location);
		if (location == null) {
			return new String[] { "Temp: Location NA", "Humidity: Location NA" };
		}
		String addr = getAddressFromLocation(context, location.getLatitude(),
				location.getLongitude());
		Log.d(TAG, "  addr=" + addr);
		if (addr == null) {
			return new String[] { "Temp: Address NA", "Humidity: Address NA" };
		}
		String[] vals = getTemperatureHumidityFromAddress(addr);
		Log.d(TAG, "  vals=" + vals);
		return vals;
	}

	/**
	 * Gets the temperature from the location. Not finished.
	 * 
	 * @param context
	 * @return
	 */
	@Deprecated
	public static String getTempFromLocation1(Context context) {
		String retVal = "NA";

		// Get the location
		LocationManager locationManager = (LocationManager) context
				.getSystemService(Context.LOCATION_SERVICE);
		String gpsProvider = LocationManager.GPS_PROVIDER;
		String networkProvider = LocationManager.NETWORK_PROVIDER;
		Location location = locationManager.getLastKnownLocation(gpsProvider);
		if (location == null) {
			location = locationManager.getLastKnownLocation(networkProvider);
		}
		if (location == null) {
			return "Location NA";
		}

		// Get the address from the location
		Geocoder geocoder = new Geocoder(context, Locale.ENGLISH);
		List<Address> addresses = null;
		String zipCode = null;
		try {
			addresses = geocoder.getFromLocation(location.getLatitude(),
					location.getLongitude(), 3);
			for (int i = 0; i < addresses.size(); i++) {
				Address address = addresses.get(i);
				if (address.getPostalCode() != null) {
					zipCode = address.getPostalCode();
					break;
				}
			}
		} catch (IOException ex) {
			// Do nothing
		}
		if (zipCode == null) {
			return "Address NA";
		}

		return retVal;
	}

	/**
	 * Gets the current location first trying GPS then Network.
	 * 
	 * @param context
	 * @return
	 */
	public static Location findLocation(Context context) {
		Location location = null;
		LocationManager locationManager = (LocationManager) context
				.getSystemService(Context.LOCATION_SERVICE);
		String gpsProvider = LocationManager.GPS_PROVIDER;
		String networkProvider = LocationManager.NETWORK_PROVIDER;
		location = locationManager.getLastKnownLocation(gpsProvider);
		if (location == null) {
			location = locationManager.getLastKnownLocation(networkProvider);
		}
		return location;
	}

	/**
	 * Gets the current location by checking all providers and using the first
	 * one that is successful.
	 * 
	 * @param context
	 * @return
	 */
	public static Location findLocation1(Context context) {
		Location location = null;
		String location_context = Context.LOCATION_SERVICE;
		LocationManager locationManager = (LocationManager) context
				.getSystemService(location_context);

		List<String> providers = locationManager.getProviders(true);
		for (String provider : providers) {
			// locationManager.requestLocationUpdates(provider, 1000, 0,
			// new LocationListener() {
			// public void onLocationChanged(Location location) {
			// }
			//
			// public void onProviderDisabled(String provider) {
			// }
			//
			// public void onProviderEnabled(String provider) {
			// }
			//
			// public void onStatusChanged(String provider,
			// int status, Bundle extras) {
			// }
			// });
			location = locationManager.getLastKnownLocation(provider);
			if (location != null) {
				break;
			}
		}
		return location;
	}

	/**
	 * Finds an address form the given latitude and longitude.
	 * 
	 * @param context
	 * @param lat
	 * @param lon
	 * @return
	 */
	public static String getAddressFromLocation(Context context, double lat,
			double lon) {
		String address = "";
		String addrLine = null;
		Geocoder geoCoder = new Geocoder(context, Locale.getDefault());
		try {
			List<Address> addresses = geoCoder.getFromLocation(lat, lon, 1);
			if (addresses.size() > 0) {
				for (int i = 0; i < addresses.get(0).getMaxAddressLineIndex(); i++) {
					addrLine = addresses.get(0).getAddressLine(i);
					Log.d(TAG, "  addrLine=" + addrLine);
					if (addrLine != null) {
						address += addrLine + " ";
					}
				}
			}
		} catch (IOException ex) {
			// Do nothing
		}
		return address;
	}

	/**
	 * Finds the temperature for the given address. Uses
	 * "https://www.google.com/ig/api?weather=". Must not be called from the
	 * main thread.
	 * 
	 * @param address
	 * @return
	 */
	private static String getTemperatureFromAddress(String address) {
		String temp = null;
		try {
			address = address.replace(" ", "%20");
			String queryString = "https://www.google.com/ig/api?weather="
					+ address;
			Log.d(TAG, "  queryString=" + queryString);
			URL url = new URL(queryString);

			URLConnection conn = url.openConnection();
			Log.d(TAG, "  conn=" + conn);
			InputStream is = conn.getInputStream();
			Log.d(TAG, "  is=" + is);
			XmlPullParser xpp = XmlPullParserFactory.newInstance()
					.newPullParser();
			Log.d(TAG, "  xpp=" + xpp);
			xpp.setInput(is, null);
			int eventType = xpp.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if (eventType == XmlPullParser.START_TAG) {
					String elementName = xpp.getName();
					Log.d(TAG, "  elementName=" + elementName);
					// temp_c is Centigrade and temp_f is Fahrenheit
					if (elementName.equals("temp_f")) {
						int attrCount = xpp.getAttributeCount();
						for (int i = 0; i < attrCount; i++) {
							// xpp.getAttributeValue(i);
							temp = xpp.getAttributeValue(i);
							if (temp != null) {
								break;
							}
						}
					}
				}
				eventType = xpp.next();
			}
		} catch (Exception ex) {
			Log.d(TAG, "  getTemperatureFromAddress: Exception: " + ex);
			// Do nothing
		}
		return temp;
	}

	/**
	 * Finds the temperature, humidity, and city for the given address. Uses
	 * "https://www.google.com/ig/api?weather=". Must not be called from the
	 * main thread.
	 * 
	 * @param address
	 * @return String[3] as {temperature, humidity, city} or null on failure.
	 */
	private static String[] getTemperatureHumidityFromAddress(String address) {
		String temp = null;
		String humidity = null;
		String city = null;
		boolean tempFound = false;
		boolean humidityFound = false;
		boolean cityFound = false;
		try {
			address = address.replace(" ", "%20");
			String queryString = "https://www.google.com/ig/api?weather="
					+ address;
			Log.d(TAG, "  queryString=" + queryString);
			URL url = new URL(queryString);

			URLConnection conn = url.openConnection();
			Log.d(TAG, "  conn=" + conn);
			InputStream is = conn.getInputStream();
			Log.d(TAG, "  is=" + is);
			XmlPullParser xpp = XmlPullParserFactory.newInstance()
					.newPullParser();
			Log.d(TAG, "  xpp=" + xpp);
			xpp.setInput(is, null);
			int eventType = xpp.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if (eventType == XmlPullParser.START_TAG) {
					String elementName = xpp.getName();
					// temp_c is Centigrade and temp_f is Fahrenheit
					if (!tempFound && elementName.equals("temp_f")) {
						int attrCount = xpp.getAttributeCount();
						for (int i = 0; i < attrCount; i++) {
							// xpp.getAttributeValue(i);
							temp = xpp.getAttributeValue(i);
							if (temp != null) {
								tempFound = true;
							}
						}
					}
					if (!humidityFound && elementName.equals("humidity")) {
						int attrCount = xpp.getAttributeCount();
						for (int i = 0; i < attrCount; i++) {
							// xpp.getAttributeValue(i);
							humidity = xpp.getAttributeValue(i);
							if (humidity != null) {
								humidityFound = true;
							}
						}
					}
					if (!cityFound && elementName.equals("city")) {
						int attrCount = xpp.getAttributeCount();
						for (int i = 0; i < attrCount; i++) {
							// xpp.getAttributeValue(i);
							city = xpp.getAttributeValue(i);
							if (city != null) {
								cityFound = true;
							}
						}
					}
					if (tempFound == true && humidityFound == true
							&& cityFound == true) {
						break;
					}
				}
				eventType = xpp.next();
			}
		} catch (Exception ex) {
			Log.d(TAG, "  getTemperatureHumidityFromAddress: Exception: " + ex);
			// Do nothing
		}
		String[] vals = new String[3];
		vals[0] = tempFound ? "Temp: " + temp + "°" : "Temp: NA";
		vals[1] = humidityFound ? humidity : "Humidity: NA";
		vals[2] = cityFound ? city : "City: NA";
		return vals;
	}

}
