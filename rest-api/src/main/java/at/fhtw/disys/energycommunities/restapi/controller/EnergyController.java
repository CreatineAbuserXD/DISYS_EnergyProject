package at.fhtw.disys.energycommunities.restapi.controller;

import at.fhtw.disys.energycommunities.shared.model.PercentageRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

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
    public Map<String, Object> getHistorical(
            @RequestParam String start,
            @RequestParam String end) {

        return Collections.emptyMap();
    }
}
