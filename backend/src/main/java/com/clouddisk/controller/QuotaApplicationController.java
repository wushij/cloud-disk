package com.clouddisk.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clouddisk.entity.QuotaApplication;
import com.clouddisk.service.QuotaApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quota-applications")
@RequiredArgsConstructor
public class QuotaApplicationController {

    private final QuotaApplicationService quotaApplicationService;

    /**
     * 提交扩容申请
     */
    @PostMapping
    public Map<String, Object> apply(@RequestBody Map<String, Object> body) {
        Object quotaObj = body.get("applyQuota");
        if (quotaObj == null) {
            throw new com.clouddisk.common.BusinessException("缺少 applyQuota 参数");
        }
        Long applyQuota = Long.valueOf(String.valueOf(quotaObj));
        String reason = (String) body.get("reason");
        
        QuotaApplication qa = quotaApplicationService.apply(applyQuota, reason);
        return Map.of("message", "申请已提交", "id", qa.getId());
    }

    /**
     * 获取用户本人的申请历史
     */
    @GetMapping("/my")
    public List<QuotaApplication> listUserHistory() {
        return quotaApplicationService.listUserHistory();
    }

    /**
     * 管理员：获取所有申请记录
     */
    @GetMapping("/admin")
    public Page<QuotaApplication> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return quotaApplicationService.listAllApplications(page, size);
    }

    /**
     * 管理员：通过扩容申请
     */
    @PostMapping("/admin/{id}/approve")
    public Map<String, String> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String opinion = body != null ? body.get("opinion") : "";
        quotaApplicationService.approve(id, opinion);
        return Map.of("message", "扩容申请已通过");
    }

    /**
     * 管理员：拒绝扩容申请
     */
    @PostMapping("/admin/{id}/reject")
    public Map<String, String> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String opinion = body != null ? body.get("opinion") : "";
        quotaApplicationService.reject(id, opinion);
        return Map.of("message", "扩容申请已拒绝");
    }
}
