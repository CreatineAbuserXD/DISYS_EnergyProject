package at.fhtw.disys.energycommunities.percentage;

import at.fhtw.disys.energycommunities.shared.config.RabbitMQConfig;
import at.fhtw.disys.energycommunities.shared.dto.UpdateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;

public class PercentageServiceApp {

    public static void main(String[] args) throws Exception {
        System.out.println("Percentage Service started.");

        // JSON-Tool, um eine empfangene Nachricht zurueck in ein UpdateMessage-Objekt umzuwandeln.
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());   // damit das datetime gelesen werden kann

        // Verbindung zu RabbitMQ herstellen (siehe shared --> RabbitMQConfig)
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RabbitMQConfig.HOST);
        factory.setPort(RabbitMQConfig.PORT);

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // Exchange + Update-Queue + Binding deklarieren - gleiche Parameter wie beim Usage Service!
        channel.exchangeDeclare(RabbitMQConfig.EXCHANGE_NAME, "direct", true);
        channel.queueDeclare(RabbitMQConfig.QUEUE_UPDATE, true, false, false, null);
        channel.queueBind(RabbitMQConfig.QUEUE_UPDATE, RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_UPDATE);

        // Diese Methode wird fuer JEDE ankommende Update-Nachricht aufgerufen.
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
            UpdateMessage message = objectMapper.readValue(json, UpdateMessage.class);
            System.out.println("Received update for " + message.getBucketHour()
                    + " -> produced=" + message.getCommunityProduced()
                    + ", used=" + message.getCommunityUsed()
                    + ", grid=" + message.getGridUsed());
        };

        // Consumer starten. autoAck=true: eine Nachricht gilt sofort als erledigt.
        channel.basicConsume(RabbitMQConfig.QUEUE_UPDATE, true, deliverCallback, consumerTag -> { });

        System.out.println("Waiting for update messages...");
        // Programm am Leben halten, damit der Consumer weiter Nachrichten empfaengt (laeuft bis Ctrl+C).
        Thread.currentThread().join();
    }
}
