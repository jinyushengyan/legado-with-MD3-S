# 私人魔改版接手说明（3.26.25-jinyu）

本包基于 [HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3) 3.26.25，
只针对"纯小说阅读"场景做私有改动。本文件记录**与原版不同的地方**，读完这一篇即可接手。

## 构建

环境：JDK 21、Android SDK 37（compileSdk 37 / compileSdkMinor 1 / minSdk 26）。

上游引入了 flavor 拆分（2026-11-10 同步），构建任务名需使用完整 flavor 变体：
- `appCore` = 核心小说版（无本地漫画推理引擎，自用主变体）
- `appComic` = 漫画增强版（内置 ML Kit / ONNX Runtime，多 ~50MB）

```powershell
# Debug（应用名「Legado·改」，包名 io.legato.kazusa.debug）
.\gradlew.bat :app:assembleAppCoreDebug

# Release（应用名「阅读·S」，版本 3.26.25-jinyu，同包名，R8 压缩）
.\gradlew.bat :app:assembleAppCoreRelease

# 重新生成 Room schema（改了 AppDatabase 版本/实体后必须跑）
.\gradlew.bat :app:kspAppCoreDebugKotlin
```

`gradle.properties` 已默认关闭 configuration cache 和 KSP 增量（Windows 跨盘符会导致
R.jar 文件锁、KSP 中间产物删除失败）。**不需要再手动加** `--no-configuration-cache`
或 `-Pksp.incremental=false`，直接跑上面的命令即可。

产物路径（ABI splits 开启，默认 3 份）：
- `app/build/outputs/apk/appCore/debug/app-app-core-arm64-v8a-debug.apk`
- `app/build/outputs/apk/appCore/debug/app-app-core-armeabi-v7a-debug.apk`
- `app/build/outputs/apk/appCore/debug/app-app-core-universal-debug.apk`
- release 路径相同，替换 `debug` → `release`

### 版本号规则

- `versionName` 在魔改分支上固定为「上游版本 + `-jinyu`」，例如上游 3.26.25 → `3.26.25-jinyu`。
  release buildType 自动追加 `-jinyu`（`versionNameSuffix`），无需手动改 `version.properties`。
- 同版本内自己又改了东西：追加 `-beta.N`。上游发 beta 时写成 `<上游版本>-beta.N-jinyu`。
- `versionCode` 不手工维护，沿用 `app/build.gradle.kts` 里的默认值（CI 有 `COMMIT_NUMBER`
  时自动偏移）。**自用发布如果覆盖安装被拒，先把默认值 +1**；签名变化时仍需卸载重装。

首次构建需要联网拉依赖，`gradle/wrapper/` 与 Cronet jar（`app/cronetlib/`）已随包提供。

## 与原版的差异

### 1. 共存与产物标识（`app/build.gradle.kts`）

- debug 与 release 都带 `applicationIdSuffix = ".debug"`，包名恒为 `io.legato.kazusa.debug`，
  不覆盖正式版 `io.legato.kazusa`，两者可同机安装。
- debug 应用名 `Legado·改`，release 应用名 `阅读·S`，版本名后缀 `-jinyu`。
- 无 release keystore 时用 Android debug keystore 签名，方便直接装机自用（**不可上架**）。
- release 开启 R8 + 资源压缩，APK 约 20MB（debug 约 73MB）。
- 图标：`app/src/debug/res/values/colors.xml` 覆盖了 debug 变体的图标底色，
  release 不加载该源集，图标为原版深蓝灰。
- `AndroidManifest.xml` 的 FileProvider authority 由 `${applicationId}.fileProvider`
  改为全小写 `${applicationId}.fileprovider`，与代码里拼的 authority 保持一致（部分 ROM
  对大小写敏感会报「找不到 FileProvider」）。

### 2. 书签角标位置

- `feature/reader/core/model/ReaderBookmarkBadge.kt`
- `feature/reader/legacy/LegacyReaderPageDecorationFactory.kt`

角标 top 贴页眉下沿（`topPx = headerBottomPx`）。**该修复已合并回上游（PR #2573）**，
同步上游后这部分以上游实现为准，无需额外保留我们的 patch。

### 3. 备份/恢复忽略项（`help/storage/BackupConfig.kt`）

- 勾选"忽略主题配置"时，额外忽略：主题色、纯黑、强调色、字体、字体缩放、卡片圆角/边框、
  分隔线、容器背景图、自定义标签颜色、导航栏自定义图标、浮动底栏与液态玻璃
  （`useFloatingBottomBar`、`useFloatingBottomBarLiquidGlass`）等（`themePrefKeys`）。
  这些键上游原本没有登记，**已合并回上游（PR #2574）**；同步上游后若上游列表更全，以新列表为准。
- `defaultCover` / `defaultCoverDark` 永不恢复覆盖（在 `alwaysIgnoredPreferenceKeys`）——
  恢复备份永远保留本机封面设置。
- 新增数据库忽略键 `bookRating`，恢复时可选择跳过评分表。

### 4. 自定义维度评分（全新功能）

只服务小说阅读，评分**独立于 `books` 表**，以规范化"书名+作者"为主键（复用
`ReadRecordIdentity.key`），移出书架、换源都不丢分。

| 位置 | 说明 |
|---|---|
| `data/entities/BookRating.kt` | Room 实体，表 `book_ratings` |
| `data/dao/BookRatingDao.kt` | DAO |
| `data/AppDatabase.kt` | version 135，`AutoMigration(133→134)` 加评分表 + `AutoMigration(134→135)` 承接上游列变更 |
| `domain/model/BookRating.kt` | 维度/分级配置、总分公式、规范化 key |
| `domain/gateway/BookRatingGateway.kt` | 领域接口 |
| `data/repository/BookRatingRepository.kt` | 配置存 `PreferKey.bookRatingConfig`，CSV 导出 |
| `di/appModule.kt` | Koin 显式绑定 |
| `feature/bookrating/*` | 评分弹层、总览页、维度/分级设置页 |
| `ui/book/info/*` | 详情页评分卡片与入口 |

规则：
- 维度可增删改，每个维度带"细则参考"文案。
- 内容分级档位（名称与档数均可自定义）各有一套维度权重。
- 每维 0–100 分；总分 = `Σ(分/100×权) / Σ权 × 100`，只计**已打分且权>0**的维度，无分显示"未评分"。
- 支持"从阅读记录导入"补录历史，支持手动新建。
- 总览页支持导出 CSV（UTF-8 BOM，Excel/WPS 直接打开；表头按当前维度动态生成）。

### 5. 评分页排版

`feature/bookrating/BookRatingSheet.kt` 每个维度块：

```
短评键（次要色）    维度名（行正中，titleMedium）    [分数框 48-56dp]
────────────── 滑块 ──────────────
短评输入框（默认折叠，已有内容时展开）
细则参考（默认完整显示，浅色圆角容器）
```

分数输入框用 `BasicTextField` 自绘边框，避免 `OutlinedTextField` 默认 label padding 挤压文字。

### 6. 导出方式

评分总览页右上角导出走 **SAF 另存为**（`ActivityResultContracts.CreateDocument`），
不是分享面板，也不需要 FileProvider。文件名 `Legado_ratings_yyyyMMdd.csv`。

## 分支与同步上游

- 魔改集中在 `jinyu` 分支，基线是上游 3.26.19 的发布提交 `c6798651`，
  当前已同步到上游 main tip（2026-11-10，`4d2cc8e5`）。`main` 保持与上游一致。
- 同步上游步骤（Windows + ghproxy 网络）：
  1. 浅克隆上游到临时目录：`git clone --depth 1 --no-checkout https://ghproxy.net/https://github.com/HapeLee/legado-with-MD3.git upstream_main`
  2. 在临时目录加深祖先链：`git fetch --shallow-since=2026-10-22 --no-tags origin main`
     （必须覆盖我们的基线 `c6798651`，否则 merge-base 为空，后续 merge 会变成乱冲突）
  3. 拉到工作仓库：`git fetch --update-shallow --depth=1000 --no-tags ../upstream_main main:refs/remotes/upstream/main`
  4. 合并：`git merge --no-ff upstream/main`
  5. 解决冲突后 `git add`，然后 `git commit`
- 冲突高发文件：`data/AppDatabase.kt`（数据库版本号与迁移列表长行追加）、
  `help/storage/BackupConfig.kt`、`res/values*/strings.xml`、`ui/main/MainNavGraph.kt`、
  `ui/book/info/BookInfoScreen.kt`、`app/build.gradle.kts`、
  `ui/config/ConfigNavScreen.kt`（导航回调参数列表）。
- 数据库版本冲突处置：双方若都占了同一个 version 号（如本次双方都用 134），
  把 `version` 改为「当前最大 + 1」，并追加一条 `AutoMigration(旧版→新版)`。
  然后重新跑 `.\gradlew.bat :app:kspAppCoreDebugKotlin` 生成新的 `app/schemas/.../<新版本>.json`。
  Room 的 AutoMigration 会对比新旧 schema 自动生成 ALTER TABLE SQL。
- 已合并回上游的两处（书签角标、themePrefKeys）在同步后应以上游实现为准。

## 已知环境问题（非代码缺陷）

- **Robolectric 单测**：如果 `C:\Users\<user>\.robolectric-download-lock` 无法创建，
  所有依赖 Robolectric 的测试会报 `IllegalStateException: Couldn't create lock file`。
  这是本机权限/文件锁问题，与代码无关；用管理员终端或清理该文件后可恢复。
- **vivo 等国产 ROM 的 adb 安装拦截**：报 `INSTALL_FAILED_ABORTED: User rejected permissions`。
  需要开启"USB 调试（安全设置）"和"允许通过 USB 安装应用"，或把 APK 推到
  `/sdcard/Download/` 后手动点击安装。
- **Gradle daemon 兼容**：上游升级后旧 daemon 会报 "incompatible and N stopped"，
  下次构建会自动启新的，忽略即可。

## 架构约束

沿用原仓库 `AGENTS.md`：新 Compose 页面走 MVI（Contract/ViewModel/Screen），
UI 与 ViewModel 不直连 DAO，统一经 Gateway；Koin 显式绑定接口；
新 Feature 放在 `io.legado.app.feature.<name>` 包。
