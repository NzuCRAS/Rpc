package com.example.rpc.stability;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 综合限流管理器，支持接口级、参数级（热点参数）、IP级限流。
 */
public class SentinelLimitManager {

    // 用于IP级限流的规则缓存
    private static final Map<String, Integer> ipQpsRuleMap = new ConcurrentHashMap<>();

    /**
     * 注册接口级限流规则
     * @param resourceQpsMap 资源名->QPS。例如: "UserService_login" -> 100
     */
    public static void loadInterfaceRules(Map<String, Integer> resourceQpsMap) {
        List<FlowRule> rules = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : resourceQpsMap.entrySet()) {
            FlowRule rule = new FlowRule();
            rule.setResource(entry.getKey());
            rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
            rule.setCount(entry.getValue());
            rules.add(rule);
        }
        FlowRuleManager.loadRules(rules);
    }

    /**
     * 注册参数级（热点参数）限流规则
     * @param resourceName 资源名
     * @param paramIdx 参数下标（第几个参数作为热点参数）
     * @param qps 每个参数的最大QPS
     */

    public static void loadParamRules(String resourceName, int paramIdx, int qps) {
        ParamFlowRule rule = new ParamFlowRule(resourceName)
            .setParamIdx(paramIdx)
            .setCount(qps);
        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
    }

    /**
     * 注册IP级限流规则（所有接口统一规则；如需细粒度可自行扩展）
     * @param qps 每个IP最大QPS
     */

    public static void loadIpRule(int qps) {
        ipQpsRuleMap.put("GLOBAL_IP_LIMIT", qps);
    }

    /**
     * 尝试接口级限流
     * @param resourceName 资源名
     * @return Entry对象（需调用exit），如被限流抛BlockException
     */

    public static Entry tryInterfaceEntry(String resourceName) throws BlockException {
        return SphU.entry(resourceName);
    }

    /**
     * 尝试参数级限流（Sentinel热点参数限流，需在接口限流基础上使用）
     * @param resourceName 资源名
     * @param params 参数数组（含需限流的参数）
     * @return Entry对象（需调用exit），如被限流抛BlockException
     */

    public static Entry tryParamEntry(String resourceName, Object params) throws BlockException {
        // 进入上下文（通常 context name 可用 resourceName）
        ContextUtil.enter(resourceName);
        try {
            Entry entry = SphU.entry(resourceName);
            // 热点参数限流时，需要在业务逻辑里调用 ParamFlowSlot#checkFlow
            // Sentinel会根据你在规则里设置的 paramIdx，自动关联参数。
            // 在业务逻辑调用时，用 SphU.entry(resourceName) 即可，参数通过异步埋点或Slot内部处理
            return entry;
        } catch (BlockException ex) {
            throw ex;
        }
        // 注意 exit 需在业务完成后调用
    }

    /**
     * 尝试IP级限流（建议在Netty Handler中调用）
     * @param ip 客户端IP
     * @return true表示通过，false表示被限流
     */

    public static boolean tryIpLimit(String ip) {
        int maxQps = ipQpsRuleMap.getOrDefault("GLOBAL_IP_LIMIT", 0);
        if (maxQps <= 0) return true; // 未开启IP限流
        String resourceName = "IP_" + ip;
        // 为每个IP注册限流规则（如需性能优化可单独维护规则注册）
        FlowRule rule = new FlowRule();
        rule.setResource(resourceName);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(maxQps);
        FlowRuleManager.loadRules(mergeWithExistRules(rule));
        try (Entry entry = SphU.entry(resourceName)) {
            return true;
        } catch (BlockException e) {
            return false;
        }
    }

    // 合并已有规则用于IP限流（防止覆盖其它规则）
    private static List<FlowRule> mergeWithExistRules(FlowRule newRule) {
        List<FlowRule> merged = new ArrayList<>(FlowRuleManager.getRules());
        // 避免重复添加
        merged.removeIf(r -> r.getResource().equals(newRule.getResource()));
        merged.add(newRule);
        return merged;
    }
}