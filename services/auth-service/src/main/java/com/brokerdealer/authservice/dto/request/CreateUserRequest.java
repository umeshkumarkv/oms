package com.brokerdealer.authservice.dto.request;

import com.brokerdealer.authservice.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "Username may only contain letters, numbers, underscores, and dots")
    private String username;

    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 100, message = "Display name must be 2–100 characters")
    private String displayName;

    @NotNull(message = "Role is required")
    private UserRole role;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
