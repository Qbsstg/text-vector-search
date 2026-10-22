# 任务空间

本轮：C0初始化。主控负责契约、集成和真实验证；PM负责计划与追踪，不替代主控Owner。

角色配置和可复用提示词见`roles/`，注册与工作区见`agent-registry.md`，任务状态见`backlog.md`，角色交接见`handoffs/`。本工具五角色为内部子agent，不是五个独立侧栏聊天；并发上限为主控加三个子agent。

状态：TODO / READY / IN_PROGRESS / REVIEW / DONE / BLOCKED。仅有实际产物与验收证据才可DONE；外部依赖未具备不应把整个本地项目标BLOCKED。

交付格式：任务ID、分支/基线SHA、修改文件、契约/依赖变化、命令与真实结果、未执行项、风险/复现、建议审查位置、提交SHA。阶段命名C0/B1/B2/B3/B4/FE/DEP/QA；先完成本轮C0，不自行扩展到全量业务实现。

