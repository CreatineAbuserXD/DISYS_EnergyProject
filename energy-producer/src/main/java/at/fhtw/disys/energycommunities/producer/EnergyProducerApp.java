package at.fhtw.disys.energycommunities.producer;

import at.fhtw.disys.energycommunities.shared.config.RabbitMQConfig;
import at.fhtw.disys.energycommunities.shared.dto.EnergyMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public class EnergyProducerApp {

    public static void main(String[] args) throws Exception {
        System.out.println("Energy Producer started.");

        // API-Abfrage um das cloud cover auf der FH zu bekommen
        WeatherClient weatherClient = new WeatherClient();
        double cloudCover = weatherClient.getCloudCover();
        System.out.println("Cloud cover is " + cloudCover + "%");
        long lastWeatherFetch = System.currentTimeMillis();   // die letzte Abfrage merken --> für Vergleich, wann wieder neue gemacht werden muss

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Verbindung zu RabbitMQ herstellen (siehe auch shared --> RabbitMQConfig)
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RabbitMQConfig.HOST);
        factory.setPort(RabbitMQConfig.PORT);

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(RabbitMQConfig.EXCHANGE_NAME, "direct", true);
        channel.queueDeclare(RabbitMQConfig.QUEUE_ENERGY, true, false, false, null);
        channel.queueBind(RabbitMQConfig.QUEUE_ENERGY, RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ENERGY);

        // infinite loop, Messages immer schicken, wenn ausgeführt
        while (true) {
            // die API liefert ja alle 15 Minuten neue Daten ("interval": 900), daher hier checken, ob ein neuer Request nötig ist
            if (System.currentTimeMillis() - lastWeatherFetch > 15 * 60 * 1000) {
                cloudCover = weatherClient.getCloudCover();
                lastWeatherFetch = System.currentTimeMillis();
                System.out.println("Refreshed cloud cover: " + cloudCover + "%");
            }

            // der sunFactor hängt vom cloud cover ab, zusätzlich wurde in der Spezifikation noch ein random Wert gefordert
            double sunFactor = 0.2 + 0.8 * (100.0 - cloudCover) / 100.0;
            double jitter = 0.8 + Math.random() * 0.4;
            double kwh = 0.005 * sunFactor * jitter; // hier noch mit einem "realistischen" wert skalieren

            EnergyMessage message = new EnergyMessage("PRODUCER", "COMMUNITY", kwh, LocalDateTime.now());

            String json = objectMapper.writeValueAsString(message);
            channel.basicPublish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ENERGY,
                    null, json.getBytes(StandardCharsets.UTF_8));
            System.out.println("Sent: " + json);

            // erfüllt: "random 1-5 second intervals"
            int waitMillis = 1000 + (int) (Math.random() * 4000);
            Thread.sleep(waitMillis);
        }
    }
}
