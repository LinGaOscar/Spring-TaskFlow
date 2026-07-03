package com.taskflow.collab;

import com.taskflow.board.Board;
import com.taskflow.board.BoardService;
import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

// 部門隔離延伸到即時層的安全閘測試：SUBSCRIBE 幀必須過 canReadBoard
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CollabSubscribeAuthTest {

    @Autowired BoardSubscribeAuthInterceptor interceptor;
    @Autowired BoardService boardService;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;

    final MessageChannel channel = mock(MessageChannel.class);

    User chief;      // 資訊科科長，看板建立者
    User outsider;   // 資訊科2 成員，無此看板權限
    Board board;

    @BeforeEach
    void setUp() {
        Department div = new Department(); div.setName("資訊部"); departmentRepository.save(div);
        Department sec = new Department(); sec.setName("資訊科"); sec.setParent(div); departmentRepository.save(sec);
        Department sec2 = new Department(); sec2.setName("資訊科2"); sec2.setParent(div); departmentRepository.save(sec2);

        chief = newUser("chief@t.com", User.Role.SECTION_CHIEF, sec);
        outsider = newUser("out@t.com", User.Role.PROJECT_MEMBER, sec2);

        var req = new com.taskflow.board.BoardDto.CreateRequest();
        req.setName("授權測試看板");
        board = boardService.createBoard(req, chief.getId());
    }

    User newUser(String email, User.Role role, Department dept) {
        User u = new User();
        u.setEmail(email); u.setPasswordHash("x"); u.setDisplayName(email);
        u.setRole(role); u.setDepartment(dept);
        return userRepository.save(u);
    }

    // 建立帶身份的 STOMP SUBSCRIBE 幀；user 為 null 模擬未登入
    Message<byte[]> subscribeFrame(String destination, User user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionId("s1");
        accessor.setSubscriptionId("sub-0");
        if (user != null) {
            accessor.setUser(user::getEmail);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void 跨科成員訂閱看板頻道_被拒() {
        Message<byte[]> frame =
            subscribeFrame("/topic/board/" + board.getId() + "/tasks", outsider);
        assertThatThrownBy(() -> interceptor.preSend(frame, channel))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 跨科成員訂閱presence初始清單_被拒() {
        Message<byte[]> frame =
            subscribeFrame("/app/board/" + board.getId() + "/presence", outsider);
        assertThatThrownBy(() -> interceptor.preSend(frame, channel))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 未登入訂閱看板頻道_被拒() {
        Message<byte[]> frame =
            subscribeFrame("/topic/board/" + board.getId() + "/tasks", null);
        assertThatThrownBy(() -> interceptor.preSend(frame, channel))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 有權限者訂閱看板頻道_放行() {
        Message<byte[]> frame =
            subscribeFrame("/topic/board/" + board.getId() + "/tasks", chief);
        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
    }

    @Test
    void 非看板頻道的訂閱_不攔截() {
        // 未來若有其他公共頻道，不應被看板授權邏輯誤傷
        Message<byte[]> frame = subscribeFrame("/topic/announcements", outsider);
        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
    }
}
