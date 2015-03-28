package com.pubkit.network;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by puran on 3/22/15.
 */
public final class PubKitNetwork {

    private static String PUBKIT_API_URL = "https://pubkit.co/push/register";

    public static JSONObject sendPost(String apiKey, JSONObject jsonObject) {
        URL url;
        HttpsURLConnection connection = null;
        try {
            //Create connection
            url = new URL(PUBKIT_API_URL);
            String encodedData = URLEncoder.encode(jsonObject.toString(), "UTF-8");
            String postData = "data=" + encodedData;

            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Length", "" + String.valueOf(postDataBytes.length));
            connection.setRequestProperty("api_key", apiKey);

            connection.setUseCaches(false);
            connection.setDoOutput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(postData);//set data
            wr.flush();

            //Get Response
            InputStream inputStream = connection.getErrorStream(); //first check for error.
            if (inputStream == null) {
                inputStream = connection.getInputStream();
            }
            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            StringBuilder response = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            wr.close();
            rd.close();

            String responseString = response.toString();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return new JSONObject(responseString);
            } else {
                try {
                    return new JSONObject(responseString);
                } catch (JSONException e) {
                    Log.e("NETWORK", "Error parsing data", e);
                }
            }
        } catch (Exception e) {
            Log.e("NETWORK", "Network exception:", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }
}
