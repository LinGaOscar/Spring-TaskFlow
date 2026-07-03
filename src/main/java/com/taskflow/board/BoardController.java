package com.taskflow.board;

import com.taskflow.common.ApiResponse;
import com.taskflow.task.TaskDto;
import com.taskflow.task.TaskExportService;
import com.taskflow.task.TaskService;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final BoardMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final TaskExportService exportService;

    // 返回 Thymeleaf 靜態殼頁，資料由前端 API 非同步載入
    @GetMapping("/boards")
    public String boardsPage() { return "board/list"; }

    // 歷史區：全員可查詢，可見範圍依角色部門隔離
    @GetMapping("/boards/history")
    public String history(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          Model model, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        model.addAttribute("boards", boardService.searchHistory(user, keyword, from, to));
        model.addAttribute("keyword", keyword);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        // 供頁面比對 PROJECT_LEADER 是否為看板負責人，決定是否顯示還原鈕（與後端 checkArchivePermission 對齊）
        model.addAttribute("currentUserId", user.getId());
        return "board/history";
    }

    // 只有科長或 Leader 才能進入成員管理頁，其他角色（含部長）導回看板列表
    // 可管理範圍：科長看本科全部看板，Leader 只看自己負責的看板，避免越權操作他人看板成員
    @GetMapping("/admin/members")
    public String membersPage(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        if (!user.canManageSection()) return "redirect:/boards";
        List<BoardDto.Response> boards = boardService.listForUser(user, false).stream()
            .filter(b -> user.getRole() == User.Role.SECTION_CHIEF
                || (b.getOwner() != null && b.getOwner().getId().equals(user.getId())))
            .map(BoardDto.Response::from)
            .toList();
        model.addAttribute("boards", boards);
        return "admin/members";
    }

    // 看板頁：SSR 只出骨架與權限旗標，任務資料由前端 REST/WebSocket 載入
    @GetMapping("/boards/{id}")
    public String detail(@PathVariable Long id, Model model, Principal principal) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        try {
            if (!boardService.canReadBoard(id, user)) {
                return "redirect:/boards";
            }
            Board board = boardService.getById(id);
            model.addAttribute("board", board);
            model.addAttribute("canWrite", boardService.canWriteBoard(id, user));
            model.addAttribute("currentUserId", user.getId());
            return "board/detail";
        } catch (EntityNotFoundException e) {
            return "error/404";
        }
    }

    // 依角色回傳對應可見看板清單，支援 archived 參數切換現行/歷史
    @GetMapping("/api/boards")
    @ResponseBody
    public ApiResponse<List<BoardDto.Response>> listBoards(
            @RequestParam(defaultValue = "false") boolean archived,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        return ApiResponse.ok(
            boardService.listForUser(user, archived)
                .stream().map(BoardDto.Response::from).toList());
    }

    // 歸檔後看板進入唯讀歷史，不刪資料
    @PostMapping("/api/boards/{id}/archive")
    @ResponseBody
    public ApiResponse<Void> archiveBoard(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        boardService.archiveBoard(id, user);
        return ApiResponse.ok(null);
    }

    // 還原歸檔讓看板回到可編輯狀態
    @PostMapping("/api/boards/{id}/unarchive")
    @ResponseBody
    public ApiResponse<Void> unarchiveBoard(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        boardService.unarchiveBoard(id, user);
        return ApiResponse.ok(null);
    }

    // 只有科長與 Leader 才能建立看板，PROJECT_MEMBER 無此權限
    @PostMapping("/api/boards")
    @ResponseBody
    public ApiResponse<BoardDto.Response> createBoard(
            @Valid @RequestBody BoardDto.CreateRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        // 只有科長與看板Leader才能建立看板
        if (!user.canCreateBoard()) {
            throw new SecurityException("您沒有建立看板的權限");
        }
        return ApiResponse.ok(BoardDto.Response.from(
            boardService.createBoard(req, user.getId())));
    }

    @GetMapping("/api/boards/{id}")
    @ResponseBody
    public ApiResponse<BoardDto.Response> getBoard(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        checkBoardReadAccess(id, userDetails);
        return ApiResponse.ok(BoardDto.Response.from(boardService.getById(id)));
    }

    // 成員清單用於 WBS owner 下拉選單，只有有讀取權限者才能查詢
    @GetMapping("/api/boards/{id}/members")
    @ResponseBody
    public ApiResponse<List<BoardDto.MemberResponse>> getMembers(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        checkBoardReadAccess(id, userDetails);
        return ApiResponse.ok(memberRepository.findByIdBoardId(id)
            .stream().map(BoardDto.MemberResponse::from).toList());
    }

    // 統一讀取權限檢查，避免各 GET 端點重複撰寫
    private void checkBoardReadAccess(Long id, UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        if (!boardService.canReadBoard(id, user)) {
            throw new SecurityException("無存取權限");
        }
    }

    // 匯出是唯讀操作：凡可檢視看板者（含歸檔、部長跨科唯讀）皆可使用
    @GetMapping("/api/boards/{id}/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportTasks(@PathVariable Long id,
            @RequestParam(defaultValue = "json") String format,
            @AuthenticationPrincipal UserDetails userDetails) {
        checkBoardReadAccess(id, userDetails);
        List<TaskDto.Response> tasks = taskService.listByBoard(id).stream()
            .map(TaskDto.Response::from).toList();
        String base = "board-" + id + "-tasks";
        return switch (format) {
            case "csv" -> download(exportService.toCsv(tasks).getBytes(StandardCharsets.UTF_8),
                base + ".csv", "text/csv; charset=UTF-8");
            case "xlsx" -> download(exportService.toXlsx(tasks), base + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> download(exportService.toJson(tasks).getBytes(StandardCharsets.UTF_8),
                base + ".json", "application/json");
        };
    }

    private ResponseEntity<byte[]> download(byte[] body, String filename, String contentType) {
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .header("Content-Type", contentType)
            .body(body);
    }

    // 科長可管理本科任何看板成員；看板負責人可管理自己的看板
    @PostMapping("/api/boards/{id}/members")
    @ResponseBody
    public ApiResponse<Void> addMember(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User caller = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        Board board = boardService.getById(id);
        // 科長可管理本科任何看板成員；看板負責人可管理自己的看板
        boolean isSectionChief = caller.getRole() == User.Role.SECTION_CHIEF
            && board.getDepartment() != null
            && board.getDepartment().getId().equals(caller.getDepartment().getId());
        boolean isBoardOwner = board.getOwner().getId().equals(caller.getId());
        if (!isSectionChief && !isBoardOwner) {
            throw new SecurityException("只有科長或看板負責人才可管理成員");
        }
        Long targetUserId = body.get("userId");
        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        // 成員必須與看板同科，避免跨科帳號被加入看板成員造成部門隔離被繞過
        if (board.getDepartment() == null || targetUser.getDepartment() == null
            || !board.getDepartment().getId().equals(targetUser.getDepartment().getId())) {
            throw new IllegalArgumentException("成員必須為本科帳號");
        }
        boardService.addMember(id, targetUserId, caller.getId());
        return ApiResponse.ok(null);
    }

    // 移除成員後立即失去 WBS 編輯權限
    @DeleteMapping("/api/boards/{id}/members/{userId}")
    @ResponseBody
    public ApiResponse<Void> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User caller = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        Board board = boardService.getById(id);
        boolean isSectionChief = caller.getRole() == User.Role.SECTION_CHIEF
            && board.getDepartment() != null
            && board.getDepartment().getId().equals(caller.getDepartment().getId());
        boolean isBoardOwner = board.getOwner().getId().equals(caller.getId());
        if (!isSectionChief && !isBoardOwner) {
            throw new SecurityException("只有科長或看板負責人才可移除成員");
        }
        boardService.removeMember(id, userId);
        return ApiResponse.ok(null);
    }

    // 科長（本科看板）或看板 owner（含部長自建）可變更負責人
    @PutMapping("/api/boards/{id}/owner")
    @ResponseBody
    public ApiResponse<Void> changeOwner(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User caller = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Board board = boardService.getById(id);
        boolean isSectionChief = caller.getRole() == User.Role.SECTION_CHIEF
            && board.getDepartment() != null
            && board.getDepartment().getId().equals(caller.getDepartment().getId());
        boolean isBoardOwner = board.getOwner().getId().equals(caller.getId());
        if (!isSectionChief && !isBoardOwner) throw new SecurityException("無權變更負責人");
        Long newOwnerId = body.get("userId");
        User newOwner = userRepository.findById(newOwnerId)
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        // 新負責人必須與看板同科，避免跨科指派負責人造成部門隔離被繞過
        if (board.getDepartment() == null || newOwner.getDepartment() == null
            || !board.getDepartment().getId().equals(newOwner.getDepartment().getId())) {
            throw new IllegalArgumentException("負責人必須為本科帳號");
        }
        boardService.changeOwner(id, newOwnerId);
        return ApiResponse.ok(null);
    }
}
