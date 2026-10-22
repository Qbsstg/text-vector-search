# QA-B1 交接

- 任务：B1独立验收；工作树`.worktrees/qa`，分支`agent/qa`。
- 设计基线：224bc791b114b1e3a788585dad0ee6baa8c86ad2；代码执行基线：a33f1e4f8c633640e45a4ca4abe214d5fa68de8f。
- 修改：acceptance/B1AlgorithmIT.java、acceptance/B1VectorProbe.java、docs/testing/b1-test-plan.md、docs/testing/generate_b1_goldens.py、本交接文档。
- 契约/依赖/业务/DDL/pom/基础配置：全部未修改；无新依赖。
- 独立性：Python标准库oracle不读取生产代码；明确canonical + Counter特征计数 + hashlib + struct产生固定黄金。U+FF0C人工规范化预期在执行Java前经独立Unicode核对修正为ASCII逗号，说明与可重跑脚本均保留。
- 真实执行：Python脚本；JDK17目标`verify -Dit.test=B1AlgorithmIT`（首次通过，29 unit + 16 IT）；随后完整`clean verify`（29 unit + 22 IT），均失败/错误/跳过0。
- 最终完成时间：2026-10-22 22:22:04 +08:00，BUILD SUCCESS。
- 本机下载适配：使用临时Maven settings/cache路径；不是交付要求。真实完整命令另向主控回报；完整HTTP回归经授权允许本机监听。
- 未发现阻塞缺陷；不依赖测试失败后读取生产输出改golden。
- 已验证重点：8黄金/8独立JVM、共享实例7线程barrier、Unicode与非法UTF16/规范化扩张、codec有限极值和−0原始位、非单位及极值余弦、随机全排序与同分ID方向、负分/无阈值、生产配置唯一bean。
- 限制：本机Java17验证；没有跨其他JDK/平台、性能结论。B2+业务、run_token、未知提交、强杀恢复、UI及服务器均未执行。
- 主控审查建议：固定SHA的独立来源、参考全排序比较器、子进程只加载测试/生产类目录、有限等待清理及不预抢占中断H2的Spring装配检查。
- 提交SHA：以交付消息中的真实commit为准；合入后主控对集成SHA完整复跑。
