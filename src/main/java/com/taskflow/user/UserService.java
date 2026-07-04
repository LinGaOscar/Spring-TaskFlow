package com.taskflow.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // 成員管理限科內帳號：只回傳指定科（同 department）且啟用中的帳號，避免跨科帳號
    // 被誤加入該科的看板成員（部門隔離旁路）；停用帳號亦排除，避免離職人員出現在成員選單
    public List<User> listUsers(Long departmentId) {
        return userRepository.findByDepartmentIdAndEnabledTrue(departmentId);
    }

    // 部長專用：回傳本部＋所有子科的啟用帳號——部長自建看板掛部層級，
    // 成員來自下屬科，若只回部層級帳號會使部長的成員管理形同空清單
    public List<User> listUsersForDivision(Long divisionId, List<Long> sectionIds) {
        List<User> result = new java.util.ArrayList<>(
            userRepository.findByDepartmentIdAndEnabledTrue(divisionId));
        for (Long sectionId : sectionIds) {
            result.addAll(userRepository.findByDepartmentIdAndEnabledTrue(sectionId));
        }
        return result;
    }
}
