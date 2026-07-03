package com.taskflow.board;

import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class BoardControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BoardMemberRepository boardMemberRepository;
    @Autowired BoardRepository boardRepository;
    @Autowired DepartmentRepository departmentRepository;

    @BeforeEach
    void setUp() {
        // 建立與 @WithMockUser username 對應的資料庫使用者
        // SECTION_CHIEF 必屬某科，listForUser 依 department 過濾，缺少會 NPE
        if (userRepository.findByEmail("admin@test.com").isEmpty()) {
            Department dept = new Department();
            dept.setName("測試科");
            departmentRepository.save(dept);

            User u = new User();
            u.setEmail("admin@test.com");
            u.setPasswordHash("x");
            u.setDisplayName("Admin User");
            u.setRole(User.Role.SECTION_CHIEF);
            u.setDepartment(dept);
            userRepository.save(u);
        }
    }

    @AfterEach
    void tearDown() {
        boardMemberRepository.deleteAll();
        boardRepository.deleteAll();
        userRepository.deleteAll();
        departmentRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "SECTION_CHIEF")
    void listBoards_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/boards"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listBoards_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/api/boards"))
            .andExpect(status().is3xxRedirection());
    }
}
