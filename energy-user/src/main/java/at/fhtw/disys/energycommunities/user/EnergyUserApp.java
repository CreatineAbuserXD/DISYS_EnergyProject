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

        // Set up the tool that turns our message object into JSON text.
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());                 // needed so it can write the datetime
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // write the datetime as a readable text

        // Connect to RabbitMQ.
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RabbitMQConfig.HOST);
        factory.setPort(RabbitMQConfig.PORT);

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // Make sure the exchange and the energy queue exist and are linked together.
        // Same queue and routing key as the producer: both feed the usage service.
        channel.exchangeDeclare(RabbitMQConfig.EXCHANGE_NAME, "direct", true);
        channel.queueDeclare(RabbitMQConfig.QUEUE_ENERGY, true, false, false, null);
        channel.queueBind(RabbitMQConfig.QUEUE_ENERGY, RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ENERGY);

        // Keep sending usage messages forever (stop with Ctrl+C).
        while (true) {
            // PHASE 1: fixed placeholder value. Gets replaced by the time-of-day logic in phase 2.
            double kwh = 0.005;

            EnergyMessage message = new EnergyMessage("USER", "COMMUNITY", kwh, LocalDateTime.now());

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
