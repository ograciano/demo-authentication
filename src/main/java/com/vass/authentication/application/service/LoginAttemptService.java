package com.vass.authentication.application.service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.vass.authentication.config.LoginAttemptProperties;
import com.vass.authentication.domain.exception.AccountLockedException;
import com.vass.authentication.domain.exception.RateLimitExceededException;

@Service
public class LoginAttemptService {

    private static final String LOCKED_MESSAGE = "Acceso temporalmente restringido";
    private static final String RATE_LIMIT_MESSAGE = "Demasiadas solicitudes, intente más tarde";

    private final LoginAttemptProperties properties;

    // Rate limiting
    private final ConcurrentHashMap<String, RateEntry> ipRateMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateEntry> emailRateMap = new ConcurrentHashMap<>();

    // Lockout por email
    private final ConcurrentHashMap<String, LockoutEntry> lockoutMap = new ConcurrentHashMap<>();

    public LoginAttemptService(LoginAttemptProperties properties) {
        this.properties = properties;
    }

    /**
     * Verifica rate limiting por IP y por email.
     * Lanza RateLimitExceededException si alguno supera el umbral configurado.
     */
    public void checkRateLimit(String clientIp, String email) {
        checkRateLimitForKey(clientIp, ipRateMap,
                properties.rateLimit().maxAttemptsPerIp(),
                properties.rateLimit().windowSeconds());
        checkRateLimitForKey(email.toLowerCase(), emailRateMap,
                properties.rateLimit().maxAttemptsPerEmail(),
                properties.rateLimit().windowSeconds());
    }

    private void checkRateLimitForKey(String key,
                                      ConcurrentHashMap<String, RateEntry> map,
                                      int maxAttempts,
                                      long windowSeconds) {
        Instant now = Instant.now();
        RateEntry entry = map.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plusSeconds(windowSeconds))) {
                return new RateEntry(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });
        if (entry.count().get() > maxAttempts) {
            throw new RateLimitExceededException(RATE_LIMIT_MESSAGE);
        }
    }

    /**
     * Verifica si el email tiene un bloqueo activo.
     * Lanza AccountLockedException si el bloqueo no ha expirado.
     */
    public void checkLockout(String email) {
        LockoutEntry entry = lockoutMap.get(email.toLowerCase());
        if (entry != null && Instant.now().isBefore(entry.lockedUntil())) {
            throw new AccountLockedException(LOCKED_MESSAGE);
        }
        if (entry != null) {
            // Bloqueo expirado: limpiar entrada
            lockoutMap.remove(email.toLowerCase(), entry);
        }
    }

    /**
     * Registra un intento fallido para el email.
     * Si los fallos acumulados alcanzan el umbral, activa el bloqueo temporal.
     */
    public void recordFailure(String email) {
        String key = email.toLowerCase();
        lockoutMap.compute(key, (k, existing) -> {
            int failures = (existing == null) ? 1 : existing.failureCount() + 1;
            Instant lockedUntil = (existing == null) ? Instant.EPOCH : existing.lockedUntil();

            if (failures >= properties.lockout().maxFailures()) {
                lockedUntil = Instant.now().plusSeconds(properties.lockout().durationSeconds());
            }
            return new LockoutEntry(failures, lockedUntil);
        });
    }

    /**
     * Reinicia los contadores de fallos y bloqueo para el email tras login exitoso.
     */
    public void resetFailures(String email) {
        lockoutMap.remove(email.toLowerCase());
    }

    /**
     * Limpia todos los contadores en memoria. Útil para tests de integración.
     */
    public void resetAll() {
        ipRateMap.clear();
        emailRateMap.clear();
        lockoutMap.clear();
    }

    // --- Tipos internos ---

    private record RateEntry(AtomicInteger count, Instant windowStart) {}

    private record LockoutEntry(int failureCount, Instant lockedUntil) {}
}
