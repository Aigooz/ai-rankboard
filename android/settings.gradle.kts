// 网络分流：GitHub Actions（海外）只走官方仓库，避免阿里云镜像跨洋抖动导致插件解析失败；
// 本地（国内网络）保留阿里云镜像优先，绕开 dl.google.com 等直连不通的仓库。
pluginManagement {
    repositories {
        if (System.getenv("CI") != "true" && System.getenv("GITHUB_ACTIONS") != "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("CI") != "true" && System.getenv("GITHUB_ACTIONS") != "true") {
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "AILeaderboard"
include(":app")
