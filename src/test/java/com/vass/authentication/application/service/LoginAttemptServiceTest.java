package com.vass.authentication.application.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vass.authentication.config.LoginAttemptProperties;
import com.vass.authentication.domain.exception.AccountLockedException;
import com.vass.authentication.domain.exception.RateLimitExceededException;

class LoginAttemptServiceTest {

    private static final String CLIENT_IP = "192.168.1.1";
    private static final String EMAIL = "test@email.com";

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        LoginAttemptProperties properties = new LoginAttemptProperties(
                new LoginAttemptProperties.RateLimit(3, 3, 60),
                new LoginAttemptProperties.Lockout(3, 300)
        );
        loginAttemptService = new LoginAttemptService(properties);
    }

    // --- Rate limiting por IP ---

    @Test
    void testCheckRateLimit_IpUnderLimit_NoException() {
        loginAttemptService.checkRateLimit(CLIENT_IP, EMAIL);
        loginAttemptService.checkRateLimit(CLIENT_IP, EMAIL);

        assertThatCode(() -> loginAttemptService.checkRateLimit(CLIENT_IP, EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    void testCheckRateLimit_IpOverLimit_ThrowsRateLimitExceeded() {
        loginAttemptService.checkRateLimit(CLIENT_IP, "a@email.com");
        loginAttemptService.checkRateLimit(CLIENT_IP, "b@email.com");
        loginAttemptService.checkRateLimit(CLIENT_IP, "c@email.com");

        assertThatThrownBy(() -> loginAttemptService.checkRateLimit(CLIENT_IP, "d@email.com"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void testCheckRateLimit_EmailOverLimit_ThrowsRateLimitExceeded() {
        loginAttemptService.checkRateLimit("1.1.1.1", EMAIL);
        loginAttemptService.checkRateLimit("2.2.2.2", EMAIL);
        loginAttemptService.checkRateLimit("3.3.3.3", EMAIL);

        assertThatThrownBy(() -> loginAttemptService.checkRateLimit("4.4.4.4", EMAIL))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void testCheckRateLimit_DifferentIpsAndEmails_NoException() {
        assertThatCode(() -> {
            loginAttemptService.checkRateLimit("10.0.0.1", "user1@email.com");
            loginAttemptService.checkRateLimit("10.0.0.2", "user2@email.com");
            loginAttemptService.checkRateLimit("10.0.0.3", "user3@email.com");
        }).doesNotThrowAnyException();
    }

    // --- Lockout ---

    @Test
    void testCheckLockout_NotLocked_NoException() {
        assertThatCode(() -> loginAttemptService.checkLockout(EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    void testCheckLockout_ActiveLock_ThrowsAccountLocked() {
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);

        assertThatThrownBy(() -> loginAttemptService.checkLockout(EMAIL))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void testRecordFailure_BelowThreshold_NoLock() {
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);

        assertThatCode(() -> loginAttemptService.checkLockout(EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    void testRecordFailure_AtThreshold_ActivatesLock() {
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);

        assertThatThrownBy(() -> loginAttemptService.checkLockout(EMAIL))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void testResetFailures_ClearsCounterAndLock() {
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);

        loginAttemptService.resetFailures(EMAIL);

        assertThatCode(() -> loginAttemptService.checkLockout(EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    void testResetFailures_AfterReset_CanAccumulateFailuresAgain() {
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.resetFailures(EMAIL);

        loginAttemptService.recordFailure(EMAIL);
        loginAttemptService.recordFailure(EMAIL);

        assertThatCode(() -> loginAttemptService.checkLockout(EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    void testCheckLockout_EmailCaseInsensitive_SameLock() {
        loginAttemptService.recordFailure("User@Email.COM");
        loginAttemptService.recordFailure("user@email.com");
        loginAttemptService.recordFailure("USER@EMAIL.COM");

        assertThatThrownBy(() -> loginAttemptService.checkLockout("user@email.com"))
                .isInstanceOf(AccountLockedException.class);
    }
}
