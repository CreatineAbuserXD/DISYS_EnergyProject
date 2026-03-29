package at.fhtw.disys.energycommunities.restapi.controller;

import at.fhtw.disys.energycommunities.shared.model.PercentageRecord;
import at.fhtw.disys.energycommunities.shared.model.UsageBucket;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/energy")
public class EnergyController {

    @GetMapping("/current")
    public PercentageRecord getCurrent() {
        PercentageRecord record = new PercentageRecord();
        record.setBucketHour(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS));
        record.setCommunityDepleted(15.6);
        record.setGridPortion(4.7);
        return record;
    }

    @GetMapping("/historical")
    public List<UsageBucket> getHistorical(
            @RequestParam String start,
            @RequestParam String end) {

        List<UsageBucket> historical = new ArrayList<>();

        UsageBucket b1 = new UsageBucket();
        b1.setBucketHour(LocalDateTime.of(2026, 3, 29, 9, 0));
        b1.setCommunityProduced(18.05);
        b1.setCommunityUsed(18.05);
        b1.setGridUsed(1.07);
        historical.add(b1);

        UsageBucket b2 = new UsageBucket();
        b2.setBucketHour(LocalDateTime.of(2026, 3, 29, 10, 0));
        b2.setCommunityProduced(22.10);
        b2.setCommunityUsed(19.50);
        b2.setGridUsed(3.20);
        historical.add(b2);

        UsageBucket b3 = new UsageBucket();
        b3.setBucketHour(LocalDateTime.of(2026, 3, 29, 11, 0));
        b3.setCommunityProduced(15.80);
        b3.setCommunityUsed(15.80);
        b3.setGridUsed(0.50);
        historical.add(b3);

        return historical;
    }
}
