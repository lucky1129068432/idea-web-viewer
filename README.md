# Web Viewer

把 Chromium 浏览器嵌进 IntelliJ IDEA 的编辑器标签页：一个网址 = 一个标签页，查资料、看文档不用切出 IDE。

## 安装

1. `Settings → Plugins → ⚙ → Install Plugin from Disk…`
2. 选择打包好的 `idea-web-viewer-*.zip`
3. 重启 IDE

## 怎么打开

装好后有 **三个入口**，都在主菜单 `Tools` 下：

| 入口 | 位置 | 说明 |
|---|---|---|
| **Open URL** | `Tools → Open URL` | 主入口，新开一个网页标签页 |
| 快捷键 | `Ctrl + Shift + U` | 同上，最快的方式 |
| **Toggle Web Viewer Theme** | `Tools → Toggle Web Viewer Theme` | 切换当前标签页的亮/暗主题 |

> macOS 上快捷键若没生效，去 `Settings → Keymap` 搜 `Open URL` 自行绑定。
> 快捷键被别的插件占用时也同样处理。

打开后默认加载百度首页。**在顶部地址栏输入任意网址、回车即可跳转**，不写 `https://` 会自动补上。

## 使用说明

打开后的界面从上到下是：**地址栏** + **工具栏** + **网页区域**。

工具栏按钮（从左到右）：

| 按钮 | 作用 |
|---|---|
| 后退 / 前进 | 页面历史导航，不可用时自动置灰 |
| 刷新 | 重新加载当前页面 |
| 历史 | 打开历史记录窗口：双击跳转、删除单条、清空全部（最多保留 200 条） |
| 阅读 | 抽取正文进入专注阅读模式，按钮变「退出」，再点返回原页面 |
| 简转 | 把当前页面正文里的繁体字批量转成简体 |
| 收藏 | 打开书签窗口：填标题/网址添加，双击跳转、删除（按网址去重，最多 300 条） |
| 暗色 / 亮色 | 切换整页配色，主题按标签页记住 |
| 亮度滑块 | 左暗右亮，靠 CSS 滤镜实现，不改变页面本身 |
| 配色 | 自定义网页的背景色和文字颜色，可一键重置 |

几个顺手的行为：

- **同一网址重复打开会复用已有标签页**，不会开一堆重复页；
- 网页里的 `Ctrl+点击`、`target=_blank`、`window.open` 都会在编辑器内新开标签，不弹独立窗口；
- 关掉标签页就结束浏览，网址本身不会保存，需要留存请用「收藏」。

## 环境要求

- IntelliJ IDEA **263.x**（`since-build=263`，`until-build=263.*`）
- 需要 IDE 自带的 `com.intellij.modules.jcef` 插件（默认启用，不要关掉）

> 2024.1 ~ 2026.2（241~262）暂不支持：这些版本的 JCEF 随平台发布，没有可以声明的模块 id，
> 与 263 的依赖方式不兼容。

## 常见问题

**打开后是空白页 / 一直转圈**
Chromium 内核首次启动需要几秒，稍等即可；仍然空白就重启 IDE 再试。

**提示「当前 IDE 未启用 JCEF」**
在 `Settings → Plugins` 里确认 `Web Browser (JCEF)` 处于启用状态。

**某些网站显示错乱或按钮点不动**
插件会往页面注入页边距、配色等样式，个别站点可能受影响。可以先关掉「暗色」和「配色」再试。

## 开发

```bash
./gradlew compileJava    # 编译
./gradlew test           # 单元测试
./gradlew buildPlugin    # 打包，产物在 build/distributions/
./gradlew runIde         # 启动带插件的沙箱 IDE
```

> Gradle Wrapper 固定 8.6：`org.jetbrains.intellij` 1.17.4 只在 Gradle 8.x 上验证过，不要升到 Gradle 9。

升级目标 IDE 版本时，除了改 `build.gradle.kts` 里的 `untilBuild`，建议再跑一次兼容性验证
（用本机已安装的 IDE 当编译目标，JDK 25 会自动从 `JAVA_HOME` 或 IDE 自带 `jbr` 中挑选）：

```powershell
./scripts/verify-against-ide.ps1 -IdePath "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2"
```

## 已知限制

- 阅读模式的正文抽取基于 DOM 启发式，结构特殊的页面可能抽不准；
- 繁简转换是字级映射（`src/main/resources/opencc.js`），不处理词组差异；
- 点击网页里的下载链接不会触发 IDE 的下载流程。
