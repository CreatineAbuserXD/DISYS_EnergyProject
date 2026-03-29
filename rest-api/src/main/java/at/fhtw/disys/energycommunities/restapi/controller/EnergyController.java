package at.fhtw.disys.energycommunities.restapi.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/energy")
public class EnergyController {

    @GetMapping("/current")
    public Map<String, Object> getCurrent() {
        // TODO: query percentage_record table for current hour
        return Collections.emptyMap();
    }

    @GetMapping("/historical")
    public Map<String, Object> getHistorical(
            @RequestParam String start,
            @RequestParam String end) {
        // TODO: query usage_bucket table for date range
        return Collections.emptyMap();
    }
}
