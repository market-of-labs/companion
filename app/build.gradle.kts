import java.util.Properties

// AGP 9 内置 Kotlin：不再应用 org.jetbrains.kotlin.android。
// Compose 编译器插件仍要显式应用（内置 Kotlin 不含它）。
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// local.properties：非敏感的本机构建参数（版本号、默认清单地址）
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}

// key.properties：签名凭据（不入库）
val keystoreProperties = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}

val appVersionCode = (localProperties.getProperty("companion.versionCode") ?: "1").toInt()
val appVersionName = localProperties.getProperty("companion.versionName") ?: "1.0.0"

// 第一期内置默认清单地址（规格 01 §2.1）。部署期切 CF 后，用户可在设置页改，无需发版。
//
// apps.json 在 store 仓库的**根目录**（不在 store/ 子目录里）—— 那个子目录只放 endpoints.json
// 这类不直接伺服给客户端的东西。所以路径是 /apps.json，不会有 store/store 的重段。
//
// 分支名是 **master**，不是 main —— store 仓库的默认分支就叫 master，照抄 raw 直链时必须用对，
// 写错会在设备上表现为 404（清单拉不下来）。注意 companion 仓库自己用的是 main，两者不同名，
// 这是有意的：改地址比改默认分支的代价小（后者要动远端与本地跟踪分支）。
// 部署期切到 CF 后这段整个作废（地址变成 https://<cf域>/manifest）。
val defaultManifestUrl = localProperties.getProperty("companion.manifestUrl")
    ?: "https://raw.githubusercontent.com/market-of-labs/store/master/apps.json"

// 这里刻意不写 kotlin { compilerOptions { jvmTarget = ... } }：
// 内置 Kotlin 下 jvmTarget 的默认值就是 android.compileOptions.targetCompatibility，
// 下面 compileOptions 已经设成 17，再写一遍只是多一处会漂移的重复。

android {
    namespace = "com.obtainium.companion"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.obtainium.companion"
        minSdk = 26          // = Obtainium 自身的 minSdk：设备至少要能跑 Obtainium
        targetSdk = 34       // 刻意停在 34：避开更高版本对安装 Intent 的行为变更
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "DEFAULT_MANIFEST_URL", "\"$defaultManifestUrl\"")
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
            storeFile = keystoreProperties.getProperty("storeFile")?.let { file(it) }
            storePassword = keystoreProperties.getProperty("storePassword")
        }
    }

    buildTypes {
        getByName("release") {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            } else if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
                // 不抛异常（会连带打断 Android Studio 的 Sync），但把话说到最响。
                logger.error(
                    """
                    ⚠️ 正在构建 release，但没有可用的 key.properties —— 产物将未签名。

                    自更新依赖「签名恒定」：换一把签名，覆盖安装就会失败，
                    而设备上那个版本再也升不上来。请在 [companion]/key.properties 里配置：

                        storeFile=<keystore 绝对路径或相对路径>
                        storePassword=<...>
                        keyAlias=<...>
                        keyPassword=<...>

                    并把这个 keystore 离线备份。keystore.* / *.jks / key.properties 已被 .gitignore 排除。
                    """.trimIndent()
                )
            }
            // v1 不开 R8：APK 只有几 MB，省下的体积不值得引入「release 才崩」的风险。
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.kotlin_module",
            "META-INF/{AL2.0,LGPL2.1}",
        )
    }
}

dependencies {
    // ---- Compose：由 BOM 统一版本，下面各 artifact 一律不写版本号 ----
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // 手机 / 通用
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // 电视：androidx.tv.material3 —— 带 D-pad 焦点态（放大 + 描边）的 10 尺组件
    implementation("androidx.tv:tv-material:1.1.0")

    // 宿主与生命周期
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
