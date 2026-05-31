package at.fhtw.disys.energycommunities.user;

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

public class EnergyUserApp {

    public static void main(String[] args) throws Exception {
        System.out.println("Energy User started.");

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
            // die folgenden Werte sind reine Annahmen (grobe Werte wurden gesucht, keine genau auf Wien bezogenen)
            int hour = LocalDateTime.now().getHour();
            double base;
            if (hour >= 6 && hour <= 9) {
                base = 0.010;
            } else if (hour >= 17 && hour <= 21) {
                base = 0.0125;
            } else if (hour >= 22 || hour <= 5) {
                base = 0.002;
            } else {
                base = 0.0055;
            }

            double jitter = 0.8 + Math.random() * 0.4;
            double kwh = base * jitter;

            EnergyMessage message = new EnergyMessage("USER", "COMMUNITY", kwh, LocalDateTime.now());

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
