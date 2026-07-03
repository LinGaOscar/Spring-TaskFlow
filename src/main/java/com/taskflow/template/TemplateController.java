package com.taskflow.template;

import com.taskflow.common.ApiResponse;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final UserRepository userRepository;

    private User currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName()).orElseThrow();
    }

    // 頁面殼：資料由前端 API 非同步載入（與 BoardController 看板列表頁同模式）
    @GetMapping("/templates")
    public String page() {
        return "template/list";
    }

    // JSON API 統一以 ApiResponse 包裝，維持與 BoardController 一致的前端解析方式
    @GetMapping("/api/templates")
    @ResponseBody
    public ApiResponse<List<TemplateDto.Response>> list(Principal principal) {
        return ApiResponse.ok(
            templateService.listVisible(currentUser(principal)).stream()
                .map(TemplateDto.Response::from).toList());
    }

    @PostMapping("/api/templates")
    @ResponseBody
    public ApiResponse<TemplateDto.Response> create(@Valid @RequestBody TemplateDto.SaveRequest req,
                                       Principal principal) {
        return ApiResponse.ok(TemplateDto.Response.from(
            templateService.create(req, currentUser(principal))));
    }

    @PutMapping("/api/templates/{id}")
    @ResponseBody
    public ApiResponse<TemplateDto.Response> update(@PathVariable Long id,
                                       @Valid @RequestBody TemplateDto.SaveRequest req,
                                       Principal principal) {
        return ApiResponse.ok(TemplateDto.Response.from(
            templateService.update(id, req, currentUser(principal))));
    }

    @DeleteMapping("/api/templates/{id}")
    @ResponseBody
    public ApiResponse<Void> delete(@PathVariable Long id, Principal principal) {
        templateService.delete(id, currentUser(principal));
        return ApiResponse.ok(null);
    }
}
