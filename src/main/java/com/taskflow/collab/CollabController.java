package com.taskflow.collab;

import com.taskflow.board.BoardService;
import com.taskflow.task.TaskDto;
import com.taskflow.task.TaskService;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class CollabController {

    private final SimpMessagingTemplate broker;
    private final CollabService collabService;
    private final TaskService taskService;
    private final UserRepository userRepository;
    private final BoardService boardService;

    private User resolve(Principal principal) {
        return userRepository.findByEmail(principal.getName()).orElseThrow();
    }

    // 寫入操作統一走 canWriteBoard 安全閘（部長唯讀、歸檔唯讀、非成員拒絕）
    private void checkWrite(Long boardId, User user) {
        if (!boardService.canWriteBoard(boardId, user)) {
            throw new SecurityException("無編輯此看板的權限");
        }
    }

    private TaskChangeMessage.UserInfo operatorOf(User user) {
        TaskChangeMessage.UserInfo ui = new TaskChangeMessage.UserInfo();
        ui.setUserId(user.getId());
        ui.setDisplayName(user.getDisplayName());
        ui.setColor(collabService.userColor(user.getId()));
        return ui;
    }

    private void broadcast(Long boardId, TaskChangeMessage.Type type, Long taskId,
                           Object payload, User operator) {
        TaskChangeMessage msg = new TaskChangeMessage();
        msg.setType(type);
        msg.setTaskId(taskId);
        msg.setPayload(payload);
        msg.setOperator(operatorOf(operator));
        broker.convertAndSend("/topic/board/" + boardId + "/tasks", msg);
    }

    @MessageMapping("/board/{boardId}/task/create")
    public void onTaskCreate(@DestinationVariable Long boardId,
                             @Payload TaskDto.SaveRequest req, Principal principal) {
        User user = resolve(principal);
        checkWrite(boardId, user);
        var created = TaskDto.Response.from(taskService.createTask(boardId, req, user));
        broadcast(boardId, TaskChangeMessage.Type.TASK_CREATE, created.getId(), created, user);
    }

    @MessageMapping("/board/{boardId}/task/update")
    public void onTaskUpdate(@DestinationVariable Long boardId,
                             @Payload TaskDto.SaveRequest req,
                             @Header("taskId") Long taskId, Principal principal) {
        User user = resolve(principal);
        checkWrite(boardId, user);
        var updated = TaskDto.Response.from(taskService.updateTask(boardId, taskId, req, user));
        broadcast(boardId, TaskChangeMessage.Type.TASK_UPDATE, taskId, updated, user);
    }

    // MOVE 廣播全板任務快照：欄內重排影響多筆 sortOrder，整批同步最不易漂移
    @MessageMapping("/board/{boardId}/task/{taskId}/move")
    public void onTaskMove(@DestinationVariable Long boardId,
                           @DestinationVariable Long taskId,
                           @Payload TaskDto.MoveRequest req, Principal principal) {
        User user = resolve(principal);
        checkWrite(boardId, user);
        taskService.moveTask(boardId, taskId, req, user);
        var snapshot = taskService.listByBoard(boardId).stream()
            .map(TaskDto.Response::from).toList();
        broadcast(boardId, TaskChangeMessage.Type.TASK_MOVE, taskId, snapshot, user);
    }

    @MessageMapping("/board/{boardId}/task/{taskId}/delete")
    public void onTaskDelete(@DestinationVariable Long boardId,
                             @DestinationVariable Long taskId, Principal principal) {
        User user = resolve(principal);
        checkWrite(boardId, user);
        taskService.deleteTask(boardId, taskId, user);
        broadcast(boardId, TaskChangeMessage.Type.TASK_DELETE, taskId, null, user);
    }

    // SockJS 無法攔截 onConnect，前端連線後主動 publish join 以補足 presence 廣播
    @MessageMapping("/board/{boardId}/join")
    public void handleJoin(@DestinationVariable Long boardId, Principal principal) {
        User user = resolve(principal);
        collabService.join(boardId, user.getId(), user.getDisplayName());
        PresenceMessage msg = new PresenceMessage();
        msg.setType(PresenceMessage.Type.JOIN);
        msg.setUserId(user.getId());
        msg.setDisplayName(user.getDisplayName());
        msg.setColor(collabService.userColor(user.getId()));
        broker.convertAndSend("/topic/board/" + boardId + "/presence", msg);
        // 回送目前在線清單給剛加入者（透過同一 topic 廣播全量清單，前端以 userId 去重）
        collabService.getOnlineUsers(boardId).forEach(p ->
            broker.convertAndSend("/topic/board/" + boardId + "/presence", p));
    }

    @MessageMapping("/board/{boardId}/leave")
    public void handleLeave(@DestinationVariable Long boardId, Principal principal) {
        User user = resolve(principal);
        collabService.leave(boardId, user.getId());
        PresenceMessage msg = new PresenceMessage();
        msg.setType(PresenceMessage.Type.LEAVE);
        msg.setUserId(user.getId());
        msg.setDisplayName(user.getDisplayName());
        msg.setColor(collabService.userColor(user.getId()));
        broker.convertAndSend("/topic/board/" + boardId + "/presence", msg);
    }
}
