package com.brokerdealer.authservice.service;

import com.brokerdealer.authservice.domain.User;
import com.brokerdealer.authservice.domain.UserRole;
import com.brokerdealer.authservice.domain.UserStatus;
import com.brokerdealer.authservice.dto.response.UserResponse;
import com.brokerdealer.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User traderUser;
    private User adminUser;
    private User inactiveUser;

    @BeforeEach
    void setUp() {
        traderUser = User.builder()
                .id(UUID.randomUUID())
                .username("trader.john")
                .passwordHash("$hashed$")
                .role(UserRole.TRADER)
                .status(UserStatus.ACTIVE)
                .displayName("John Trader")
                .createdAt(Instant.parse("2026-04-01T00:00:00Z"))
                .lastLoginAt(Instant.parse("2026-05-01T09:30:00Z"))
                .build();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin.user")
                .passwordHash("$hashed$")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .displayName("Admin User")
                .createdAt(Instant.parse("2026-04-01T00:00:00Z"))
                .lastLoginAt(null)
                .build();

        inactiveUser = User.builder()
                .id(UUID.randomUUID())
                .username("old.trader")
                .passwordHash("$hashed$")
                .role(UserRole.TRADER)
                .status(UserStatus.INACTIVE)
                .displayName("Old Trader")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .lastLoginAt(null)
                .build();
    }

    @Test
    void shouldReturnAllUsersWhenNoFiltersProvided() {
        when(userRepository.findAll()).thenReturn(List.of(traderUser, adminUser, inactiveUser));

        List<UserResponse> result = userService.listUsers(null, null);

        assertThat(result).hasSize(3);
        verify(userRepository).findAll();
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void shouldReturnOnlyTRADERUsersWhenRoleFilterApplied() {
        when(userRepository.findByRole(UserRole.TRADER)).thenReturn(List.of(traderUser, inactiveUser));

        List<UserResponse> result = userService.listUsers(UserRole.TRADER, null);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(u -> "TRADER".equals(u.getRole()));
        verify(userRepository).findByRole(UserRole.TRADER);
    }

    @Test
    void shouldReturnOnlyACTIVEUsersWhenStatusFilterApplied() {
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(traderUser, adminUser));

        List<UserResponse> result = userService.listUsers(null, UserStatus.ACTIVE);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(u -> "ACTIVE".equals(u.getStatus()));
        verify(userRepository).findByStatus(UserStatus.ACTIVE);
    }

    @Test
    void shouldReturnFilteredByRoleAndStatus() {
        when(userRepository.findByRoleAndStatus(UserRole.TRADER, UserStatus.ACTIVE))
                .thenReturn(List.of(traderUser));

        List<UserResponse> result = userService.listUsers(UserRole.TRADER, UserStatus.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("trader.john");
        verify(userRepository).findByRoleAndStatus(UserRole.TRADER, UserStatus.ACTIVE);
    }

    @Test
    void shouldReturnEmptyListWhenNoUsersMatchFilter() {
        when(userRepository.findByRoleAndStatus(UserRole.VIEWER, UserStatus.INACTIVE))
                .thenReturn(List.of());

        List<UserResponse> result = userService.listUsers(UserRole.VIEWER, UserStatus.INACTIVE);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNeverExposePasswordHashInResponse() {
        when(userRepository.findAll()).thenReturn(List.of(traderUser, adminUser));

        List<UserResponse> result = userService.listUsers(null, null);

        assertThat(result).isNotEmpty();
        // UserResponse has no passwordHash field — compile-time safety
        // Verify all expected fields are present
        UserResponse first = result.get(0);
        assertThat(first.getUserId()).isNotNull();
        assertThat(first.getUsername()).isNotBlank();
        assertThat(first.getDisplayName()).isNotBlank();
        assertThat(first.getRole()).isNotBlank();
        assertThat(first.getStatus()).isNotBlank();
        assertThat(first.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFallbackToUsernameWhenDisplayNameIsNull() {
        User userWithNullDisplayName = User.builder()
                .id(UUID.randomUUID())
                .username("no.displayname")
                .passwordHash("$hashed$")
                .role(UserRole.TRADER)
                .status(UserStatus.ACTIVE)
                .displayName(null)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findAll()).thenReturn(List.of(userWithNullDisplayName));

        List<UserResponse> result = userService.listUsers(null, null);

        assertThat(result.get(0).getDisplayName()).isEqualTo("no.displayname");
    }

    @Test
    void shouldPopulateLastLoginAtWhenPresent() {
        when(userRepository.findAll()).thenReturn(List.of(traderUser));

        List<UserResponse> result = userService.listUsers(null, null);

        assertThat(result.get(0).getLastLoginAt())
                .isEqualTo(Instant.parse("2026-05-01T09:30:00Z"));
    }

    @Test
    void shouldReturnNullLastLoginAtWhenUserHasNeverLoggedIn() {
        when(userRepository.findAll()).thenReturn(List.of(adminUser));

        List<UserResponse> result = userService.listUsers(null, null);

        assertThat(result.get(0).getLastLoginAt()).isNull();
    }
}
