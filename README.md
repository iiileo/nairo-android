# Nairo App / 奈洛

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](./LICENSE)

> 公开源码仓。正式 APK 仍发布到 [`nairo-android` Releases](https://github.com/iiileo/nairo-android/releases)；请勿提交签名证书与 `keystore.properties`。

Android 收款监听端：读取微信 / 支付宝系统收款通知，解析金额后签名上报 [Nairo API](https://github.com/iiileo/nairo-api)，并维持心跳与本地时间线。

正式安装包：[Releases](https://github.com/iiileo/nairo-android/releases)

> **仅用于本人商户设备。** 须用户主动授予通知使用权。禁止用于未经授权监控他人通知。  
> 本方案依赖通知文案与进程保活，**不能**替代官方商户支付能力。

## 功能

- `NotificationListenerService` 采集通知；匹配规则从服务端 `GET /api/v1/public/app-match-rules` 拉取
- 扫码 / 粘贴导入 `AppBindConfig`（与 Console 绑定二维码一致）
- 解析到有效金额后签名 `POST /app/push/{callback_id}`（`idem_key` + 失败出队重试）
- 过滤营销文案、昨日汇总、金额 ≤ 0 等噪声
- 前台保活：优先 WebSocket 心跳，失败回落 HTTP；隐藏无声视频 + 常亮降低 OEM 杀后台概率
- Compose 时间线、绑定状态、识别规则查看、应用内检查更新（gh-proxy 加速可选）

包名：`com.nairo.app`（**请勿轻易修改**，否则无法覆盖升级、需重新授权与绑定）

## 使用步骤

1. 安装 [Release APK](https://github.com/iiileo/nairo-android/releases)
2. 打开「通知使用权」，启用奈洛
3. 忽略电池优化，开启前台保活（Android 13+ 允许通知权限）
4. 在 API Console 创建监控设备 → App 内扫码或粘贴配置
5. 按厂商要求允许自启动 / 后台运行（小米 / 华为 / OPPO / vivo 等）

验收：他机向本机个人收款码支付小额，App 时间线出现记录且服务端订单变为已支付。

## 与后端的关系

```text
微信 / 支付宝弹出收款通知
        │
        ▼
NotificationListenerService
        │  匹配规则 + 金额解析
        ▼
签名推送 ──► api_service /app/push
心跳     ──► /app/heartbeat 或 /app/heartbeat/ws
规则同步 ──► /api/v1/public/app-match-rules
```

机制说明：[docs/通知监听支付回调原理.md](docs/通知监听支付回调原理.md)

## 本地开发

需要 JDK 17 + Android SDK（compileSdk / target 见 `app/build.gradle.kts`）。

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Release 签名**禁止**回落 debug：

1. 本地放置 `nairo-release.jks` + `keystore.properties`（已 gitignore，参考 `keystore.properties.example`）
2. 或使用下方 CI Secrets 发版

## 发版（GitHub Actions）

不强制本机安装完整 SDK。推送 `v*` tag 即可构建：

```bash
# 先 bump app/build.gradle.kts 的 versionCode / versionName 并提交
git tag v1.4.2
git push origin v1.4.2
```

- Workflow：`.github/workflows/build_apk.yml`
- 产物：`nairo-app-<version>-release.apk` + `latest.json`
- 发布到公开仓 [`iiileo/nairo-android`](https://github.com/iiileo/nairo-android) 的 GitHub Releases
- App「设置 → 检查更新」读取该仓的 `latest.json`，校验 SHA-256 后调起安装

### Actions Secrets

| Secret | 说明 |
|--------|------|
| `NAIRO_KEYSTORE_BASE64` | `base64 -i nairo-release.jks \| tr -d '\n'` |
| `NAIRO_STORE_PASSWORD` | 密钥库口令 |
| `NAIRO_KEY_ALIAS` | 如 `nairo` |
| `NAIRO_KEY_PASSWORD` | 密钥口令 |
| `RELEASE_REPO_TOKEN` | 对 `iiileo/nairo-android` 有写权限的 token（发布 Release 与 `latest.json`） |

Fork 时请：

1. 换成自己的签名证书与 Secrets
2. 修改发布仓库名（Workflow 里的 `RELEASE_REPO`，以及 `UpdateSources.REPO`）
3. 按需修改应用显示名；**包名变更会导致无法覆盖安装**

## 目录结构

```text
nairo_app/
├── app/src/main/java/com/nairo/app/
│   ├── notify/          # 通知监听
│   ├── match/           # 匹配规则
│   ├── sync/            # 推送队列 / 心跳调度
│   ├── bind/            # 扫码绑定
│   ├── update/          # 应用内更新
│   └── ui/              # Compose 界面
├── docs/                # 原理说明
└── .github/workflows/   # 发版 CI
```

## 相关仓库

| 仓库 | 说明 |
|------|------|
| [nairo-api](https://github.com/iiileo/nairo-api) | 后端 + Console / Admin / 收银台 |
| [nairo-android](https://github.com/iiileo/nairo-android) | 公开源码 + APK Releases / `latest.json` |

## 同步到公开仓

私有仓开发完成后，可用无历史方式覆盖同步到 `public` remote（`iiileo/nairo-android`）：

```bash
./scripts/push-public.sh
```

## License

[MIT](./LICENSE)

安全说明见 [SECURITY.md](./SECURITY.md)。
