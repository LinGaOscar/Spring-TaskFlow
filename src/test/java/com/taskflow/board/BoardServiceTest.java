package com.taskflow.board;

import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BoardServiceTest {

    @Autowired BoardService boardService;
    @Autowired BoardRepository boardRepository;
    @Autowired BoardMemberRepository memberRepository;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository deptRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User manager, member;
    Department dept;

    @BeforeEach
    void setup() {
        memberRepository.deleteAll();
        boardRepository.deleteAll();
        userRepository.deleteAll();
        deptRepository.deleteAll();

        dept = new Department(); dept.setName("IT"); deptRepository.save(dept);

        manager = new User(); manager.setEmail("mgr@t.com");
        manager.setPasswordHash(passwordEncoder.encode("p")); manager.setDisplayName("Manager");
        manager.setRole(User.Role.SECTION_CHIEF); manager.setDepartment(dept);
        userRepository.save(manager);

        member = new User(); member.setEmail("mem@t.com");
        member.setPasswordHash(passwordEncoder.encode("p")); member.setDisplayName("Member");
        member.setRole(User.Role.PROJECT_MEMBER); userRepository.save(member);
    }

    @Test
    void createBoard_setsOwnerAsCreator() {
        BoardDto.CreateRequest req = new BoardDto.CreateRequest();
        req.setName("新看板"); req.setDepartmentId(dept.getId());
        Board proj = boardService.createBoard(req, manager.getId());
        assertThat(proj.getOwner().getId()).isEqualTo(manager.getId());
    }

    @Test
    void addMember_memberCanBeFound() {
        BoardDto.CreateRequest req = new BoardDto.CreateRequest();
        req.setName("P"); req.setDepartmentId(dept.getId());
        Board proj = boardService.createBoard(req, manager.getId());

        boardService.addMember(proj.getId(), member.getId(), manager.getId());

        assertThat(boardService.isMember(proj.getId(), member.getId())).isTrue();
    }

    @Test
    void searchHistory_依關鍵字與日期區間過濾() {
        // 建兩個歸檔看板：名稱不同、歸檔日不同
        BoardDto.CreateRequest reqA = new BoardDto.CreateRequest();
        reqA.setName("2025 網站改版"); reqA.setDepartmentId(dept.getId());
        Board a = boardService.createBoard(reqA, manager.getId());

        BoardDto.CreateRequest reqB = new BoardDto.CreateRequest();
        reqB.setName("內部工具"); reqB.setDepartmentId(dept.getId());
        Board b = boardService.createBoard(reqB, manager.getId());

        boardService.archiveBoard(a.getId(), manager);
        boardService.archiveBoard(b.getId(), manager);

        List<Board> byKeyword = boardService.searchHistory(manager, "網站", null, null);
        assertThat(byKeyword).extracting(Board::getName).containsExactly("2025 網站改版");

        List<Board> byDate = boardService.searchHistory(manager, null,
            LocalDate.now().plusDays(1), null);   // 起日在未來 → 應查無資料
        assertThat(byDate).isEmpty();
    }
}
