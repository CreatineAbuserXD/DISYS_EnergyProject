package at.fhtw.disys.energycommunities.restapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RestApiApplication { //verlgichen mit Nextjs wie nextDev/nextSart (nich ganz aber ja)

    public static void main(String[] args) {
        // Baut den Spring Context auf, scannt Beans (z.B. EnergyController, JdbcTemplate) und startet den eingebetteten Tomcat.
        SpringApplication.run(RestApiApplication.class, args);
    }
}