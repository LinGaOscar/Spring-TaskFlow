package com.taskflow.template;

import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TemplateServiceTest {

    @Autowired TemplateService templateService;
    @Autowired TemplateRepository templateRepository;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;

    User chief;
    User member;
    User chief2;
    TaskTemplate system;

    @BeforeEach
    void setUp() {
        Department div = new Department(); div.setName("資訊部"); departmentRepository.save(div);
        Department sec = new Department(); sec.setName("資訊科"); sec.setParent(div); departmentRepository.save(sec);
        Department sec2 = new Department(); sec2.setName("資訊科2"); sec2.setParent(div); departmentRepository.save(sec2);
        chief  = newUser("chief@t.com",  User.Role.SECTION_CHIEF,  sec);
        member = newUser("member@t.com", User.Role.PROJECT_MEMBER, sec);
        chief2 = newUser("chief2@t.com", User.Role.SECTION_CHIEF,  sec2);

        system = new TaskTemplate();
        system.setName("Bug 修復"); system.setTitle("[Bug] "); system.setSystem(true);
        templateRepository.save(system);
    }

    User newUser(String email, User.Role role, Department dept) {
        User u = new User();
        u.setEmail(email); u.setPasswordHash("x"); u.setDisplayName(email);
        u.setRole(role); u.setDepartment(dept);
        return userRepository.save(u);
    }

    TemplateDto.SaveRequest req(String name) {
        TemplateDto.SaveRequest r = new TemplateDto.SaveRequest();
        r.setName(name); r.setTitle(name + "標題");
        return r;
    }

    @Test
    void 可見範圍_系統模板加本科自訂() {
        templateService.create(req("本科模板"), chief);
        templateService.create(req("他科模板"), chief2);
        assertThat(templateService.listVisible(member))
            .extracting(TaskTemplate::getName)
            .containsExactlyInAnyOrder("Bug 修復", "本科模板");
    }

    @Test
    void member建立模板_被拒() {
        assertThatThrownBy(() -> templateService.create(req("偷建"), member))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void 系統模板不可修改刪除() {
        assertThatThrownBy(() -> templateService.update(system.getId(), req("改名"), chief))
            .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> templateService.delete(system.getId(), chief))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void 跨科修改自訂模板_被拒() {
        TaskTemplate mine = templateService.create(req("本科模板"), chief);
        assertThatThrownBy(() -> templateService.update(mine.getId(), req("他科亂改"), chief2))
            .isInstanceOf(SecurityException.class);
    }
}
