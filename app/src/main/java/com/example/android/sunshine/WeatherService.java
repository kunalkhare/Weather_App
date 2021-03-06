package com.example.android.sunshine;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcel;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.text.format.Time;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Created by Kunal on 5/18/2016.
 */
public class WeatherService extends AsyncTask<String, Void, String[]> {
    // Adding network code

    private HttpURLConnection urlConn = null;
    private BufferedReader reader = null;
    private String jsonString = null;
    private final String LOG_TAG = WeatherService.class.getSimpleName();
    String format = "json";
    String units = "metric";
    int numDays = 7;

    private String getReadableDateString(long time){
        // Because the API returns a unix timestamp (measured in seconds),
        // it must be converted to milliseconds in order to be converted to valid date.
        SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
        return shortenedDateFormat.format(time);
    }

    private String formatHighLows(double high, double low) {
        // For presentation, assume the user doesn't care about tenths of a degree.
        long roundedHigh = Math.round(high);
        long roundedLow = Math.round(low);

        String highLowStr = roundedHigh + "/" + roundedLow;
        return highLowStr;
    }

    private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
            throws JSONException {

        // These are the names of the JSON objects that need to be extracted.
        final String OWM_LIST = "list";
        final String OWM_WEATHER = "weather";
        final String OWM_TEMPERATURE = "temp";
        final String OWM_MAX = "max";
        final String OWM_MIN = "min";
        final String OWM_DESCRIPTION = "main";

        JSONObject forecastJson = new JSONObject(forecastJsonStr);
        JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

        // OWM returns daily forecasts based upon the local time of the city that is being
        // asked for, which means that we need to know the GMT offset to translate this data
        // properly.

        // Since this data is also sent in-order and the first day is always the
        // current day, we're going to take advantage of that to get a nice
        // normalized UTC date for all of our weather.

        Time dayTime = new Time();
        dayTime.setToNow();

        // we start at the day returned by local time. Otherwise this is a mess.
        int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

        // now we work exclusively in UTC
        dayTime = new Time();

        String[] resultStrs = new String[numDays];
        for(int i = 0; i < weatherArray.length(); i++) {
            // For now, using the format "Day, description, hi/low"
            String day;
            String description;
            String highAndLow;

            // Get the JSON object representing the day
            JSONObject dayForecast = weatherArray.getJSONObject(i);

            // The date/time is returned as a long.  We need to convert that
            // into something human-readable, since most people won't read "1400356800" as
            // "this saturday".
            long dateTime;
            // Cheating to convert this to UTC time, which is what we want anyhow
            dateTime = dayTime.setJulianDay(julianStartDay+i);
            day = getReadableDateString(dateTime);

            // description is in a child array called "weather", which is 1 element long.
            JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
            description = weatherObject.getString(OWM_DESCRIPTION);

            // Temperatures are in a child object called "temp".  Try not to name variables
            // "temp" when working with temperature.  It confuses everybody.
            JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
            double high = temperatureObject.getDouble(OWM_MAX);
            double low = temperatureObject.getDouble(OWM_MIN);

            highAndLow = formatHighLows(high, low);
            resultStrs[i] = day + " - " + description + " - " + highAndLow;
        }

        for (String s : resultStrs) {
            Log.v(LOG_TAG, "Forecast entry: " + s);
        }
        return resultStrs;

    }

    @Override
    protected  String[] doInBackground(String...params){
        final String W_KEY="3614b2e78ef7c2c791bc4d81eab61a7b";
        final String weatherUrl = "http://api.openweathermap.org/data/2.5/forecast/daily?";
        final String ZIP_PARAM = "q";
        final String MODE_PARAM = "mode";
        final String UNITS_PARAM = "units";
        final String DAYS_PARAM = "cnt";
        final String APPID_PARAM = "APPID";
       // final String apiKey = "&APPID="+W_KEY;
        String weatherData[];

        try
        {
            Uri buildUrl = Uri.parse(weatherUrl).buildUpon()
                    .appendQueryParameter(ZIP_PARAM,params[0])
                    .appendQueryParameter(MODE_PARAM,format)
                    .appendQueryParameter(UNITS_PARAM,units)
                    .appendQueryParameter(DAYS_PARAM,Integer.toString(numDays))
                    .appendQueryParameter(APPID_PARAM,W_KEY)
                    .build();

            URL url = new URL(buildUrl.toString());

            Log.v(LOG_TAG,"FORECAST URL ######" + buildUrl.toString());

            //open connection
            urlConn = (HttpURLConnection)url.openConnection();
            urlConn.setRequestMethod("GET");
            urlConn.connect();

            InputStream inputStream = urlConn.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if(inputStream==null){
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            while((line = reader.readLine())!=null){
                buffer.append(line + "\n");
            }

            if(buffer.length()==0){
                return null;
            }
            jsonString = buffer.toString();
            weatherData = getWeatherDataFromJson(jsonString,numDays);
            Log.v(LOG_TAG,"FORECAST JSON ######" + jsonString);

        }catch (Exception e){

            Log.e(LOG_TAG, "Error", e);
            return null;
        }finally {
            if(urlConn!=null){
                urlConn.disconnect();
            }
            if(reader!=null){
                try{
                    reader.close();
                }catch(final IOException e){
                    Log.e(LOG_TAG, "Error closing reader", e);
                }
            }
        }
        return  weatherData;
    }

    @Override
    protected void onPostExecute(String[] result) {
        MainActivityFragment fragment=new MainActivityFragment();
        if(result!=null)
        {
            fragment.getAdapter().clear();
            for(String dayForecast: result)
            {
                fragment.getAdapter().add(dayForecast);
            }
        }
    }
}

