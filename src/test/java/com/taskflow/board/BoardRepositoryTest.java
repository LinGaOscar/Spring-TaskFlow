package com.taskflow.board;

import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class BoardRepositoryTest {

    @Autowired BoardRepository boardRepository;
    @Autowired BoardMemberRepository memberRepository;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository deptRepository;

    @Test
    void findByDepartmentId_returnsBoards() {
        Department dept = new Department(); dept.setName("IT"); deptRepository.save(dept);
        User owner = new User(); owner.setEmail("o@test.com"); owner.setPasswordHash("x");
        owner.setDisplayName("Owner"); owner.setRole(User.Role.PROJECT_MEMBER); userRepository.save(owner);

        Board p = new Board(); p.setName("測試看板");
        p.setDepartment(dept); p.setOwner(owner); p.setCreatedBy(owner);
        boardRepository.save(p);

        List<Board> result = boardRepository.findByDepartmentIdAndArchived(dept.getId(), false);
        assertThat(result).hasSize(1);
    }
}
