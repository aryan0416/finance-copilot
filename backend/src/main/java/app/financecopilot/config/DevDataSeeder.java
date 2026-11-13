package app.financecopilot.config;

import app.financecopilot.domain.User;
import app.financecopilot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds the demo account on the dev profile so reviewers can log in without registering. */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.demo-user", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@financecopilot.app";
    private static final String DEMO_PASSWORD = "Demo@123";
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            return;
        }
        users.save(new User(DEMO_EMAIL, passwordEncoder.encode(DEMO_PASSWORD), "Demo User"));
        log.info("Seeded demo user {}", DEMO_EMAIL);
    }
}
