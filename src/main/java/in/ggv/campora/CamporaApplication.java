package in.ggv.campora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Campora — a demand-oriented campus mobility platform.
 *
 * <p>The application is a modular monolith: each top-level package
 * ({@code identity}, {@code campus}, {@code profile}, {@code mobility},
 * {@code trip}, {@code companion}, {@code admin}) owns its entities,
 * repositories and services, so any module can later be extracted into
 * its own service without redesigning the database.</p>
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class CamporaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CamporaApplication.class, args);
    }
}
