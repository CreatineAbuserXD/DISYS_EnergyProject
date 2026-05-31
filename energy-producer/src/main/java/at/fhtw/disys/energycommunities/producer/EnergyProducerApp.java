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

        // 2. Set up the tool that turns our message object into JSON text.
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());                 // needed so it can write the datetime
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // write the datetime as a readable text

        // 3. Connect to RabbitMQ.
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RabbitMQConfig.HOST);
        factory.setPort(RabbitMQConfig.PORT);

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // Make sure the exchange and the energy queue exist and are linked together.
        channel.exchangeDeclare(RabbitMQConfig.EXCHANGE_NAME, "direct", true);
        channel.queueDeclare(RabbitMQConfig.QUEUE_ENERGY, true, false, false, null);
        channel.queueBind(RabbitMQConfig.QUEUE_ENERGY, RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ENERGY);

        // 4. Keep sending production messages forever (stop with Ctrl+C).
        while (true) {
            // More sun (less cloud) means more energy. sunFactor is 1.0 at clear sky and still
            // 0.2 when fully overcast, because a solar panel always produces a little.
            double sunFactor = 0.2 + 0.8 * (100.0 - cloudCover) / 100.0;
            // A random factor between 0.8 and 1.2 so the value is not always the same.
            double jitter = 0.8 + Math.random() * 0.4;
            // Base production of 0.005 kWh per message, scaled by sun and randomness.
            double kwh = 0.005 * sunFactor * jitter;

            EnergyMessage message = new EnergyMessage("PRODUCER", "COMMUNITY", kwh, LocalDateTime.now());

            String json = objectMapper.writeValueAsString(message);
            channel.basicPublish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ENERGY,
                    null, json.getBytes(StandardCharsets.UTF_8));
            System.out.println("Sent: " + json);

            // Wait a random time between 1 and 5 seconds before sending the next message.
            int waitMillis = 1000 + (int) (Math.random() * 4000);
            Thread.sleep(waitMillis);
        }
    }
}
