package app.financecopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Authentication is JWT-only, so the default in-memory user (and its logged password) is disabled.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class FinanceCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceCopilotApplication.class, args);
    }
}
