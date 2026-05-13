package com.brokerdealer.authservice.controller;

import com.brokerdealer.authservice.config.SecurityConfig;
import com.brokerdealer.authservice.domain.UserRole;
import com.brokerdealer.authservice.domain.UserStatus;
import com.brokerdealer.authservice.dto.response.UserResponse;
import com.brokerdealer.authservice.dto.response.UserStatusResponse;
import com.brokerdealer.authservice.exception.CannotDeactivateSelfException;
import com.brokerdealer.authservice.exception.UserNotFoundException;
import com.brokerdealer.authservice.exception.UsernameTakenException;
import com.brokerdealer.authservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    private UserResponse buildUserResponse(String username, String role, String status) {
        return UserResponse.builder()
                .userId(UUID.randomUUID())
                .username(username)
                .displayName(username)
                .role(role)
                .status(status)
                .createdAt(Instant.parse("2026-04-01T00:00:00Z"))
                .lastLoginAt(null)
                .build();
    }

    // ─── GET /api/v1/users ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200WithUserListForADMIN() throws Exception {
        List<UserResponse> users = List.of(
                buildUserResponse("trader.john", "TRADER", "ACTIVE"),
                buildUserResponse("admin.user", "ADMIN", "ACTIVE")
        );
        when(userService.listUsers(null, null)).thenReturn(users);

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(50))
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "TRADER")
    void shouldReturn403ForTRADERRole() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void shouldReturn403ForVIEWERRole() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void shouldReturn403ForOPERATIONSRole() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturn403WhenCallerIsNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnOnlyTRADERUsersWhenRoleFilterApplied() throws Exception {
        List<UserResponse> traders = List.of(
                buildUserResponse("trader.john", "TRADER", "ACTIVE"),
                buildUserResponse("trader.sarah", "TRADER", "ACTIVE")
        );
        when(userService.listUsers(UserRole.TRADER, null)).thenReturn(traders);

        mockMvc.perform(get("/api/v1/users").param("role", "TRADER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(2));

        verify(userService).listUsers(UserRole.TRADER, null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnOnlyINACTIVEUsersWhenStatusFilterApplied() throws Exception {
        List<UserResponse> inactiveUsers = List.of(
                buildUserResponse("old.trader", "TRADER", "INACTIVE")
        );
        when(userService.listUsers(null, UserStatus.INACTIVE)).thenReturn(inactiveUsers);

        mockMvc.perform(get("/api/v1/users").param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(1));

        verify(userService).listUsers(null, UserStatus.INACTIVE);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnResponseWithAllExpectedFields() throws Exception {
        UUID userId = UUID.randomUUID();
        UserResponse user = UserResponse.builder()
                .userId(userId)
                .username("trader.john")
                .displayName("John Trader")
                .role("TRADER")
                .status("ACTIVE")
                .createdAt(Instant.parse("2026-04-01T00:00:00Z"))
                .lastLoginAt(Instant.parse("2026-05-01T09:30:00Z"))
                .build();
        when(userService.listUsers(null, null)).thenReturn(List.of(user));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].username").value("trader.john"))
                .andExpect(jsonPath("$.data[0].displayName").value("John Trader"))
                .andExpect(jsonPath("$.data[0].role").value("TRADER"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].lastLoginAt").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnEmptyListWhenNoUsersMatchFilter() throws Exception {
        when(userService.listUsers(UserRole.VIEWER, UserStatus.INACTIVE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users")
                        .param("role", "VIEWER")
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0));
    }

    // ─── POST /api/v1/users ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn201WithEnvelopeOnCreate() throws Exception {
        UUID newUserId = UUID.randomUUID();
        UserResponse created = UserResponse.builder()
                .userId(newUserId)
                .username("new.trader")
                .displayName("New Trader")
                .role("TRADER")
                .status("ACTIVE")
                .createdAt(Instant.parse("2026-05-01T10:00:00Z"))
                .lastLoginAt(null)
                .build();
        when(userService.createUser(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.trader",
                                  "displayName": "New Trader",
                                  "role": "TRADER",
                                  "password": "SecurePass1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(newUserId.toString()))
                .andExpect(jsonPath("$.data.username").value("new.trader"))
                .andExpect(jsonPath("$.data.role").value("TRADER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn409WhenUsernameTaken() throws Exception {
        when(userService.createUser(any()))
                .thenThrow(new UsernameTakenException("USERNAME_TAKEN", "Username already taken: new.trader"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.trader",
                                  "displayName": "New Trader",
                                  "role": "TRADER",
                                  "password": "SecurePass1"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USERNAME_TAKEN"))
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400WhenUsernameBlank() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "displayName": "New Trader",
                                  "role": "TRADER",
                                  "password": "SecurePass1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400WhenPasswordTooShort() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.trader",
                                  "displayName": "New Trader",
                                  "role": "TRADER",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400WhenRoleInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.trader",
                                  "displayName": "New Trader",
                                  "role": "SUPERUSER",
                                  "password": "SecurePass1"
                                }
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400WhenDisplayNameMissing() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.trader",
                                  "role": "TRADER",
                                  "password": "SecurePass1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "TRADER")
    void shouldReturn403ForTRADEROnPostUsers() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.trader",
                                  "displayName": "New Trader",
                                  "role": "TRADER",
                                  "password": "SecurePass1"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void shouldReturn403ForVIEWEROnPostUsers() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.trader",
                                  "displayName": "New Trader",
                                  "role": "TRADER",
                                  "password": "SecurePass1"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(userService);
    }

    // ─── PATCH /api/v1/users/{userId}/status ────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200WithStatusAndUpdatedAt() throws Exception {
        UUID targetId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-05-13T10:00:00Z");
        UserStatusResponse response = UserStatusResponse.builder()
                .userId(targetId)
                .status("INACTIVE")
                .updatedAt(updatedAt)
                .build();
        when(userService.updateUserStatus(eq(targetId), any(), any())).thenReturn(response);

        mockMvc.perform(patch("/api/v1/users/{userId}/status", targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(targetId.toString()))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn404WhenUserIdNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(userService.updateUserStatus(eq(unknownId), any(), any()))
                .thenThrow(new UserNotFoundException("USER_NOT_FOUND", "User not found: " + unknownId));

        mockMvc.perform(patch("/api/v1/users/{userId}/status", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn403WhenSelfDeactivation() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(userService.updateUserStatus(eq(adminId), eq(adminId), any()))
                .thenThrow(new CannotDeactivateSelfException("CANNOT_DEACTIVATE_SELF",
                        "Admin cannot deactivate their own account"));

        mockMvc.perform(patch("/api/v1/users/{userId}/status", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", adminId.toString())
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CANNOT_DEACTIVATE_SELF"));
    }

    @Test
    @WithMockUser(roles = "TRADER")
    void shouldReturn403ForTRADEROnPatchStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{userId}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400WhenStatusMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{userId}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200WhenActivatingUser() throws Exception {
        UUID targetId = UUID.randomUUID();
        UserStatusResponse response = UserStatusResponse.builder()
                .userId(targetId)
                .status("ACTIVE")
                .updatedAt(Instant.parse("2026-05-13T11:00:00Z"))
                .build();
        when(userService.updateUserStatus(eq(targetId), any(), any())).thenReturn(response);

        mockMvc.perform(patch("/api/v1/users/{userId}/status", targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
