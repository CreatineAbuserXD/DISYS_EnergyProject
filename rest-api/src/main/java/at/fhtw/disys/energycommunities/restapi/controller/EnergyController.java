package at.fhtw.disys.energycommunities.restapi.controller;

import at.fhtw.disys.energycommunities.shared.model.PercentageRecord;
import at.fhtw.disys.energycommunities.shared.model.UsageBucket;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/energy")
public class EnergyController {

    // Spring stellt das JdbcTemplate automatisch bereit (Verbindung aus den application.properties).
    private final JdbcTemplate jdbcTemplate;

    public EnergyController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Liefert die Prozentwerte der aktuellen Stunde aus percentage_record.
    @GetMapping("/current")
    public PercentageRecord getCurrent() {
        LocalDateTime currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);

        List<PercentageRecord> rows = jdbcTemplate.query(
                "SELECT bucket_hour, community_depleted, grid_portion FROM percentage_record WHERE bucket_hour = ?",
                (rs, rowNum) -> {
                    PercentageRecord record = new PercentageRecord();
                    record.setBucketHour(rs.getTimestamp("bucket_hour").toLocalDateTime());
                    record.setCommunityDepleted(rs.getDouble("community_depleted"));
                    record.setGridPortion(rs.getDouble("grid_portion"));
                    return record;
                },
                currentHour);

        if (rows.isEmpty()) {
            // Fuer diese Stunde gibt es noch keine Daten -> Nullen zurückgeben
            PercentageRecord empty = new PercentageRecord();
            empty.setBucketHour(currentHour);
            empty.setCommunityDepleted(0);
            empty.setGridPortion(0);
            return empty;
        }
        return rows.get(0);
    }

    // Liefert die Stundenwerte aus usage_bucket fuer den gewaehlten Zeitraum.
    @GetMapping("/historical")
    public List<UsageBucket> getHistorical(
            @RequestParam String start,
            @RequestParam String end) {

        LocalDateTime startTime = LocalDateTime.parse(start);
        LocalDateTime endTime = LocalDateTime.parse(end);

        return jdbcTemplate.query(
                "SELECT bucket_hour, community_produced, community_used, grid_used FROM usage_bucket "
                        + "WHERE bucket_hour BETWEEN ? AND ? ORDER BY bucket_hour",
                (rs, rowNum) -> {
                    UsageBucket bucket = new UsageBucket();
                    bucket.setBucketHour(rs.getTimestamp("bucket_hour").toLocalDateTime());
                    bucket.setCommunityProduced(rs.getDouble("community_produced"));
                    bucket.setCommunityUsed(rs.getDouble("community_used"));
                    bucket.setGridUsed(rs.getDouble("grid_used"));
                    return bucket;
                },
                startTime, endTime);
    }
}
