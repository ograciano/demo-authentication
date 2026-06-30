package com.vass.authentication.application.port.in;

import com.vass.authentication.api.dto.MeResponse;

public interface GetCurrentUserUseCase {

    MeResponse getCurrentUser(String username);
}
