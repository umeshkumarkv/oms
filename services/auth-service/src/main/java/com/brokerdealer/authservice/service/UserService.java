package com.brokerdealer.authservice.service;

import com.brokerdealer.authservice.domain.User;
import com.brokerdealer.authservice.domain.UserRole;
import com.brokerdealer.authservice.domain.UserStatus;
import com.brokerdealer.authservice.dto.response.UserResponse;
import com.brokerdealer.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

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
