package at.fhtw.disys.energycommunities.usage;

import at.fhtw.disys.energycommunities.shared.config.RabbitMQConfig;
import at.fhtw.disys.energycommunities.shared.dto.EnergyMessage;
import at.fhtw.disys.energycommunities.shared.dto.UpdateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.util.concurrent.CountDownLatch;

public class UsageServiceApp {

    public static void main(String[] args) throws Exception {
        System.out.println("Usage Service started.");

        // Verbindung zu RabbitMQ herstellen (siehe shared → RabbitMQConfig)
        ConnectionFactory factory = new ConnectionFactory(); //
        factory.setHost(RabbitMQConfig.HOST);
        factory.setPort(RabbitMQConfig.PORT);

        ObjectMapper objectMapper = new ObjectMapper(); //JACKSON für JSON<->JAVA Objekt wie Codeable
        objectMapper.registerModule(new JavaTimeModule()); // damit das datetime gelesen werden kann
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            //JDBC part, weil ich hier ja mit postgresql arbeite (könnte man ja austauschen)
        String dbUrl = "jdbc:postgresql://localhost:5432/energycommunities"; // Zugangsdaten siehe docker-compose

        // JDBC + RabbitMQ - try-with-resources: alle drei werden beim Verlassen des Blocks automatisch geschlossen
        try (java.sql.Connection db = DriverManager.getConnection(dbUrl, "disysuser", "disyspw");
             Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            System.out.println("Connected to database.");

            // "direct": Routing Key muss exakt zum Binding passen (kein Pattern wie bei "topic", kein Broadcast wie bei "fanout") —
            // reicht hier, weil es nur die zwei fixen Routing Keys "energy"/"update" gibt, je genau einer eigenen Queue zugeordnet.
            channel.exchangeDeclare(RabbitMQConfig.EXCHANGE_NAME, "direct", true);
            channel.queueDeclare(RabbitMQConfig.QUEUE_ENERGY, true, false, false, null);
            channel.queueBind(RabbitMQConfig.QUEUE_ENERGY, RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ENERGY);

            channel.queueDeclare(RabbitMQConfig.QUEUE_UPDATE, true, false, false, null);
            channel.queueBind(RabbitMQConfig.QUEUE_UPDATE, RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_UPDATE);

            // event-driven: dieser Callback wird nicht aktiv aufgerufen, sondern von RabbitMQ ausgelöst,
            // Lambda Objekt wird erstelt und speiche es in der Vaiable deliverCallback
            // sobald eine Nachricht in energy-queue ankommt (das "Event") — kein Polling, kein Warten im Code.
            // Body läuft hier noch NICHT — erst channel.basicConsume(...) weiter unten registriert dieses
            // Objekt beim RabbitMQ-Client als Handler; ausgeführt wird der Body dann später, auf einem
            // eigenen Thread des RabbitMQ-Clients, jedes Mal wenn tatsächlich eine Nachricht ankommt.
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
                EnergyMessage message = objectMapper.readValue(json, EnergyMessage.class);
                long deliveryTag = delivery.getEnvelope().getDeliveryTag();

                // auf volle Stunde abrunden
                LocalDateTime bucketHour = message.getDatetime().truncatedTo(ChronoUnit.HOURS);

                try {
                    // aktuellen Stand dieser Stunde lesen (oder 0, falls die Stunde noch nicht existiert)
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

                    UsageCalculator.Result updated = UsageCalculator.apply(produced, used, grid, message.getType(), message.getKwh());
                    produced = updated.produced();
                    used = updated.used();
                    grid = updated.grid();

                    // Stunde anlegen oder aktualisieren (mit nativem upsert)
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

                    UpdateMessage update = new UpdateMessage(bucketHour, produced, used, grid);
                    String updateJson = objectMapper.writeValueAsString(update);
                    channel.basicPublish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_UPDATE,
                            null, updateJson.getBytes(StandardCharsets.UTF_8));

                    channel.basicAck(deliveryTag, false);

                    System.out.println("Updated " + bucketHour + " -> produced=" + produced + ", used=" + used + ", grid=" + grid);
                } catch (SQLException e) {
                    System.out.println("DB-Fehler: " + e.getMessage());
                    // Nachricht zurueck in die Queue, statt sie bei einem DB-Fehler zu verlieren
                    channel.basicNack(deliveryTag, false, true);
                }
            };

            // Consumer starten. autoAck=false: Nachricht wird erst nach erfolgreichem DB-Write + Publish bestaetigt
            channel.basicConsume(RabbitMQConfig.QUEUE_ENERGY, false, deliverCallback, consumerTag -> {
            }); //consumerTAG->ID die diese consumer regist. identifiziert (interface brauchts aber wird nicht benutzt?)

            System.out.println("Waiting for messages...");
            // Service läuft einfach weiter, auch wenn keine Messages mehr in der Queue sind
            new CountDownLatch(1).await();
        }
    }
}
