package com.brokerdealer.authservice.service;

import com.brokerdealer.authservice.domain.User;
import com.brokerdealer.authservice.domain.UserRole;
import com.brokerdealer.authservice.domain.UserStatus;
import com.brokerdealer.authservice.dto.request.CreateUserRequest;
import com.brokerdealer.authservice.dto.request.UpdateUserStatusRequest;
import com.brokerdealer.authservice.dto.response.UserResponse;
import com.brokerdealer.authservice.dto.response.UserStatusResponse;
import com.brokerdealer.authservice.exception.CannotDeactivateSelfException;
import com.brokerdealer.authservice.exception.UserNotFoundException;
import com.brokerdealer.authservice.exception.UsernameTakenException;
import com.brokerdealer.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponse> listUsers(UserRole role, UserStatus status) {
        log.debug("Listing users: role={} status={}", role, status);

        List<User> users;
        if (role != null && status != null) {
            users = userRepository.findByRoleAndStatus(role, status);
        } else if (role != null) {
            users = userRepository.findByRole(role);
        } else if (status != null) {
            users = userRepository.findByStatus(status);
        } else {
            users = userRepository.findAll();
        }

        return users.stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse createUser(CreateUserRequest request) {
        log.debug("Creating user: username={} role={}", request.getUsername(), request.getRole());

        if (userRepository.findByUsernameIgnoreCase(request.getUsername()).isPresent()) {
            throw new UsernameTakenException("USERNAME_TAKEN", "Username already taken: " + request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .displayName(request.getDisplayName())
                .role(request.getRole())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .build();

        User saved = userRepository.save(user);
        log.info("User created: userId={} role={}", saved.getId(), saved.getRole());
        return toResponse(saved);
    }

    public UserStatusResponse updateUserStatus(UUID targetId, UUID callerAdminId, UpdateUserStatusRequest request) {
        log.debug("Updating user status: targetId={} status={}", targetId, request.getStatus());

        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException("USER_NOT_FOUND", "User not found: " + targetId));

        if (callerAdminId != null && callerAdminId.equals(targetId) && request.getStatus() == UserStatus.INACTIVE) {
            throw new CannotDeactivateSelfException("CANNOT_DEACTIVATE_SELF",
                    "Admin cannot deactivate their own account");
        }

        user.setStatus(request.getStatus());
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);

        log.info("User status updated: userId={} status={}", saved.getId(), saved.getStatus());
        return UserStatusResponse.builder()
                .userId(saved.getId())
                .status(saved.getStatus().name())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    private UserResponse toResponse(User user) {
        String displayName = (user.getDisplayName() != null && !user.getDisplayName().isBlank())
                ? user.getDisplayName()
                : user.getUsername();
        return UserResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(displayName)
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
