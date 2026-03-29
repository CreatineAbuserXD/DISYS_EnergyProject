package at.fhtw.disys.energycommunities.shared.config;

public final class RabbitMQConfig {

    public static final String HOST = "localhost";
    public static final int PORT = 5672;

    public static final String EXCHANGE_NAME = "energy-exchange";

    public static final String QUEUE_ENERGY = "energy-queue";
    public static final String QUEUE_UPDATE = "update-queue";

    public static final String ROUTING_KEY_ENERGY = "energy";
    public static final String ROUTING_KEY_UPDATE = "update";

    private RabbitMQConfig() {
    }
}
