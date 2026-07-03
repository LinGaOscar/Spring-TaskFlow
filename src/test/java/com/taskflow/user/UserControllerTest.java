package com.taskflow.user;

import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import org.junit.jupiter.api.AfterEach;
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
    @Autowired DepartmentRepository departmentRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        departmentRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "chief@t.com", roles = "SECTION_CHIEF")
    void listUsers_authenticated_returns200() throws Exception {
        // /api/users 現在依呼叫者 department 過濾，需先建立與 @WithMockUser username 對應的資料庫使用者
        Department dept = new Department();
        dept.setName("資訊科");
        departmentRepository.save(dept);

        User user = new User();
        user.setEmail("chief@t.com");
        user.setPasswordHash("x");
        user.setDisplayName("Chief");
        user.setRole(User.Role.SECTION_CHIEF);
        user.setDepartment(dept);
        userRepository.save(user);

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
