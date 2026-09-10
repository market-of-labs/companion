// 根工程本身不配模块，只做一件插件版本层面的事。
//
// AGP 9.x 的内置 Kotlin 运行期依赖 KGP 2.2.10（9.0.1 / 9.3.2 / 9.4.0 三者的 .module 元数据都是这个值），
// 且只会「自动抬高低版本」。
// 本工程要 Kotlin 2.4.10（Compose 编译器插件必须与 Kotlin 同版本），
// 就必须显式把 kotlin-gradle-plugin 抬到 classpath 上 —— 这是 AGP 9.0 发布说明给的唯一办法。
// 少了这段，Sync 会失败在「Compose 编译器插件版本与 Kotlin 版本不匹配」上。
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
