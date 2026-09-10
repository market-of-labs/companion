# companion —— 私有市场同步器

路线 C 的**伴侣应用**。它**不改动 Obtainium 的任何一行代码**，只做三件事：

| # | 职责 | 实现位置 |
|---|------|----------|
| ① | **同步**：拉清单 → 剥壳 → 按 ABI 折叠 → 以 `obtainium://apps/<payload>` 投给 Obtainium | `MarketClient` / `ManifestParser` / `AbiFilter` / `DeepLinkBuilder` / `ObtainiumLauncher` |
| ② | **引导安装**：本机没有 Obtainium 时，从清单里下载并装上 | `DownloadInstaller` + `MarketViewModel.installObtainium()` |
| ③ | **自更新**：比对清单里的 `kind:"companion"` 条目，比自己新就下载安装 | `SelfUpdate` + `DownloadInstaller` |

规格见 `../../market-spec/`：`01-companion-app-spec.md`（行为）、`02-manifest-schema.md`（数据契约）。

---

## 单 APK、运行时自适应（手机 + 电视）

**同一份业务逻辑、同一份状态**（`MarketViewModel` + `UiState`），只有渲染层分叉：

| 设备 | 设计系统 | 入口 |
|------|----------|------|
| 手机 | `androidx.compose.material3`（浅色优先） | `ui/PhoneScreen.kt` |
| 电视 | `androidx.tv.material3`（深色、10 尺、D-pad 焦点态） | `ui/TvScreen.kt` |

判定在 `MarketViewModel.kt` 末尾的 `Context.isTelevision()`：先看 `UiModeManager.currentModeType`，
再退到 `FEATURE_LEANBACK`（有些盒子只报其中一个）。**判定只做一次**并缓存，不在重组里反复查系统服务。

两套界面说同一句话，靠的是 `ui/ScreenContent.kt`：它把状态压成一个**与设计系统无关**的
`ScreenContent`，两个界面各自只剩排版。文案漂移（手机写 A、电视写 B）因此不可能发生。

设置页（`ui/SettingsScreen.kt`）**只有 material3 一套**，手机与电视共用 —— 它是低频入口，
双份排版的漂移风险大于电视上那点焦点观感收益。这是有意的取舍，不是遗漏。

---

## 构建与运行

### 前置

- Android Studio（AGP 9 / Kotlin 2.4 需要较新的 IDE）
- JDK 17
- Android SDK：`compileSdk 37`

工具链：**AGP `9.3.2` / Kotlin `2.4.10` / Gradle `9.7.1`**。

**两处有意不对齐 Obtainium 工程**，都记在这里，免得下次看到时以为是手误：

**① AGP 版本更高（9.3.2 vs 9.0.1）。** 这不是追赶新版本，是被 Compose 逼的：
`compose-bom:2026.09.00` 解析出的 compose 1.12.x 在**自身元数据**里声明了最低 AGP 9.1.0，
AGP 9.0.1 会在 Sync 阶段硬性拒绝，报三条
`Dependency 'androidx.compose.foundation:foundation-android:1.12.1' requires Android Gradle plugin 9.1.0 or higher`。
降到旧 Compose 也能解，但那就放弃了「用最新 Compose」这个前提，所以选择升 AGP。

为什么是 9.3.2 而不是更新的 9.4.0：**9.3.2 本机 Gradle 缓存里已有，Sync 不必联网下载 AGP**。
两者都满足 compose 的最低要求，也都官方支持到 API 37 —— 后者正是
`compileSdk = 37` 不再需要 `android.suppressUnsupportedCompileSdk` 的原因。
代价是 AGP 9.3.2 要求 Gradle ≥ **9.5.0**（本机 wrapper 是 Android Studio 自己升到 9.7.1 的，满足），
比 9.4.0 要求的 9.6.0 略低一点。

**② 走内置 Kotlin + 新 DSL**（两个开关都用默认值），不设
`android.builtInKotlin=false` / `android.newDsl=false`，也不再应用
`org.jetbrains.kotlin.android` 插件。Obtainium 工程关掉它们是为了兼容自己的经典 DSL 写法；
本工程是新写的，没有这个包袱，而这两个逃生舱在 **AGP 10.0** 会被移除，早晚要迁。

由此带来一个额外配置：AGP 9.x 内置的 KGP 是 `2.2.10`（9.0.1 / 9.3.2 / 9.4.0 三者的 `.module`
元数据都验过，是同一个值），且只会自动抬起**低**版本。本工程要 Kotlin `2.4.10`
（Compose 编译器插件必须与 Kotlin 同版本），所以根 `build.gradle.kts` 里有一段

```kotlin
buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10") } }
```

这是 AGP 9.0 发布说明给的唯一做法，不是冗余配置 —— 删掉它会失败在
「Compose 编译器插件版本与 Kotlin 版本不匹配」上。

### 首次打开

**本仓库不含 `gradle/wrapper/gradle-wrapper.jar`**（与 Obtainium 工程同款处理，见 `.gitignore`）。
所以命令行直接 `./gradlew` 会失败。两种做法：

1. **用 Android Studio 打开**，首次 Sync 会自己补齐 wrapper；或
2. 用本机已装的 Gradle 跑一次 `gradle wrapper --gradle-version 9.7.1`。

`gradle-wrapper.properties` 里的 `distributionUrl` 指向 **`services.gradle.org` 官方源**，
用的是 `-bin` 包。注意：Android Studio 会自作主张改写这个文件（初版曾指向腾讯云镜像，
被 Studio 换成了官方源并把版本抬到 9.7.1）。要么接受 Studio 的选择，要么改完就别让它再改
—— 总之**别把这里的版本号降到 AGP 9.3.2 要求的最低 9.5.0 以下**，否则 Sync 会直接失败。
需要看 Gradle 自身源码或在 IDE 里补全 Gradle DSL 时，把 `-bin` 换成同版本的 `-all.zip`。

### `local.properties`（本机参数，不入库）

SDK 路径由 Android Studio 自动写入。以下三项可选，缺省即用括号里的值：

```properties
companion.versionCode=1                                     # 默认 1
companion.versionName=1.0.0                                 # 默认 1.0.0
companion.manifestUrl=https://raw.githubusercontent.com/... # 默认见 app/build.gradle.kts
```

### `key.properties`（签名凭据，**不入库**）

**自更新依赖「签名恒定」。** 换一把签名，覆盖安装会被系统拒绝，而设备上那个版本就再也升不上来了。
所以 keystore 必须**固定不变**并**离线备份**；`key.properties` / `*.jks` / `keystore.*` 已被 `.gitignore` 排除。

```properties
storeFile=<keystore 绝对路径>
storePassword=<...>
keyAlias=<...>
keyPassword=<...>
```

没有 `key.properties` 时 release 构建**不会报错**（那会连带打断 Android Studio 的 Sync），
但会在日志里打印一段醒目的警告，且产物未签名 —— 未签名的包**不能**用于自更新。

---

## 怎么验

### 离线跑通整条链路

设置页 → **调试（仅 debug 构建）** → 打开「使用内置样例清单」。
它会改用 `app/src/debug/assets/fixture-manifest.json`，不联网。

这份样例刻意这样构造：

- `dev.imranr.obtainium`（`kind:"obtainium"`）：**4 个变体**，`name` 是契约形状
  （`…-1.6.15-arm64-v8a.apk`，ABI token 从 `name` 尾缀取，见 02 §2.4），
  `url` 却是**真实可下载**的 Obtainium v1.6.15 官方 asset。
  一次同时验到「折叠选对了变体」和「下载装得上」。
- `com.example.variantdemo`（**无** `kind`）：同样 4 个变体，用来验普通市场 App 的折叠与推送。
- `com.obtainium.companion`（`kind:"companion"`）：`versionCode` 刻意**低于**当前构建，
  所以它只会显示「已是最新」，不会去下一个**尚不存在**的 URL。

### 验自更新（C16）

把 fixture 里 companion 条目的 `versionCode` 改成大于当前构建的值，重开应用 →
主界面出现「伴侣应用 x.y.z 可用」+「更新」按钮。**不要**在 fixture 里放真能下载的 companion URL，
否则会真的触发一次安装 —— 那条 URL 要等第一期发布后才存在。

也可以用**真清单**验：`market-of-labs/store` 里 `com.obtainium.companion` 条目的 `additionalSettings.versionCode`
现在是 `100`。把它调大再刷新即可。

### 验容错（坏条目被跳过而不是整份作废）

在 `apps` 数组里插一条缺 `latestVersion` 的，或把某条 `apkUrls` 写成 `"{不是 JSON"`。
预期：主界面显示「清单里有 N 处被跳过」并逐条列出原因，其余条目照常工作。
若**所有**条目都坏，则整份解析硬失败并给出可读错误（静默产出空 payload 比报错危险得多）。

### 验目标发现（陷阱三）

在设备上装第二份 Obtainium 变体（例如自己签名的），点「重新扫描」→ 应弹出选择框且**不能**点外部关掉。
`<queries>` 块一旦被删，`queryIntentActivities` 在 Android 11+ 恒返回空 —— 表现是
「永远以为没装 Obtainium，反复引导安装」。这个块在 `AndroidManifest.xml` 里，不要删。

---

## 与规格的偏离

以下几处**有意**不照 `01-companion-app-spec.md` 的字面表述做，理由如下：

| # | 规格 | 实现 | 理由 |
|---|------|------|------|
| 1 | §3.12 步骤 4 用 `androidx.core.content.FileProvider` | 自带 `ApkProvider`（只读 `ContentProvider`） | 只需要「把一个已知文件以只读 fd 交给安装器」这一件事。FileProvider 带来 `<paths>` XML 和一整条 androidx.core 依赖链，而这里的安全语义完全等价：`exported=false` + `grantUriPermissions=true` + 调用方显式授予 `FLAG_GRANT_READ_URI_PERMISSION`。 |
| 2 | §3.12 步骤 3 放 `getExternalFilesDir("apk")` | 放 `cacheDir/apk` | 与 `/data` 同一文件系统：安装器取 fd 时少一次跨挂载点拷贝；仍是应用私有，不需要任何存储权限。用完即弃的产物本就应该让系统在存储紧张时能回收。 |
| 3 | —— | URI 长度预检（`DeepLinkBuilder.MAX_URI_CHARS`） | Q4 明确「不要按**条数**预检、不要分片」。这是**长度**预检：不分片，只把 `TransactionTooLargeException` 这个静默/崩溃故障变成一个带可操作文案的错误。注意 Binder 事务缓冲区是**全进程共享**的 1 MB，不是每份 Intent 一份，所以固定条数上限本来就不成立。 |
| 4 | —— | 探测用 `obtainium://apps/probe` | 比用裸 `obtainium://` 更严：保证被发现的目标**真的能接**完整 payload，而不是只能接住 scheme。 |
| 5 | —— | 设置页只有 material3 一套 | 见上文「单 APK、运行时自适应」。 |

---

## 已知风险（未在本机验证）

- **全部代码仍未编译过**。Sync 已跑过，但只走到依赖解析就失败了（见下面那条 compose/AGP 版本冲突），
  Kotlin 源码一个字节都没有编译。上面所有 API 用法都是对着反编译/源码 jar 核过的，
  但「核过」不等于「编译通过」。
- **tv-material 与 BOM 的版本落差**：`androidx.tv:tv-material:1.1.0` 自身是对 compose `1.10.3` 编译的，
  而 `compose-bom:2026.09.00` 解析到 `1.12.x`。Gradle 会取高版本（1.12.x），
  即 tv-material 1.1.0 跑在比它新的 compose 上。AndroidX 内部通常二进制兼容，但这是**唯一**的运行时风险点。
  为把暴露面压到最小，`ui/TvScreen.kt` **刻意只用了 `androidx.tv.material3` 的三个成员**
  （`MaterialTheme` / `Text` / `Button`），其余全部用 Compose foundation 原语手写
  —— 包括进度条和那个全屏选择覆盖层（tv-material3 没有对话框组件）。
- **compose 与 AGP 的版本耦合**：这不是「配错了一次」，而是这条链本身的形状 ——
  compose 1.12.x 要求 AGP ≥9.1.0，AGP 9.3.2 要求 Gradle ≥9.5.0。**将来单独升其中任何一个都可能触发
  另一条下限**，而且报错点离真正的原因很远（报的是依赖名，不是版本策略）。
  撞上时按这个顺序查：compose BOM → AGP → Gradle，每一跳的官方兼容表都在 release notes 里。
- **AGP 9 新 DSL 尚未验证到**：内置 Kotlin 与新 DSL 已按官方迁移指南改完
  （删 `org.jetbrains.kotlin.android`、删 `kotlin{compilerOptions{jvmTarget}}`、根工程抬 KGP classpath），
  但因为依赖解析先失败了，配置阶段**还没真正走到**。新 DSL 下最可能出问题的是 `packaging{}` /
  `signingConfigs{}` / `buildTypes{}` / `compileOptions{}` 这几处写法。
  失败时先看 Sync 日志里的 `e:` 行，再对照
  `developer.android.com/build/migrate-to-built-in-kotlin`。

---

## 目录

```
app/src/main/kotlin/com/obtainium/companion/
  Model.kt              数据模型 + MarketException + MarketCache
  Prefs.kt              全部持久化状态（SharedPreferences）
  BuildInfo.kt          版本 / 签名指纹 / 设备 ABI
  MarketClient.kt       只负责拉清单原文
  ManifestParser.kt     信封解析 + 逐条容错校验
  AbiFilter.kt          按设备 ABI 折叠 apkUrls
  DeepLinkBuilder.kt    剥壳 + Uri.encode → obtainium://apps/<payload>
  ObtainiumLauncher.kt  目标发现（setPackage 精确投递）+ <queries>
  DownloadInstaller.kt  下载（自己跟重定向、.part 改名、ZIP 魔数校验）→ 系统安装器
  ApkStore.kt           下载落盘目录 + 文件名净化
  ApkProvider.kt        只读 ContentProvider（FileProvider 的等价子集）
  SelfUpdate.kt         companion 条目比对
  UiState.kt            界面状态 + 文案
  MarketViewModel.kt    三个职责的唯一状态机
  MainActivity.kt       单屏主界面（运行时选手机/电视渲染）
  SettingsActivity.kt   设置页
  ui/                   ScreenContent（与设计系统无关）→ PhoneScreen / TvScreen / SettingsScreen
app/src/debug/assets/   fixture-manifest.json（仅 debug）
```
