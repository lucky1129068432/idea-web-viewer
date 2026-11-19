# Web Viewer

把 Chromium 浏览器嵌进 IntelliJ IDEA 编辑器标签页的插件：一个网址 = 一个标签页，浏览网页时不用切出 IDE。

## 功能

| 功能 | 说明 |
|---|---|
| 地址栏跳转 | 输入网址回车直达，未写协议时自动补 `https://` |
| 前进 / 后退 / 刷新 | 基于 CEF 原生历史 |
| 历史记录 | 记录标题、网址与时间，支持跳转、单条删除、清空；上限 200 条 |
| 书签 | 保存标题与网址，支持跳转、删除；按 URL 去重，上限 300 条 |
| 暗色 / 亮色主题 | 可对整个标签页套用配色，主题按标签页记忆 |
| 亮度调节 | 通过 CSS `filter: brightness()` 实现，左暗右亮 |
| 自定义配色 | 分别设置背景色与文字颜色 |
| 阅读模式 | 抽取正文生成专注阅读层，可随时退出 |
| 繁转简 | 对内嵌页面做繁体转简体批量转换 |
| 标签页内打开 | `Ctrl+点击` 链接或 `target=_blank` / `window.open` 都在编辑器内新开标签，不弹独立窗口 |

## 环境要求

- IntelliJ IDEA **263 及以上的 263.x**（`since-build=263`，`until-build=263.*`）
  - 目标版本：**IU-263.7307.200**（产品版本 2026.2.2 / 2026.3，JCEF 150.0.14），已实测编译 + 安装运行
- 构建使用 JDK 17+（`gradle.properties` 中指定了 `org.gradle.java.home`，按需修改）
- **必须**有 `com.intellij.modules.jcef` 插件：263 起 `org.cef.*` 才从平台核心 jar
  （`lib/lib-client.jar`）移入这个独立插件，缺了它类加载会直接失败。
  也正因为该模块 id 在 241~262 中并不存在（JCEF 当时随平台发布），插件只声明支持 263+

## 构建与运行

```bash
# 编译
./gradlew compileJava

# 单元测试
./gradlew test

# 生成可分发的 zip（产物在 build/distributions/）
./gradlew buildPlugin

# 在沙箱 IDE 里调试运行
./gradlew runIde
```

> Gradle Wrapper 固定为 8.6：`org.jetbrains.intellij` 1.17.4 只在 Gradle 8.x 上验证过，请勿随意升级到 Gradle 9。

## 适配新版 IDE（263 及以后）

从 2026.x 起，JetBrains 平台部分模块是 **Java 25 字节码**（例如 `JBCefBrowser` 的 class 版本为 69），
并且 IDE 分发包按 `plugins/<id>/lib/modules/*.jar` 的模块化布局组织。若只在 Gradle 里改版本，
遇到内网拉不到 `cache-redirector.jetbrains.com` 时无法编译。

可以用脚本直接拿本机已安装的 IDE 当编译目标，验证源码与目标平台 API 是否兼容：

```powershell
# JDK 25 会自动从 JAVA_HOME / IDE 自带 jbr 中挑选
./scripts/verify-against-ide.ps1 -IdePath "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2"
```

脚本会用 JDK 25 的 `javac` 编译 `src/main/java`（产物只用于验证，不参与打包），
输出 `OK` 且退出码为 0 即表示兼容。升级目标版本时改 `build.gradle.kts` 里的
`untilBuild`，再跑一次脚本确认即可。

## 使用方式

1. `Tools → Open URL`（快捷键 `Ctrl+Shift+U`）打开浏览器标签页；
2. `Tools → Toggle Web Viewer Theme` 或工具栏「暗色/亮色」按钮切换主题；
3. 在地址栏输入网址回车即可访问。

## 项目结构

```
src/main/java/com/idea/webviewer/
├─ action/          入口动作：OpenUrlAction、ToggleThemeAction
├─ editor/          WebViewerEditor（Swing UI）、JBCefBrowserWrapper（JCEF 与脚本注入）、
│                   WebViewerEditorProvider（*.webpage 路由）
├─ bookmark/        书签存储与对话框
├─ history/         历史记录存储与对话框
├─ storage/         无第三方依赖的记录编解码与旧数据迁移
└─ util/            临时 .webpage 文件管理（创建、复用、清理）
```

## 实现要点

插件把「编辑器标签页」建模成一个临时文件：`%TEMP%/idea-webviewer/webpage_*.webpage`，文件内容就是网址。`WebViewerEditorProvider` 只接管 `.webpage` 扩展名，所以打开该文件时渲染的是浏览器而不是文本编辑器。

- 同一网址重复打开会复用已有标签页；超过 6 小时的残留临时文件会在下次创建时清理。
- 历史记录与书签保存在应用级 `PropertiesComponent` 中，使用 Base64 行格式序列化（不依赖 Gson）；从 1.0.1 升级时会自动迁移旧的 JSON 数据。
- 打开标签页走 `OpenFileDescriptor.navigate(true)`（`Navigatable` 接口），而不是
  `FileEditorManager.openFile`——后者在 263 上所有重载都已被标记为过时。
- 创建浏览器前会用 `JBCefApp.isSupported()` 探测 JCEF；不可用时显示提示而不是抛异常。
- 页面注入逻辑（页边距、暗色配色、亮度、弹窗拦截、阅读模式、繁简转换）都在 `JBCefBrowserWrapper` 中，通过 `CefLoadHandler.onLoadEnd` 在每次加载完成后重新注入。

## 已知限制

- 阅读模式的正文抽取是基于 DOM 启发式的，复杂页面可能抽不准。
- 繁简转换使用 `src/main/resources/opencc.js`（AutoCF AutoHan 的复刻实现），词级转换能力有限。
- 插件不解析网页里的下载请求，点击下载链接不会触发 IDE 的下载流程。
