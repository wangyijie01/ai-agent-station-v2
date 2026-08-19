package cn.bugstack.ai.domain.ops.service;

import cn.bugstack.ai.domain.ops.model.JavaServiceTarget;
import cn.bugstack.ai.domain.ops.model.ServiceProbeResult;

/** Java 服务主动探测端口，便于替换为 HTTP、Kubernetes 或注册中心实现。 */
@FunctionalInterface
public interface JavaServiceProbe {

    ServiceProbeResult probe(JavaServiceTarget target);
}
