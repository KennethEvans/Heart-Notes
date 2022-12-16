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
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Locale;

import androidx.core.app.ActivityCompat;

/**
 * A class to provide location as well as location-based address and weather
 * information
 */
public class LocationUtils implements IConstants {
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
     * Gets a weather String for OpenWeatherMap.
     *
     * @param activityRef The WeakReference to the activity
     * @return The weather String.
     */
    public static String getOpenWeather(WeakReference<Activity> activityRef) {
        Log.d(TAG, "LocationUtils " + ".getOpenWeather: ");
        if (ActivityCompat.checkSelfPermission(activityRef.get(),
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(activityRef.get(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        // Get the location
        Location location = findLocation(activityRef.get());
        if (location == null) {
            Log.d(TAG, "  location=null");
            return "Failed to find location for weather.";
        }
        Log.d(TAG,
                "  location=" + location.getLatitude() + ","
                        + location.getLongitude());
        // Get the OpenWeather key
        SharedPreferences prefs = activityRef.get().getSharedPreferences(
                "HeartNotesActivity", Context.MODE_PRIVATE);
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

}
