package in.ggv.campora.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.identity.domain.UserStatus;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.identity.service.OwnerAccounts;

/** On start-up, gives the accounts named in {@code ADMIN_EMAIL} their admin role. */
@Component
public class OwnerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);

    private final OwnerAccounts owners;
    private final UserRepository users;
    private final RideAdminService rides;

    public OwnerBootstrap(OwnerAccounts owners, UserRepository users, RideAdminService rides) {
        this.owners = owners;
        this.users = users;
        this.rides = rides;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (owners.emails().isEmpty()) {
            log.warn("ADMIN_EMAIL is not set: the admin panel has no owner.");
        }
        for (String email : owners.emails()) {
            users.findByEmail(email)
                    .filter(user -> owners.isOwner(user) && user.getStatus() == UserStatus.ACTIVE)
                    .filter(user -> !AdminGuard.isAdmin(user))
                    .ifPresent(user -> {
                        rides.releaseEverything(user);
                        owners.makeAdmin(user);
                        log.info("The ADMIN_EMAIL account now owns the admin panel.");
                    });
        }
    }
}
