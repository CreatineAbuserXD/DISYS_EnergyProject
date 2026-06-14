package at.fhtw.disys.energycommunities.producer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WeatherClient {

    // wir haben uns einfach für open-meteo entschieden, siehe auch project_specification
    // cloud_cover liefert tw. sehr viel coverage (somit wenig produktion), laut internet sind aber vor allem die
    // niedrigen wolken ausschlaggebend --> daher wurde auf endpunkt cloud_cover_low umgestellt
    // koordinaten sind ca. die fh (api rundet immer auf 2 nachkommastellen, daher nicht genau perfekt aber gut genug)
    private static final String WEATHER_API = "https://api.open-meteo.com/v1/forecast?latitude=48.24&longitude=16.38&current=cloud_cover_low";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public double getCloudCover() {
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WEATHER_API))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("current").path("cloud_cover_low").asDouble();
        } catch (Exception e) {
            System.out.println("API-call-Fehler: Für die Berechnung wird 50% cloud-cover angenommen. Überprüfe WEATHER_API!");
            return 50.0;
        }
    }
}
