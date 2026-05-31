package at.fhtw.disys.energycommunities.usage;

import at.fhtw.disys.energycommunities.shared.config.RabbitMQConfig;
import at.fhtw.disys.energycommunities.shared.dto.EnergyMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;

public class UsageServiceApp {

    public static void main(String[] args) throws Exception {
        System.out.println("Usage Service started.");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Verbindung zu RabbitMQ herstellen (siehe shared --> RabbitMQConfig)
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RabbitMQConfig.HOST);
        factory.setPort(RabbitMQConfig.PORT);

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(RabbitMQConfig.EXCHANGE_NAME, "direct", true);
        channel.queueDeclare(RabbitMQConfig.QUEUE_ENERGY, true, false, false, null);
        channel.queueBind(RabbitMQConfig.QUEUE_ENERGY, RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ENERGY);

        // Diese Methode wird fuer jede ankommende Nachricht aufgerufen
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
            EnergyMessage message = objectMapper.readValue(json, EnergyMessage.class);
            System.out.println("Received: " + message.getType() + " " + message.getKwh() + " kWh @ " + message.getDatetime());
        };

        // Consumer starten. autoAck=true: eine Nachricht gilt sofort als erledigt, sobald abgeholt
        channel.basicConsume(RabbitMQConfig.QUEUE_ENERGY, true, deliverCallback, consumerTag -> { });

        System.out.println("Waiting for messages...");
        // Programm läuft einfach weiter, auch wenn keine Messages mehr in der Queue sind
        Thread.currentThread().join();
    }
}
