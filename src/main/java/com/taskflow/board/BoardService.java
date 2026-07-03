package com.taskflow.board;

import com.taskflow.department.Department;
import com.taskflow.department.DepartmentRepository;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final BoardMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    // 建立者自動被加為成員，省去建立後還要手動加入自己的步驟
    @Transactional
    public Board createBoard(BoardDto.CreateRequest req, Long creatorId) {
        User creator = userRepository.findById(creatorId)
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        Board p = new Board();
        p.setName(req.getName());
        p.setOwner(creator);
        p.setCreatedBy(creator);
        if (creator.getDepartment() != null) {
            p.setDepartment(creator.getDepartment());
        }
        Board saved = boardRepository.save(p);
        addMember(saved.getId(), creatorId, creatorId);
        return saved;
    }

    // 各角色可見範圍：部長看部+全部子科；科長/Leader 看本科；Member 只看被加入的
    @Transactional(readOnly = true)
    public List<Board> listForUser(User user, boolean archived) {
        return switch (user.getRole()) {
            case DIRECTOR -> {
                // 部長可查看直屬部的看板，以及所有子科的看板
                List<Board> own = boardRepository.findByDepartmentIdAndArchived(
                    user.getDepartment().getId(), archived);
                List<Long> sectionIds = departmentRepository.findByParentId(user.getDepartment().getId())
                    .stream().map(Department::getId).toList();
                List<Board> sectionBoards = sectionIds.isEmpty()
                    ? List.of() : boardRepository.findBySectionIds(sectionIds, archived);
                yield java.util.stream.Stream.concat(own.stream(), sectionBoards.stream()).toList();
            }
            case SECTION_CHIEF, PROJECT_LEADER ->
                boardRepository.findByDepartmentIdAndArchived(user.getDepartment().getId(), archived);
            case PROJECT_MEMBER ->
                // Member 只能看被明確加入的看板，無法瀏覽本科其他看板
                boardRepository.findByMemberOrOwner(user.getId(), archived);
        };
    }

    // 歷史查詢全員可用，但可見範圍仍依部門隔離（沿用 listForUser 的角色規則）
    @Transactional(readOnly = true)
    public List<Board> searchHistory(User user, String keyword, LocalDate from, LocalDate to) {
        return listForUser(user, true).stream()
            .filter(b -> keyword == null || keyword.isBlank()
                || b.getName().toLowerCase().contains(keyword.toLowerCase()))
            .filter(b -> from == null || (b.getArchivedAt() != null
                && !b.getArchivedAt().toLocalDate().isBefore(from)))
            .filter(b -> to == null || (b.getArchivedAt() != null
                && !b.getArchivedAt().toLocalDate().isAfter(to)))
            .toList();
    }

    // 歸檔後看板進入歷史唯讀狀態，資料不刪除
    @Transactional
    public void archiveBoard(Long boardId, User caller) {
        Board p = getById(boardId);
        checkArchivePermission(p, caller);
        p.setArchived(true);
        p.setArchivedAt(java.time.LocalDateTime.now());
        boardRepository.save(p);
    }

    // 還原歸檔讓看板回到可編輯狀態
    @Transactional
    public void unarchiveBoard(Long boardId, User caller) {
        Board p = getById(boardId);
        checkArchivePermission(p, caller);
        p.setArchived(false);
        p.setArchivedAt(null);
        boardRepository.save(p);
    }

    // 歸檔/還原只允許看板負責人或同科科長，防止跨科或一般成員誤操作
    private void checkArchivePermission(Board p, User caller) {
        boolean isOwner = p.getOwner() != null && p.getOwner().getId().equals(caller.getId());
        boolean isChief = caller.getRole() == User.Role.SECTION_CHIEF
            && p.getDepartment() != null
            && p.getDepartment().getId().equals(caller.getDepartment().getId());
        if (!isOwner && !isChief) {
            throw new SecurityException("只有看板負責人或科長可歸檔/還原看板");
        }
    }

    // 冪等操作：已存在的成員不重複新增，避免 unique constraint 衝突
    @Transactional
    public void addMember(Long boardId, Long userId, Long assignedById) {
        if (memberRepository.existsByIdBoardIdAndIdUserId(boardId, userId)) return;
        BoardMember m = new BoardMember();
        m.setId(new BoardMemberId(boardId, userId));
        User assignedBy = userRepository.findById(assignedById)
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        m.setAssignedBy(assignedBy);
        memberRepository.save(m);
    }

    // 踢出成員後立即失去 PROJECT_MEMBER 的 WBS 編輯權限
    @Transactional
    public void removeMember(Long boardId, Long userId) {
        memberRepository.deleteById(new BoardMemberId(boardId, userId));
    }

    // 變更負責人時，若新 owner 尚未加入成員清單，自動補加，確保其有編輯權限
    @Transactional
    public void changeOwner(Long boardId, Long newOwnerId) {
        Board p = boardRepository.findById(boardId)
            .orElseThrow(() -> new EntityNotFoundException("看板不存在"));
        User newOwner = userRepository.findById(newOwnerId)
            .orElseThrow(() -> new EntityNotFoundException("使用者不存在"));
        p.setOwner(newOwner);
        boardRepository.save(p);
        addMember(boardId, newOwnerId, newOwnerId);
    }

    // PROJECT_MEMBER 角色的寫入判斷依據
    public boolean isMember(Long boardId, Long userId) {
        return memberRepository.existsByIdBoardIdAndIdUserId(boardId, userId);
    }

    // 統一拋 EntityNotFoundException，避免各處 findById 遺漏 404 處理
    public Board getById(Long id) {
        return boardRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("看板不存在"));
    }

    // 系統最核心的安全閘：DIRECTOR 只能讀，不能改；歸檔看板對全員強制唯讀
    public boolean canWriteBoard(Long boardId, User user) {
        Board board = getById(boardId);
        // 歸檔看板全員唯讀
        if (board.isArchived()) return false;
        return switch (user.getRole()) {
            case DIRECTOR ->
                // 部長即使建立了看板也只能唯讀，不影響下屬科的編輯流程
                board.getOwner() != null && board.getOwner().getId().equals(user.getId());
            case SECTION_CHIEF ->
                board.getDepartment() != null
                && board.getDepartment().getId().equals(user.getDepartment().getId());
            case PROJECT_LEADER, PROJECT_MEMBER ->
                isMember(boardId, user.getId());
        };
    }

    // 部長透過 parent 關聯查子科（部門樹兩層：部 → 科），科長/Leader 只看本科，Member 僅看被加入的
    public boolean canReadBoard(Long boardId, User user) {
        Board board = getById(boardId);
        return switch (user.getRole()) {
            case DIRECTOR -> {
                if (board.getDepartment() == null) yield false;
                Department dept = board.getDepartment();
                // 直接屬於本部，或屬於本部的子科
                yield dept.getId().equals(user.getDepartment().getId())
                    || (dept.getParent() != null
                        && dept.getParent().getId().equals(user.getDepartment().getId()));
            }
            case SECTION_CHIEF, PROJECT_LEADER ->
                board.getDepartment() != null
                && board.getDepartment().getId().equals(user.getDepartment().getId());
            case PROJECT_MEMBER ->
                isMember(boardId, user.getId());
        };
    }
}
