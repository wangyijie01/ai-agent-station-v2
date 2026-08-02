package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 路由预览请求。
 *
 * <p>该接口只返回候选 Skill 及命中原因，不会创建运行或调用外部工具。</p>
 *
 * @author wangyijie01
 * @since 2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSkillRouteRequestDTO {

    /** 用于触发词和场景匹配的用户消息。 */
    private String message;

    /** 用户显式指定的 Skill ID；合法 ID 会优先进入候选集合。 */
    @Builder.Default
    private List<String> requestedSkillIds = new ArrayList<>();

    /** 最多返回的候选数量；未传时由接口使用默认值。 */
    private Integer limit;
}
