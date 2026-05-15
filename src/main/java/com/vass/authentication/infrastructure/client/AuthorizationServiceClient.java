package com.vass.authentication.infrastructure.client;

import com.vass.authentication.infrastructure.client.dto.AssignPermissionRequest;
import com.vass.authentication.infrastructure.client.dto.AssignPermissionResponse;
import com.vass.authentication.infrastructure.client.dto.UserPermissionsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "authorization-service", url = "${authorization-service.url}", primary = false)
public interface AuthorizationServiceClient {

    @GetMapping("/api/permissions/users/{userId}")
    UserPermissionsResponse getUserPermissions(@PathVariable("userId") Long userId);

    @PostMapping("/api/permissions/users/{userId}")
    AssignPermissionResponse assignPermission(
            @PathVariable("userId") Long userId,
            @RequestBody AssignPermissionRequest request
    );
}
