import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class PeachPriceClient {
    private PeachPriceClient() {}

    // Call on a background thread.
    public static JSONObject fetch(String baseUrl, String marketCode, String boxKg,
                                   String gradeCode) throws IOException, JSONException {
        Uri.Builder endpoint = Uri.parse(baseUrl).buildUpon()
                .appendPath("peach")
                .appendPath("live")
                .appendQueryParameter("marketCode", marketCode)
                .appendQueryParameter("boxKg", boxKg);
        if (gradeCode != null && !gradeCode.isEmpty()) {
            endpoint.appendQueryParameter("gradeCode", gradeCode);
        }

        HttpURLConnection connection =
                (HttpURLConnection) new URL(endpoint.build().toString()).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IOException("Price server returned HTTP " + status);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                return new JSONObject(json.toString());
            }
        } finally {
            connection.disconnect();
        }
    }
}
