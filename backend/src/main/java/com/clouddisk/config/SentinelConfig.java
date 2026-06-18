package com.clouddisk.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "clouddisk.sentinel.enabled", havingValue = "true", matchIfMissing = true)
public class SentinelConfig {

    private final CloudDiskProperties properties;

    @PostConstruct
    public void initRules() {
        if (properties.getSentinel().getDashboard() != null) {
            System.setProperty("csp.sentinel.dashboard.server", properties.getSentinel().getDashboard());
            System.setProperty("csp.sentinel.api.port", "8719");
        }
        int uploadQps = properties.getSentinel().getUploadQps();
        int loginQps = properties.getSentinel().getLoginQps();
        int registerQps = properties.getSentinel().getRegisterQps();
        FlowRule uploadRule = new FlowRule("upload_api")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(uploadQps);
        FlowRule simpleRule = new FlowRule("simple_upload")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(uploadQps);
        FlowRule loginRule = new FlowRule("auth_login")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(loginQps);
        FlowRule registerRule = new FlowRule("auth_register")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(registerQps);
        FlowRuleManager.loadRules(List.of(uploadRule, simpleRule, loginRule, registerRule));
        log.info("Sentinel 限流规则已加载 upload QPS={}, login QPS={}, register QPS={}",
                uploadQps, loginQps, registerQps);
    }
}
