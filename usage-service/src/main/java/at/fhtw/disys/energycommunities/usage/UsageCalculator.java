package at.fhtw.disys.energycommunities.usage;

/**
 * Reine Berechnung, kein I/O — dadurch isoliert testbar ohne RabbitMQ/DB.
 */
public final class UsageCalculator {

    private UsageCalculator() {
    }

    public record Result(double produced, double used, double grid) {
    }

    public static Result apply(double produced, double used, double grid, String type, double kwh) {
        if (type.equals("PRODUCER")) {
            produced += kwh;
        } else { // USER
            double available = produced - used;         // noch verfügbare Gemeinschaftsenergie
            double fromCommunity = Math.min(kwh, available); // zuerst aus der Gemeinschaft
            double fromGrid = kwh - fromCommunity;       // Rest aus dem Netz
            used += fromCommunity;
            grid += fromGrid;
        }
        return new Result(produced, used, grid);
    }
}
