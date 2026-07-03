package com.taskflow.template;

import com.taskflow.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;

    // 系統模板全員可見；自訂模板以科為共用單位，同科成員共享
    @Transactional(readOnly = true)
    public List<TaskTemplate> listVisible(User user) {
        Long sectionId = user.getDepartment() != null ? user.getDepartment().getId() : null;
        return templateRepository.findBySystemTrueOrSectionIdOrderByIdAsc(sectionId);
    }

    @Transactional
    public TaskTemplate create(TemplateDto.SaveRequest req, User caller) {
        checkManageRole(caller);
        TaskTemplate t = new TaskTemplate();
        apply(t, req);
        t.setSection(caller.getDepartment());
        return templateRepository.save(t);
    }

    @Transactional
    public TaskTemplate update(Long id, TemplateDto.SaveRequest req, User caller) {
        TaskTemplate t = getEditable(id, caller);
        apply(t, req);
        return templateRepository.save(t);
    }

    @Transactional
    public void delete(Long id, User caller) {
        templateRepository.delete(getEditable(id, caller));
    }

    // 模板管理限科長/Leader：Member 只消費模板，不維護
    private void checkManageRole(User caller) {
        if (caller.getRole() != User.Role.SECTION_CHIEF
                && caller.getRole() != User.Role.PROJECT_LEADER) {
            throw new SecurityException("僅科長或 Leader 可管理模板");
        }
    }

    // 系統模板不可改刪；自訂模板僅限同科的科長/Leader
    private TaskTemplate getEditable(Long id, User caller) {
        checkManageRole(caller);
        TaskTemplate t = templateRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("模板不存在"));
        if (t.isSystem()) throw new SecurityException("系統模板不可修改或刪除");
        if (t.getSection() == null || caller.getDepartment() == null
                || !t.getSection().getId().equals(caller.getDepartment().getId())) {
            throw new SecurityException("僅可管理本科模板");
        }
        return t;
    }

    private void apply(TaskTemplate t, TemplateDto.SaveRequest req) {
        t.setName(req.getName());
        t.setTitle(req.getTitle());
        t.setDescription(req.getDescription());
        t.setPriority(req.getPriority());
    }
}
