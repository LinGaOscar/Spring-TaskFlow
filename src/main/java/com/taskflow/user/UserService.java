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
}
