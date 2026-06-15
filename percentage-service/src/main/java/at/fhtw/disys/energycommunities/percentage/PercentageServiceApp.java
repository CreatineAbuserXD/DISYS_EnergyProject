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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PercentageServiceApp {

    public static void main(String[] args) throws Exception {
        System.out.println("Percentage Service started.");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());   // damit das datetime gelesen werden kann

        String dbUrl = "jdbc:postgresql://localhost:5432/energycommunities"; // Zugangsdaten siehe docker-compose
        java.sql.Connection db = DriverManager.getConnection(dbUrl, "disysuser", "disyspw");
        System.out.println("Connected to database.");

        // Verbindung zu RabbitMQ herstellen (siehe shared --> RabbitMQConfig)
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RabbitMQConfig.HOST);
        factory.setPort(RabbitMQConfig.PORT);

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(RabbitMQConfig.EXCHANGE_NAME, "direct", true);
        channel.queueDeclare(RabbitMQConfig.QUEUE_UPDATE, true, false, false, null);
        channel.queueBind(RabbitMQConfig.QUEUE_UPDATE, RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_UPDATE);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
            UpdateMessage message = objectMapper.readValue(json, UpdateMessage.class);

            double produced = message.getCommunityProduced();
            double used = message.getCommunityUsed();
            double grid = message.getGridUsed();

            // die zwei Prozentwerte berechnen (die Guards verhindern Division durch 0)
            double communityDepleted = 0.0;
            if (produced > 0) {
                communityDepleted = Math.min(100.0, used / produced * 100.0);
            }

            double total = used + grid;
            double gridPortion = 0.0;
            if (total > 0) {
                gridPortion = grid / total * 100.0;
            }

            try {
                // eine Zeile pro Stunde: anlegen oder bei jeder Update-Nachricht ueberschreiben (Upsert)
                PreparedStatement upsert = db.prepareStatement(
                        "INSERT INTO percentage_record (bucket_hour, community_depleted, grid_portion) " +
                        "VALUES (?, ?, ?) " +
                        "ON CONFLICT (bucket_hour) DO UPDATE SET " +
                        "community_depleted = EXCLUDED.community_depleted, " +
                        "grid_portion = EXCLUDED.grid_portion, " +
                        "updated_at = NOW()");
                upsert.setObject(1, message.getBucketHour());
                upsert.setDouble(2, communityDepleted);
                upsert.setDouble(3, gridPortion);
                upsert.executeUpdate();
                upsert.close();

                System.out.println("Percentages for " + message.getBucketHour()
                        + " -> community_depleted=" + communityDepleted + "%, grid_portion=" + gridPortion + "%");
            } catch (SQLException e) {
                System.out.println("DB-Fehler: " + e.getMessage());
            }
        };

        // Consumer starten. autoAck=true: eine Nachricht gilt sofort als erledigt, sobald abgeholt
        channel.basicConsume(RabbitMQConfig.QUEUE_UPDATE, true, deliverCallback, consumerTag -> { });

        System.out.println("Waiting for update messages...");
        // Service läuft einfach weiter, auch wenn keine Messages mehr in der Queue sind
        Thread.currentThread().join();
    }
}
