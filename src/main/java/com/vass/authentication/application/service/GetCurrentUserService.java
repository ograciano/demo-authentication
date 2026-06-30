package com.vass.authentication.application.service;

import com.vass.authentication.api.dto.MeResponse;
import com.vass.authentication.application.port.in.GetCurrentUserUseCase;
import com.vass.authentication.domain.exception.InvalidCredentialsException;
import com.vass.authentication.infrastructure.integration.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.persistence.entity.UserEntity;
import com.vass.authentication.infrastructure.persistence.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetCurrentUserService implements GetCurrentUserUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCurrentUserService.class);

    private final UserRepository userRepository;
    private final AuthorizationServiceClient authorizationServiceClient;

    public GetCurrentUserService(UserRepository userRepository,
                                 AuthorizationServiceClient authorizationServiceClient) {
        this.userRepository = userRepository;
        this.authorizationServiceClient = authorizationServiceClient;
    }

    @Override
    public MeResponse getCurrentUser(String username) {
        UserEntity user = userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> {
                    LOGGER.warn("event=ME_INTROSPECTION result=USER_NOT_FOUND username={}", username);
                    return new InvalidCredentialsException("Token inválido o expirado");
                });

        List<String> permissions = authorizationServiceClient.getPermissionsForUser(user.getId());
        return new MeResponse(username, permissions);
    }
}
