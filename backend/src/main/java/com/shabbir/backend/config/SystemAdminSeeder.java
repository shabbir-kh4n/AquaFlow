package com.shabbir.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.shabbir.backend.entity.SystemAdmin;
import com.shabbir.backend.repository.SystemAdminRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SystemAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminSeeder.class);

    private final SystemAdminRepository systemAdminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.system-admin.email}")
    private String seedEmail;

    @Value("${app.system-admin.password}")
    private String seedPassword;

    @Value("${app.system-admin.full-name}")
    private String seedFullName;

    @Override
    public void run(String... args) {
        if (systemAdminRepository.count() > 0) {
            return;
        }

        SystemAdmin admin = SystemAdmin.builder()
                .fullName(seedFullName)
                .email(seedEmail)
                .password(passwordEncoder.encode(seedPassword))
                .build();

        systemAdminRepository.save(admin);
        log.info("Seeded initial system admin account: {}", seedEmail);
    }
}
