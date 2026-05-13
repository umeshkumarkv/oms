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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

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

    // ─── listUsers ───────────────────────────────────────────────────────────

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

    // ─── createUser ──────────────────────────────────────────────────────────

    @Test
    void shouldCreateUserSuccessfully() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("new.trader");
        request.setDisplayName("New Trader");
        request.setRole(UserRole.TRADER);
        request.setPassword("SecurePass1");

        when(userRepository.findByUsernameIgnoreCase("new.trader")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecurePass1")).thenReturn("$bcrypt$hashed");

        UUID savedId = UUID.randomUUID();
        User savedUser = User.builder()
                .id(savedId)
                .username("new.trader")
                .displayName("New Trader")
                .role(UserRole.TRADER)
                .passwordHash("$bcrypt$hashed")
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse result = userService.createUser(request);

        assertThat(result.getUserId()).isEqualTo(savedId);
        assertThat(result.getUsername()).isEqualTo("new.trader");
        assertThat(result.getRole()).isEqualTo("TRADER");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldHashPasswordBeforeSave() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("secure.user");
        request.setDisplayName("Secure User");
        request.setRole(UserRole.TRADER);
        request.setPassword("PlainText1");

        when(userRepository.findByUsernameIgnoreCase("secure.user")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("PlainText1")).thenReturn("$bcrypt$encoded");

        User savedUser = User.builder()
                .id(UUID.randomUUID()).username("secure.user").displayName("Secure User")
                .role(UserRole.TRADER).passwordHash("$bcrypt$encoded")
                .status(UserStatus.ACTIVE).createdAt(Instant.now()).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        userService.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$bcrypt$encoded");
        assertThat(captor.getValue().getPasswordHash()).doesNotContain("PlainText1");
    }

    @Test
    void shouldThrowUsernameTakenWhenDuplicateUsernameIgnoreCase() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("Trader.John");
        request.setDisplayName("Dup");
        request.setRole(UserRole.TRADER);
        request.setPassword("Password1");

        when(userRepository.findByUsernameIgnoreCase("Trader.John")).thenReturn(Optional.of(traderUser));

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(UsernameTakenException.class)
                .hasMessageContaining("Trader.John");

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldSetStatusActiveOnCreate() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("brand.new");
        request.setDisplayName("Brand New");
        request.setRole(UserRole.OPERATIONS);
        request.setPassword("Password1");

        when(userRepository.findByUsernameIgnoreCase("brand.new")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("$hash");

        User savedUser = User.builder()
                .id(UUID.randomUUID()).username("brand.new").displayName("Brand New")
                .role(UserRole.OPERATIONS).passwordHash("$hash")
                .status(UserStatus.ACTIVE).createdAt(Instant.now()).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse result = userService.createUser(request);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    // ─── updateUserStatus ────────────────────────────────────────────────────

    @Test
    void shouldDeactivateUserSuccessfully() {
        UUID targetId = traderUser.getId();
        UUID adminId = adminUser.getId();
        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setStatus(UserStatus.INACTIVE);

        when(userRepository.findById(targetId)).thenReturn(Optional.of(traderUser));
        when(userRepository.save(any(User.class))).thenReturn(traderUser);

        UserStatusResponse result = userService.updateUserStatus(targetId, adminId, request);

        assertThat(result.getUserId()).isEqualTo(targetId);
        assertThat(result.getStatus()).isEqualTo("INACTIVE");
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldActivateUserSuccessfully() {
        UUID targetId = inactiveUser.getId();
        UUID adminId = adminUser.getId();
        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(targetId)).thenReturn(Optional.of(inactiveUser));
        when(userRepository.save(any(User.class))).thenReturn(inactiveUser);

        UserStatusResponse result = userService.updateUserStatus(targetId, adminId, request);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldThrowUserNotFoundWhenTargetIdUnknown() {
        UUID unknownId = UUID.randomUUID();
        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setStatus(UserStatus.INACTIVE);

        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserStatus(unknownId, adminUser.getId(), request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void shouldThrowCannotDeactivateSelfWhenAdminSetsOwnStatusInactive() {
        UUID adminId = adminUser.getId();
        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setStatus(UserStatus.INACTIVE);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> userService.updateUserStatus(adminId, adminId, request))
                .isInstanceOf(CannotDeactivateSelfException.class)
                .hasMessageContaining("cannot deactivate");

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldAllowAdminToActivateOwnAccount() {
        UUID adminId = adminUser.getId();
        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setStatus(UserStatus.ACTIVE);  // ACTIVE — self-block only applies to INACTIVE

        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.save(any(User.class))).thenReturn(adminUser);

        UserStatusResponse result = userService.updateUserStatus(adminId, adminId, request);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(userRepository).save(any());
    }

    @Test
    void shouldAllowDeactivationWhenCallerIdIsNull() {
        UUID targetId = traderUser.getId();
        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setStatus(UserStatus.INACTIVE);

        when(userRepository.findById(targetId)).thenReturn(Optional.of(traderUser));
        when(userRepository.save(any(User.class))).thenReturn(traderUser);

        UserStatusResponse result = userService.updateUserStatus(targetId, null, request);

        assertThat(result.getStatus()).isEqualTo("INACTIVE");
    }
}
