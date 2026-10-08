# 0.6.1 验证记录

本轮实现见 [登录与下载修复](../../../0.6.1登录与下载修复.md)。本目录不保存令牌、设备授权码、Client Secret 或签名材料。

- `unit-summary.json`：53 项单元测试，0 失败 / 错误。
- `device-new-features.txt`：9 项新功能设备测试集中通过；协议使用确定性服务，文件使用真实系统下载服务和不同 UID 的接收应用。
- `device-configured-full.txt`：配置正式 Client ID 后集中运行 38 项，36 项通过，两项触摸滚动失败。
- `device-scroll-retest.txt`：同两项拆分运行通过。保留原断言，没有修改阅读器实现；原生输入增加事件诊断，失败时记录触摸是否到达原生视图。
- `device-header-retest.txt`：首轮曾在 Compose Activity 销毁时出现 SlotWriter 异常，此用例单独复测通过。
- `official-oauth-start.json`：真实 GitHub 服务发起 Device Flow 成功，仅保存公开配置和协议参数，不保存授权码或令牌。
- `r8-unconfigured-baseline.txt`：配置 Client ID 之前，真实 R8 包完成 GitHub 浏览、离线保存、实际断网阅读和删除。正式配置包另做最终验证，不能以该日志替代。

这是 API 35 模拟器验证，不代表一加 Ace 5 / ColorOS 16 的真机验收。集中测试与拆分复测分别呈现，不将重试计为新用例，不声称集中运行零失败。
