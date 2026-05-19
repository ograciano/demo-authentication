package com.vass.authentication.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private String role;

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public String getRole() {
        return role;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final UserEntity target = new UserEntity();

        public Builder id(Long id) {
            target.id = id;
            return this;
        }

        public Builder email(String email) {
            target.email = email;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            target.passwordHash = passwordHash;
            return this;
        }

        public Builder name(String name) {
            target.name = name;
            return this;
        }

        public Builder active(boolean active) {
            target.active = active;
            return this;
        }

        public Builder role(String role) {
            target.role = role;
            return this;
        }

        public UserEntity build() {
            return target;
        }
    }
}
