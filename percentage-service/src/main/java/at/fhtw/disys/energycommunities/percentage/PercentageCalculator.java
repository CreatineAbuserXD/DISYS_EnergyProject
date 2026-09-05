package at.fhtw.disys.energycommunities.percentage;

/**
 * Reine Berechnung, kein I/O — dadurch isoliert testbar ohne RabbitMQ/DB.
 */
public final class PercentageCalculator {

    private PercentageCalculator() {
    }

    public record Result(double communityDepleted, double gridPortion) {
    }

    public static Result calculate(double produced, double used, double grid) {
        // die Guards verhindern Division durch 0
        double communityDepleted = 0.0;
        if (produced > 0) {
            communityDepleted = Math.min(100.0, used / produced * 100.0);
        }

        double total = used + grid;
        double gridPortion = 0.0;
        if (total > 0) {
            gridPortion = grid / total * 100.0;
        }

        return new Result(communityDepleted, gridPortion);
    }
}
