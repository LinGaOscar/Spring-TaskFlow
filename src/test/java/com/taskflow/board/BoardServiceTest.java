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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

// 全類別 @Transactional：canReadBoard/canWriteBoard 未自行開啟 session，正式環境靠
// spring.jpa.open-in-view（預設 true）在 HTTP 請求週期內維持 session 供 lazy load 使用，
// 測試沒有該 filter，需自行包一層交易來模擬同等的 session 存活範圍（詳見修復報告的發現）
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BoardServiceTest {

    @Autowired BoardService boardService;
    @Autowired BoardRepository boardRepository;
    @Autowired BoardMemberRepository memberRepository;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository deptRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User manager, member, director, chief2, leader;
    Department dept, parentDept, dept2;

    @BeforeEach
    void setup() {
        memberRepository.deleteAll();
        boardRepository.deleteAll();
        userRepository.deleteAll();
        deptRepository.deleteAll();

        // 部門雙層結構（部 → 科），用於驗證部長跨科唯讀規則
        parentDept = new Department(); parentDept.setName("研發部"); deptRepository.save(parentDept);
        dept = new Department(); dept.setName("IT"); dept.setParent(parentDept); deptRepository.save(dept);
        dept2 = new Department(); dept2.setName("業務科"); dept2.setParent(parentDept); deptRepository.save(dept2);

        manager = new User(); manager.setEmail("mgr@t.com");
        manager.setPasswordHash(passwordEncoder.encode("p")); manager.setDisplayName("Manager");
        manager.setRole(User.Role.SECTION_CHIEF); manager.setDepartment(dept);
        userRepository.save(manager);

        member = new User(); member.setEmail("mem@t.com");
        member.setPasswordHash(passwordEncoder.encode("p")); member.setDisplayName("Member");
        member.setRole(User.Role.PROJECT_MEMBER); userRepository.save(member);

        // 部長：掛在部層級（parentDept），用於驗證跨科唯讀 / 部長自建看板可寫
        director = new User(); director.setEmail("dir@t.com");
        director.setPasswordHash(passwordEncoder.encode("p")); director.setDisplayName("Director");
        director.setRole(User.Role.DIRECTOR); director.setDepartment(parentDept);
        userRepository.save(director);

        // 另一科科長：用於驗證跨科不可寫/不可讀
        chief2 = new User(); chief2.setEmail("chief2@t.com");
        chief2.setPasswordHash(passwordEncoder.encode("p")); chief2.setDisplayName("Chief2");
        chief2.setRole(User.Role.SECTION_CHIEF); chief2.setDepartment(dept2);
        userRepository.save(chief2);

        // 看板 Leader：與 manager 同科，用於驗證「加入成員才可寫」規則
        leader = new User(); leader.setEmail("leader@t.com");
        leader.setPasswordHash(passwordEncoder.encode("p")); leader.setDisplayName("Leader");
        leader.setRole(User.Role.PROJECT_LEADER); leader.setDepartment(dept);
        userRepository.save(leader);
    }

    @Test
    void createBoard_setsOwnerAsCreator() {
        BoardDto.CreateRequest req = new BoardDto.CreateRequest();
        req.setName("新看板");
        Board proj = boardService.createBoard(req, manager.getId());
        assertThat(proj.getOwner().getId()).isEqualTo(manager.getId());
    }

    @Test
    void addMember_memberCanBeFound() {
        BoardDto.CreateRequest req = new BoardDto.CreateRequest();
        req.setName("P");
        Board proj = boardService.createBoard(req, manager.getId());

        boardService.addMember(proj.getId(), member.getId(), manager.getId());

        assertThat(boardService.isMember(proj.getId(), member.getId())).isTrue();
    }

    @Test
    void searchHistory_依關鍵字與日期區間過濾() {
        // 建兩個歸檔看板：名稱不同、歸檔日不同
        BoardDto.CreateRequest reqA = new BoardDto.CreateRequest();
        reqA.setName("2025 網站改版");
        Board a = boardService.createBoard(reqA, manager.getId());

        BoardDto.CreateRequest reqB = new BoardDto.CreateRequest();
        reqB.setName("內部工具");
        Board b = boardService.createBoard(reqB, manager.getId());

        boardService.archiveBoard(a.getId(), manager);
        boardService.archiveBoard(b.getId(), manager);

        List<Board> byKeyword = boardService.searchHistory(manager, "網站", null, null);
        assertThat(byKeyword).extracting(Board::getName).containsExactly("2025 網站改版");

        List<Board> byDate = boardService.searchHistory(manager, null,
            LocalDate.now().plusDays(1), null);   // 起日在未來 → 應查無資料
        assertThat(byDate).isEmpty();
    }

    // ===== 權限矩陣測試 =====

    @Test
    void 部長_跨科唯讀() {
        // 部長掛在部層級（parentDept），對子科科長建立的看板只能讀、不能寫
        BoardDto.CreateRequest req = new BoardDto.CreateRequest();
        req.setName("科看板");
        Board board = boardService.createBoard(req, manager.getId());

        assertThat(boardService.canWriteBoard(board.getId(), director)).isFalse();
        assertThat(boardService.canReadBoard(board.getId(), director)).isTrue();
    }

    @Test
    void 部長_自建看板可寫() {
        // 部長自己建立的看板，owner 即部長本人，是唯一可寫的例外情境
        BoardDto.CreateRequest req = new BoardDto.CreateRequest();
        req.setName("部長自建看板");
        Board board = boardService.createBoard(req, director.getId());

        assertThat(boardService.canWriteBoard(board.getId(), director)).isTrue();
    }

    @Test
    void 科長_本科可寫跨科不可寫不可讀() {
        // 本科看板：科長可寫
        BoardDto.CreateRequest reqOwn = new BoardDto.CreateRequest();
        reqOwn.setName("本科看板");
        Board ownBoard = boardService.createBoard(reqOwn, manager.getId());
        assertThat(boardService.canWriteBoard(ownBoard.getId(), manager)).isTrue();

        // 他科（dept2）看板：manager 既不可寫也不可讀
        BoardDto.CreateRequest reqOther = new BoardDto.CreateRequest();
        reqOther.setName("他科看板");
        Board otherBoard = boardService.createBoard(reqOther, chief2.getId());

        assertThat(boardService.canWriteBoard(otherBoard.getId(), manager)).isFalse();
        assertThat(boardService.canReadBoard(otherBoard.getId(), manager)).isFalse();
    }

    @Test
    void leader與member_加入成員後才可寫() {
        BoardDto.CreateRequest req = new BoardDto.CreateRequest();
        req.setName("同科看板");
        Board board = boardService.createBoard(req, manager.getId());

        // 尚未加入成員前，Leader/Member 皆不可寫
        assertThat(boardService.canWriteBoard(board.getId(), leader)).isFalse();
        assertThat(boardService.canWriteBoard(board.getId(), member)).isFalse();

        boardService.addMember(board.getId(), leader.getId(), manager.getId());
        boardService.addMember(board.getId(), member.getId(), manager.getId());

        // 加入成員後即可寫
        assertThat(boardService.canWriteBoard(board.getId(), leader)).isTrue();
        assertThat(boardService.canWriteBoard(board.getId(), member)).isTrue();
    }

    @Test
    void 歸檔後所有角色皆不可寫但原可讀者仍可讀() {
        BoardDto.CreateRequest req = new BoardDto.CreateRequest();
        req.setName("待歸檔看板");
        Board board = boardService.createBoard(req, manager.getId());
        boardService.addMember(board.getId(), leader.getId(), manager.getId());
        boardService.addMember(board.getId(), member.getId(), manager.getId());

        boardService.archiveBoard(board.getId(), manager);

        // 歸檔後進入歷史唯讀狀態，含負責人科長本人、Leader、Member、部長皆不可寫
        assertThat(boardService.canWriteBoard(board.getId(), manager)).isFalse();
        assertThat(boardService.canWriteBoard(board.getId(), leader)).isFalse();
        assertThat(boardService.canWriteBoard(board.getId(), member)).isFalse();
        assertThat(boardService.canWriteBoard(board.getId(), director)).isFalse();

        // 原本可讀的角色歸檔後仍可讀（科長本科讀、部長跨科讀）
        assertThat(boardService.canReadBoard(board.getId(), manager)).isTrue();
        assertThat(boardService.canReadBoard(board.getId(), director)).isTrue();
    }

    @Test
    void 部長自建看板歸檔後連自己也不可寫() {
        // 驗證歸檔的唯讀規則對 owner 一律強制生效，即使 owner 是部長本人
        BoardDto.CreateRequest req = new BoardDto.CreateRequest();
        req.setName("部長自建看板2");
        Board board = boardService.createBoard(req, director.getId());
        assertThat(boardService.canWriteBoard(board.getId(), director)).isTrue(); // 歸檔前可寫

        boardService.archiveBoard(board.getId(), director);

        assertThat(boardService.canWriteBoard(board.getId(), director)).isFalse();
        assertThat(boardService.canReadBoard(board.getId(), director)).isTrue();
    }

    @Test
    void member角色無法建立看板() {
        // 建立看板的角色檢查目前落在 User.canCreateBoard()（由 BoardController 呼叫），
        // Service 層 createBoard 本身不做角色檢查，因此針對該方法直接驗證
        assertThat(member.canCreateBoard()).isFalse();
        assertThat(manager.canCreateBoard()).isTrue();
        assertThat(director.canCreateBoard()).isTrue();
        assertThat(leader.canCreateBoard()).isTrue();
    }
}
