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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class UsageServiceApp {

    public static void main(String[] args) throws Exception {
        System.out.println("Usage Service started.");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Verbindung zur Postgres-Datenbank (Zugangsdaten siehe docker-compose)
        String dbUrl = "jdbc:postgresql://localhost:5432/energycommunities";
        java.sql.Connection db = DriverManager.getConnection(dbUrl, "disysuser", "disyspw");
        System.out.println("Connected to database.");

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

            // auf die volle Stunde abrunden, z.B. 14:34 -> 14:00
            LocalDateTime bucketHour = message.getDatetime().truncatedTo(ChronoUnit.HOURS);

            try {
                // 1. aktuellen Stand dieser Stunde lesen (oder 0, falls die Stunde noch nicht existiert)
                double produced = 0, used = 0, grid = 0;
                PreparedStatement select = db.prepareStatement(
                        "SELECT community_produced, community_used, grid_used FROM usage_bucket WHERE bucket_hour = ?");
                select.setObject(1, bucketHour);
                ResultSet rs = select.executeQuery();
                if (rs.next()) {
                    produced = rs.getDouble("community_produced");
                    used = rs.getDouble("community_used");
                    grid = rs.getDouble("grid_used");
                }
                rs.close();
                select.close();

                // 2. Geschaeftslogik anwenden
                if (message.getType().equals("PRODUCER")) {
                    produced += message.getKwh();
                } else { // USER
                    double available = produced - used;                            // noch verfuegbare Gemeinschaftsenergie
                    double fromCommunity = Math.min(message.getKwh(), available);   // zuerst aus der Gemeinschaft
                    double fromGrid = message.getKwh() - fromCommunity;             // Rest aus dem Netz
                    used += fromCommunity;
                    grid += fromGrid;
                }

                // 3. zurueckschreiben: Stunde anlegen oder aktualisieren (Upsert)
                PreparedStatement upsert = db.prepareStatement(
                        "INSERT INTO usage_bucket (bucket_hour, community_produced, community_used, grid_used) " +
                        "VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT (bucket_hour) DO UPDATE SET " +
                        "community_produced = EXCLUDED.community_produced, " +
                        "community_used = EXCLUDED.community_used, " +
                        "grid_used = EXCLUDED.grid_used, " +
                        "updated_at = NOW()");
                upsert.setObject(1, bucketHour);
                upsert.setDouble(2, produced);
                upsert.setDouble(3, used);
                upsert.setDouble(4, grid);
                upsert.executeUpdate();
                upsert.close();

                System.out.println("Updated " + bucketHour + " -> produced=" + produced + ", used=" + used + ", grid=" + grid);
            } catch (SQLException e) {
                System.out.println("DB-Fehler: " + e.getMessage());
            }
        };

        // Consumer starten. autoAck=true: eine Nachricht gilt sofort als erledigt, sobald abgeholt
        channel.basicConsume(RabbitMQConfig.QUEUE_ENERGY, true, deliverCallback, consumerTag -> { });

        System.out.println("Waiting for messages...");
        // Programm läuft einfach weiter, auch wenn keine Messages mehr in der Queue sind
        Thread.currentThread().join();
    }
}
