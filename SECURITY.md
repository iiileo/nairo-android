# Security Policy

## 报告漏洞

请通过 GitHub Issues 或 Maintainer 私信报告。
请勿公开粘贴：绑定二维码完整 JSON、`secret`、设备 ID、真实收款通知原文中的隐私字段。

## 构建与签名

- `*.jks` / `*.keystore` / `keystore.properties` 已 gitignore，**禁止提交**
- Release 构建依赖 GitHub Actions Secrets（密钥库与口令），勿把 Secret 写进 Workflow 明文
- Fork 后请自行更换签名证书与 [UpdateSources](app/src/main/java/com/nairo/app/update/UpdateSources.kt) 中的发布仓库

## 使用边界

- 仅在**本人商户手机**上开启通知使用权
- 通知内容可能含金额、备注等敏感信息，注意锁屏与备份
- 匹配规则与金额解析依赖通知文案，存在误判 / 漏判风险，需人工对账
