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

package net.kenevans.heartnotes;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.core.app.ActivityCompat;

/**
 * A class to provide location as well as location-based address and weather
 * information
 */
public class LocationUtils implements IConstants {
    private static final String WUND_URL_PREFIX = "http://m.wund" +
            ".com/cgi-bin/findweather/getForecast?query=";
    private static final String OPEN_WEATHER_MAP_API =
            "https://api.openweathermap.org/data/2.5/onecall" +
                    "?lat=%f&lon=%f&units=imperial" +
                    "&exclude=exclude=hourly,daily,minutely" +
                    "&appid=%s";

    /**
     * Gets the current location first trying GPS then Network.
     *
     * @param context The context.
     * @return The Location.
     */
    public static Location findLocation(Context context) {
        Location location;
        LocationManager locationManager = (LocationManager) context
                .getSystemService(Context.LOCATION_SERVICE);
        String gpsProvider = LocationManager.GPS_PROVIDER;
        String networkProvider = LocationManager.NETWORK_PROVIDER;
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        location = locationManager.getLastKnownLocation(gpsProvider);
        if (location == null) {
            location = locationManager.getLastKnownLocation(networkProvider);
        }
        return location;
    }

    /**
     * Finds an address from the given latitude and longitude.  Needs a
     * project with a billing account and the Geocoding API enabled.
     * See https://developers.google.com/maps/gmp-get-started.
     *
     * @param context The context.
     * @param lat     The latitude.
     * @param lon     The longitude.
     * @return The address.
     */
    public static String getAddressFromLocation(Context context, double lat,
                                                double lon) {
        String address = "";
        String addrLine;
        Geocoder geoCoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geoCoder.getFromLocation(lat, lon, 1);
            if (addresses.size() > 0) {
                for (int i = 0; i < addresses.get(0).getMaxAddressLineIndex()
                        ; i++) {
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
     * Gets a weather String for OpenWeatherMap.
     *
     * @param context The context.
     * @return The weather String.
     */
    public static String getOpenWeather(Context context) {
        Log.d(TAG, "LocationUtils " + ".getOpenWeather: ");
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        // Get the location
        Location location = findLocation(context);
        if (location == null) {
            Log.d(TAG, "  location=null");
            return "Failed to find location for weather.";
        }
        Log.d(TAG,
                "  location=" + location.getLatitude() + ","
                        + location.getLongitude());
        // Get the OpenWeather key
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences
                        (context);
        String key = prefs.getString(PREF_OPENWEATHER_KEY, null);
        if (key == null || key.isEmpty()) {
            Log.d(TAG, "  no key");
            return "No OpenWeather Key found.";
        }

        try {
            URL url = new URL(String.format(Locale.US, OPEN_WEATHER_MAP_API,
                    location.getLatitude(), location.getLongitude(), key));
            Log.d(TAG, "  url=" + url.getFile());
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Log.d(TAG, "  response code not 200");
                return "Get weather failed: responseCode=" + responseCode + ".";
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            StringBuilder json = new StringBuilder(1024);
            String line;
            while ((line = reader.readLine()) != null)
                json.append(line).append("\n");
            reader.close();
            JSONObject data = new JSONObject(json.toString());
            return parseOpenWeather(data) + ".";
        } catch (Exception ex) {
            Log.d(TAG, "  exception");
            return "Get weather failed: exception=" + ex + ".";
        }
    }

    /**
     * Parses the JSONObject from OpenWeatherMap.
     *
     * @param json The JSONObject to parse.
     * @return The String generated from the JSONObject.
     */
    private static String parseOpenWeather(JSONObject json) {
        String info = "";
        JSONObject current;
        String strVal;
        double val;
        try {
            current = json.getJSONObject("current");
        } catch (Exception ex) {
            info += "Current weather not found";
            return info;
        }
        boolean first = true;
        try {
            val = current.getDouble("temp");
            strVal = String.format(Locale.US, "%.0f", val);
            info += "temp=" + strVal + "°F";
            first = false;
        } catch (Exception ex) {
        }
        if (!first) info += " ";
        try {
            val = current.getDouble("feels_like");
            strVal = String.format(Locale.US, "%.0f", val);
            info += "(feels like " + strVal + "°F)";
        } catch (Exception ex) {
        }
        if (!first) info += " ";
        try {
            strVal = current.getString("humidity");
            info += "humidity=" + strVal + "%";
        } catch (Exception ex) {
        }
        if (!first) info += " ";
        try {
            long timeval = 1000L * current.getLong("dt");
            Date date = null;
            if (timeval != 0) {
                date = new Date(timeval);
            }
            info += "on " + date;
        } catch (Exception ex) {
        }
        if (first) info += "No data found";
        return info;
    }

    /**
     * Gets the weather using Weather Underground. Not working in 2020.
     *
     * @param context The context.
     * @return String[3] as {temperature, humidity, city} or null on failure.
     */
    public static String[] getWundWeather(Context context) {
        Log.d(TAG, "LocationUtils " + ".getWundWeather: ");
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Location location = findLocation(context);
        if (location == null) {
            Log.d(TAG, "  location=null");
            return null;
        }
        Log.d(TAG,
                "  location=" + location.getLatitude() + ","
                        + location.getLongitude());
        return getWundTemperatureHumidityFromLocation(location);
    }

    /**
     * Finds the temperature, humidity, and city for the given address. Uses
     * "https://www.google.com/ig/api?weather=". Must not be called from the
     * main thread.
     *
     * @param location The Location.
     * @return String[3] as {temperature, humidity, city} or null on failure.
     */
    public static String[] getWundTemperatureHumidityFromLocation(
            Location location) {
        // Parse the contents
        String temp = "";
        String humidity = "";
        String city = "";
        boolean tempFound = false;
        boolean humidityFound = false;
        boolean cityFound = false;

        try {
            String queryString = WUND_URL_PREFIX + location.getLatitude() + ","
                    + location.getLongitude();
            // Debug (Homer Glen)
            // queryString = WUND_URL_PREFIX + 41.593666 + "," + -87.946309;
            // queryString = WUND_URL_PREFIX + "homer+glen%2C+il";
            Log.d(TAG, "  queryString=" + queryString);
            URL url = new URL(queryString);

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    url.openStream()));
            StringBuilder sb = new StringBuilder();

            // Concatenate the lines into a single string
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                // Stop when the line contains this
                if (line.contains("<td>Conditions</td>")) {
                    break;
                }
            }
            br.close();
            String contents = sb.toString();

            String regex = "Temperature.*?<b>(.+?)</b>";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(contents);
            if (matcher.find()) {
                tempFound = true;
                temp = matcher.group(1);
            }

            regex = "Humidity.*?<b>(.+?)%</b>";
            pattern = Pattern.compile(regex);
            matcher = pattern.matcher(contents);
            if (matcher.find()) {
                humidityFound = true;
                humidity = matcher.group(1);
            }

            regex = "Observed.*?<b>(.+?)</b>";
            pattern = Pattern.compile(regex);
            matcher = pattern.matcher(contents);
            if (matcher.find()) {
                cityFound = true;
                // Remove the state part
                // Could be e.g. "Drake Subdivision, Lockport, Illinois"

                // Note that replace does nothing, replaceAll replaces every
                // thing after all commas, not just the last comma
                // city = matcher.group(1).replaceAll(",.*?$", "");

                // Do it this way
                city = matcher.group(1);
                Log.d(TAG, "full city=|" + city + "|");
                regex = "(.*),";
                pattern = Pattern.compile(regex);
                matcher = pattern.matcher(city);
                if (matcher.find()) {
                    city = matcher.group(1);
                }
            }
        } catch (Exception ex) {
            Log.d(TAG, "  getWundTemperatureHumidityFromLocation: Exception: "
                    + ex);
            // Do nothing
        }

        String[] vals = new String[3];
        vals[0] = tempFound ? temp + "?" : "NA";
        vals[1] = humidityFound ? humidity + "%" : "NA";
        vals[2] = cityFound ? city : "NA";
        Log.d(TAG, "tempFound=" + tempFound + " val=" + vals[0]);
        Log.d(TAG, "humidityFound=" + humidityFound + " val=" + vals[1]);
        Log.d(TAG, "cityFound=" + cityFound + " val=" + vals[2]);

        return vals;
    }

}
