package at.fhtw.disys.energycommunities.producer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WeatherClient {

    // this takes vienna as starting point
    private static final String WEATHER_API = "https://api.open-meteo.com/v1/forecast?latitude=48.21&longitude=16.37&current=cloud_cover";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Returns the current cloud cover in Vienna (0-100%).
    // If the API call fails, returns 50% so the producer keeps running.
    public double getCloudCover() {
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WEATHER_API))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("current").path("cloud_cover").asDouble();
        } catch (Exception e) {
            System.out.println("Weather API call failed, using default cloud cover of 50%.");
            return 50.0;
        }
    }

    // lets you run this file on its own to check the weather API works
    public static void main(String[] args) {
        System.out.println("Cloud cover: " + new WeatherClient().getCloudCover() + "%");
    }
}
