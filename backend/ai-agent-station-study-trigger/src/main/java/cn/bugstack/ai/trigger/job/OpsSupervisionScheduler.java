package cn.bugstack.ai.trigger.job;

import cn.bugstack.ai.domain.ops.service.OpsSupervisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期触发到期 Java 服务的健康探测。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.ops.supervision", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpsSupervisionScheduler {

    private final OpsSupervisionService supervisionService;

    @Scheduled(fixedDelayString = "${ai.ops.supervision.scheduler-delay-ms:10000}")
    public void probeDueTargets() {
        int count = supervisionService.checkDueTargets().size();
        if (count > 0) {
            log.debug("完成到期 Java 服务探测 count={}", count);
        }
    }
}
