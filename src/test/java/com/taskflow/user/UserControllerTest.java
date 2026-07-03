package com.taskflow.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;

    @Test
    @WithMockUser(roles = "SECTION_CHIEF")
    void listUsers_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listUsers_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "me@test.com", roles = "PROJECT_MEMBER")
    void getCurrentUser_returns200() throws Exception {
        User user = new User();
        user.setEmail("me@test.com");
        user.setPasswordHash("x");
        user.setDisplayName("Me");
        user.setRole(User.Role.PROJECT_MEMBER);
        userRepository.save(user);

        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("me@test.com"));
    }
}
