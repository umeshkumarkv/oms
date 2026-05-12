package com.brokerdealer.authservice.controller;

import com.brokerdealer.authservice.config.SecurityConfig;
import com.brokerdealer.authservice.domain.UserRole;
import com.brokerdealer.authservice.domain.UserStatus;
import com.brokerdealer.authservice.dto.response.UserResponse;
import com.brokerdealer.authservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void shouldReturn200WithUserListForADMIN() throws Exception {
        List<UserResponse> users = List.of(
                buildUserResponse("trader.john", "TRADER", "ACTIVE"),
                buildUserResponse("admin.user", "ADMIN", "ACTIVE")
        );
        when(userService.listUsers(null, null)).thenReturn(users);

        mockMvc.perform(get("/api/v1/users")
                        .header("X-User-Role", "ADMIN"))
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
    void shouldReturn403ForTRADERRole() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturn403ForVIEWERRole() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturn403ForOPERATIONSRole() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturn403WhenXUserRoleHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturnOnlyTRADERUsersWhenRoleFilterApplied() throws Exception {
        List<UserResponse> traders = List.of(
                buildUserResponse("trader.john", "TRADER", "ACTIVE"),
                buildUserResponse("trader.sarah", "TRADER", "ACTIVE")
        );
        when(userService.listUsers(UserRole.TRADER, null)).thenReturn(traders);

        mockMvc.perform(get("/api/v1/users")
                        .header("X-User-Role", "ADMIN")
                        .param("role", "TRADER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(2));

        verify(userService).listUsers(UserRole.TRADER, null);
    }

    @Test
    void shouldReturnOnlyINACTIVEUsersWhenStatusFilterApplied() throws Exception {
        List<UserResponse> inactiveUsers = List.of(
                buildUserResponse("old.trader", "TRADER", "INACTIVE")
        );
        when(userService.listUsers(null, UserStatus.INACTIVE)).thenReturn(inactiveUsers);

        mockMvc.perform(get("/api/v1/users")
                        .header("X-User-Role", "ADMIN")
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(1));

        verify(userService).listUsers(null, UserStatus.INACTIVE);
    }

    @Test
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

        mockMvc.perform(get("/api/v1/users")
                        .header("X-User-Role", "ADMIN"))
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
    void shouldReturnEmptyListWhenNoUsersMatchFilter() throws Exception {
        when(userService.listUsers(UserRole.VIEWER, UserStatus.INACTIVE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users")
                        .header("X-User-Role", "ADMIN")
                        .param("role", "VIEWER")
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0));
    }
}
