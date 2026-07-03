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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoardControllerMembersTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BoardMemberRepository boardMemberRepository;
    @Autowired BoardRepository boardRepository;
    @Autowired DepartmentRepository departmentRepository;

    @BeforeEach
    void setUp() {
        // 建立與 @WithMockUser username 對應的資料庫使用者
        // SECTION_CHIEF / PROJECT_LEADER 必屬某科，listForUser 依 department 過濾，缺少會 NPE
        Department dept = new Department();
        dept.setName("測試科");
        departmentRepository.save(dept);

        User chief = new User();
        chief.setEmail("chief@t.com");
        chief.setPasswordHash("x");
        chief.setDisplayName("Chief User");
        chief.setRole(User.Role.SECTION_CHIEF);
        chief.setDepartment(dept);
        userRepository.save(chief);

        User member = new User();
        member.setEmail("member1@t.com");
        member.setPasswordHash("x");
        member.setDisplayName("Member User");
        member.setRole(User.Role.PROJECT_MEMBER);
        userRepository.save(member);

        User director = new User();
        director.setEmail("director@t.com");
        director.setPasswordHash("x");
        director.setDisplayName("Director User");
        director.setRole(User.Role.DIRECTOR);
        userRepository.save(director);

        User leaderA = new User();
        leaderA.setEmail("leaderA@t.com");
        leaderA.setPasswordHash("x");
        leaderA.setDisplayName("Leader A");
        leaderA.setRole(User.Role.PROJECT_LEADER);
        leaderA.setDepartment(dept);
        userRepository.save(leaderA);

        User leaderB = new User();
        leaderB.setEmail("leaderB@t.com");
        leaderB.setPasswordHash("x");
        leaderB.setDisplayName("Leader B");
        leaderB.setRole(User.Role.PROJECT_LEADER);
        leaderB.setDepartment(dept);
        userRepository.save(leaderB);

        // 同科兩個看板分屬不同 Leader，驗證 Leader 只看得到自己負責的看板
        Board boardA = new Board();
        boardA.setName("Leader A 的看板");
        boardA.setDepartment(dept);
        boardA.setOwner(leaderA);
        boardRepository.save(boardA);

        Board boardB = new Board();
        boardB.setName("Leader B 的看板");
        boardB.setDepartment(dept);
        boardB.setOwner(leaderB);
        boardRepository.save(boardB);
    }

    @AfterEach
    void tearDown() {
        boardMemberRepository.deleteAll();
        boardRepository.deleteAll();
        userRepository.deleteAll();
        departmentRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "member1@t.com")
    void member角色進成員管理頁_被導回看板列表() throws Exception {
        mockMvc.perform(get("/admin/members"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/boards"));
    }

    @Test
    @WithMockUser(username = "director@t.com")
    void director角色進成員管理頁_也被導回看板列表() throws Exception {
        mockMvc.perform(get("/admin/members"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/boards"));
    }

    @Test
    @WithMockUser(username = "chief@t.com")
    void 科長可進成員管理頁_看得到本科全部看板() throws Exception {
        mockMvc.perform(get("/admin/members"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/members"))
            .andExpect(model().attribute("boards", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "leaderA@t.com")
    void leader只看得到自己負責的看板() throws Exception {
        mockMvc.perform(get("/admin/members"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/members"))
            .andExpect(model().attribute("boards", hasSize(1)));
    }

    // 部門隔離旁路防護：科長雖有權管理本科看板成員，但目標帳號若屬於其他科，仍應被拒絕加入
    @Test
    @WithMockUser(username = "chief@t.com")
    void addMember_跨科帳號被拒() throws Exception {
        Department otherDept = new Department();
        otherDept.setName("他科");
        departmentRepository.save(otherDept);

        User outsider = new User();
        outsider.setEmail("outsider@t.com");
        outsider.setPasswordHash("x");
        outsider.setDisplayName("Outsider");
        outsider.setRole(User.Role.PROJECT_MEMBER);
        outsider.setDepartment(otherDept);
        userRepository.save(outsider);

        Board boardA = boardRepository.findAll().stream()
            .filter(b -> b.getName().equals("Leader A 的看板"))
            .findFirst().orElseThrow();

        mockMvc.perform(post("/api/boards/{id}/members", boardA.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + outsider.getId() + "}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }
}
