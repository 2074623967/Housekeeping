# Releases

本目录用于存放基于 `master` 切出的正式发布文档。

## 目录规则

1. 每个版本一份文档，命名建议：
   - `v1.0.0-发布说明.md`
   - `v1.1.0-发布说明.md`
2. 只有当代码已经进入 `master`，并确认达到发布门槛后，才允许在本目录新增正式版本文档。

## 单个版本文档最少内容

1. 版本号与发布日期
2. 发布范围
3. 功能清单
4. 数据库变更
5. 接口变更
6. 前端变更
7. 测试结论
8. 已知风险
9. 回滚说明
10. 发布后验证项
# 版本交付包

每个正式版本除根目录下的发布说明外，还必须包含一套专项变更文档：

- `docs/releases/<version>/01-后端变更说明.md`
- `docs/releases/<version>/02-前端变更说明.md`
- `docs/releases/<version>/03-数据库与接口变更说明.md`
- `docs/releases/<version>/04-运维操作手册.md`
- `docs/releases/<version>/05-测试与验收报告.md`
- `docs/releases/<version>/06-回滚与已知问题.md`

详细要求见 [`版本变更包规范.md`](./版本变更包规范.md)，模板位于 [`templates/v1.1.0`](./templates/v1.1.0)。
