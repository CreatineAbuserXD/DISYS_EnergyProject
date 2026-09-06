package at.fhtw.disys.energycommunities.restapi.controller;

import at.fhtw.disys.energycommunities.shared.model.PercentageRecord;
import at.fhtw.disys.energycommunities.shared.model.UsageBucket;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController //"Diese Klasse bietet HTTP Endpoints an. (eh kloa)
@RequestMapping("/energy") //"Alle APIs begennen mit /energy .
public class EnergyController {

    // Spring stellt das JdbcTemplate automatisch bereit (Verbindung aus den application.properties).
    private final JdbcTemplate jdbcTemplate;

    public EnergyController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    } //Dependency Injection von Spring

    @GetMapping("/current")    //--> /energy/current
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

        if (rows.isEmpty()) { // für diese Stunde gibt es noch keine Daten -> Nullen zurückgeben
            PercentageRecord empty = new PercentageRecord();
            empty.setBucketHour(currentHour);
            empty.setCommunityDepleted(0);
            empty.setGridPortion(0);
            return empty;
        }
        return rows.get(0);
    }

    @GetMapping("/historical")
    public ResponseEntity<?> getHistorical(
            @RequestParam String start,
            @RequestParam String end) {

        LocalDateTime startTime;
        LocalDateTime endTime;
        try {
            startTime = LocalDateTime.parse(start);
            endTime = LocalDateTime.parse(end);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("Invalid date format, expected e.g. 2024-01-01T14:00:00");
        }

        List<UsageBucket> buckets = jdbcTemplate.query(
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

        return ResponseEntity.ok(buckets);
    }
}
