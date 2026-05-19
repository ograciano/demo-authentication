package com.vass.authentication.config;

import com.vass.authentication.infrastructure.persistence.entity.UserEntity;
import com.vass.authentication.infrastructure.persistence.repository.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    ApplicationRunner seedUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }
            userRepository.save(UserEntity.builder()
                    .email("oscar.demo@email.com")
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .name("Oscar Demo")
                    .active(true)
                    .role("VIEWER")
                    .build());
            userRepository.save(UserEntity.builder()
                    .email("inactive.demo@email.com")
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .name("Inactive Demo")
                    .active(false)
                    .role("VIEWER")
                    .build());
        };
    }
}
