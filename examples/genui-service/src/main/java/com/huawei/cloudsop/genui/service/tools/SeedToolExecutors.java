package com.huawei.cloudsop.genui.service.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 种子工具的 mock 执行器:让 demo 里 Query/Mutation 节点真的有数据可渲染。
 *
 * <p>这是「工具型扩展」闭环的最后一段——prompt 里注册的工具契约(seed/*.json)
 * 在这里有对应的运行时实现。公司内部落地时,把本类替换为真实工具通道
 * (RPC/HTTP 调下游服务)即可,REST 契约不变。
 */
@Component
public class SeedToolExecutors {

  /** 演示告警数据集,与 seed 的网络运维主题一致。 */
  private static final List<Map<String, Object>> DEMO_ALARMS =
      List.of(
          alarm("A-001", "critical", "Router-Core-01", "核心路由器 CPU 持续超过 95%", 1717200000000L),
          alarm("A-002", "critical", "Firewall-Edge-03", "边界防火墙会话表溢出", 1717203600000L),
          alarm("A-003", "major", "Switch-Access-02", "接入交换机端口 GE0/0/3 反复抖动", 1717207200000L),
          alarm("A-004", "major", "Router-Core-01", "BGP 邻居 10.0.0.2 震荡", 1717210800000L),
          alarm("A-005", "minor", "Switch-Access-05", "光模块接收功率偏低", 1717214400000L));

  private final Map<String, Function<Map<String, Object>, Map<String, Object>>> executors =
      new LinkedHashMap<>();

  public SeedToolExecutors() {
    executors.put("queryAlarms", SeedToolExecutors::queryAlarms);
    executors.put("acknowledgeAlarm", SeedToolExecutors::acknowledgeAlarm);
  }

  public Map<String, Object> execute(String toolName, Map<String, Object> args) {
    Function<Map<String, Object>, Map<String, Object>> executor = executors.get(toolName);
    if (executor == null) {
      throw new UnknownToolException(toolName);
    }
    return executor.apply(args == null ? Map.of() : args);
  }

  private static Map<String, Object> queryAlarms(Map<String, Object> args) {
    Object severity = args.get("severity");
    List<Map<String, Object>> matched = new ArrayList<>();
    for (Map<String, Object> alarm : DEMO_ALARMS) {
      if (severity == null || severity.equals(alarm.get("severity"))) {
        matched.add(alarm);
      }
    }
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("alarms", matched);
    result.put("total", matched.size());
    return result;
  }

  private static Map<String, Object> acknowledgeAlarm(Map<String, Object> args) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("alarmId", args.get("alarmId"));
    return result;
  }

  private static Map<String, Object> alarm(
      String id, String severity, String device, String title, long time) {
    LinkedHashMap<String, Object> alarm = new LinkedHashMap<>();
    alarm.put("id", id);
    alarm.put("severity", severity);
    alarm.put("device", device);
    alarm.put("title", title);
    alarm.put("time", time);
    return alarm;
  }
}
