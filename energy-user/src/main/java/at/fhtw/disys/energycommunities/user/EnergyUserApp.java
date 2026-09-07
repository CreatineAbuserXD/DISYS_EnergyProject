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

        // genaue connection-details für message broker: RabbitMQConfig
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RabbitMQConfig.HOST);
        factory.setPort(RabbitMQConfig.PORT);

        try (Connection connection = factory.newConnection();
            Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(RabbitMQConfig.EXCHANGE_NAME, "direct", true);
            // queueDeclare(name, durable, exclusive, autoDelete, arguments):
            // durable=true    -> übersteht RabbitMQ-Neustart (nicht: Java-Prozess-Shutdown)
            // exclusive=false -> mehrere Connections/Services dürfen diese Queue nutzen
            // autoDelete=false -> Queue bleibt bestehen, auch ohne aktiven Consumer
            // arguments=null  -> keine Extra-Optionen (z.B. TTL, Dead-Letter-Exchange) nötig
            channel.queueDeclare(RabbitMQConfig.QUEUE_ENERGY, true, false, false, null);
            channel.queueBind(RabbitMQConfig.QUEUE_ENERGY, RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ENERGY);

            while (true) {

                // die folgenden Werte sind reine Annahmen (grobe kWh/min-Werte wurden gesucht, keine genau auf Wien bezogenen)
                int hour = LocalDateTime.now().getHour();
                double base;

                if (6 <= hour && hour <= 9) {  // 6 - 9 Uhr morgens PEAK
                    base = 0.010;
                } else if (17 <= hour && hour <= 21) {  // 17-21 Uhr PEAK
                    base = 0.0125;
                } else if (22 <= hour|| hour <= 5) { // Nacht -> am wenigsten
                    base = 0.002;
                } else {
                    base = 0.0055;   // sonst einfach stani Wert
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
}
