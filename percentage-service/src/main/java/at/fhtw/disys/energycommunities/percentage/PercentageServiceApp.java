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
import java.util.concurrent.CountDownLatch;

public class PercentageServiceApp {

    public static void main(String[] args) throws Exception {
        System.out.println("Percentage Service started.");

        // Verbindung zu RabbitMQ herstellen (siehe shared --> RabbitMQConfig)
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RabbitMQConfig.HOST);
        factory.setPort(RabbitMQConfig.PORT);

        ObjectMapper objectMapper = new ObjectMapper();  //JSON<-->Object Sereialisierung/Deserial. (wie codeable)
        objectMapper.registerModule(new JavaTimeModule());   // damit das datetime gelesen werden kann

        String dbUrl = "jdbc:postgresql://localhost:5432/energycommunities"; // Zugangsdaten siehe docker-compose

        // JDBC + RabbitMQ - try-with-resources: alle drei werden beim Verlassen des Blocks automatisch geschlossen
        try (java.sql.Connection db = DriverManager.getConnection(dbUrl, "disysuser", "disyspw");
             Connection connection = factory.newConnection(); // öffnet die TCP-Con über RabbitMQ
             // ein Channel ist virtuell innerhalb der TCP Connection (mehrere Channels x Connection sind erlaubt)
             Channel channel = connection.createChannel()) {

            System.out.println("Connected to database.");

            channel.exchangeDeclare(RabbitMQConfig.EXCHANGE_NAME, "direct", true); //Routing an Q's (key:direct routing verhalten), true-> bleibt bestehen nach MQ-Restart
            channel.queueDeclare(RabbitMQConfig.QUEUE_UPDATE, true, false, false, null);
            channel.queueBind(RabbitMQConfig.QUEUE_UPDATE, RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_UPDATE);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
                UpdateMessage message = objectMapper.readValue(json, UpdateMessage.class);
                long deliveryTag = delivery.getEnvelope().getDeliveryTag();

                double produced = message.getCommunityProduced();
                double used = message.getCommunityUsed();
                double grid = message.getGridUsed();

                PercentageCalculator.Result percentages = PercentageCalculator.calculate(produced, used, grid);
                double communityDepleted = percentages.communityDepleted();
                double gridPortion = percentages.gridPortion();

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

                    channel.basicAck(deliveryTag, false);

                    System.out.println("Percentages for " + message.getBucketHour()
                            + " -> community_depleted=" + communityDepleted + "%, grid_portion=" + gridPortion + "%");
                } catch (SQLException e) {
                    System.out.println("DB-Fehler: " + e.getMessage());
                    // Nachricht zurueck in die Queue, statt sie bei einem DB-Fehler zu verlieren
                    channel.basicNack(deliveryTag, false, true);
                }
            };

            // Consumer starten. autoAck=false: Nachricht wird erst nach erfolgreichem DB-Write bestaetigt
            channel.basicConsume(RabbitMQConfig.QUEUE_UPDATE, false, deliverCallback, ct -> { });

            System.out.println("Waiting for update messages...");
            // Service läuft einfach weiter, auch wenn keine Messages mehr in der Queue sind
            new CountDownLatch(1).await();
        }
    }
}
