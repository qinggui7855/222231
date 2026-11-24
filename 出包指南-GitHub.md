# 出包指南（GitHub 网页版，不用装任何软件）

目标：把一个 `.apk` 装到你手机上。全程只用浏览器点鼠标，不需要 git、不需要 Android Studio。

预计耗时：注册 3 分钟 + 上传 2 分钟 + 等编译 5 分钟。

---

## 第 0 步：先把压缩包解压

把 `star-farmer.zip` 解压到一个好找的位置，比如 `D:\star-farmer`。
**待会儿要上传的是解压后文件夹里面的内容，不是这个 zip。**

---

## 第 1 步：注册 GitHub（已有账号跳到第 2 步）

1. 打开 <https://github.com>
2. 点右上角 **Sign up**
3. 填：邮箱 → 密码 → 用户名（英文+数字，比如 `xiaoming2024`）
4. 邮箱收验证码 → 填进去 → 完成
5. 如果注册时问 "How many teams" 之类，随便选，或直接 Skip

> 打不开 / 很慢的话：GitHub 在国内经常需要加速（Watt Toolkit「Steam++」勾选 GitHub 加速，或你自己的网络工具）。这一关过不去就只能走 Android Studio 本地编译。

---

## 第 2 步：建一个空仓库

1. 登录后，点右上角 **+** → **New repository**
2. **Repository name** 填：`star-farmer`
3. 下面选 **Private**（私有）或 **Public**（公开）都行：
   - Private：每月 2000 分钟免费 Actions，够用很多次
   - Public：Actions 完全免费无限
4. **不要**勾 "Add a README file"、不要选 .gitignore、不要选 license（保持空仓库）
5. 点 **Create repository**

---

## 第 3 步：上传代码

新建好的空仓库页面中间会有一行：

```
…or upload an existing file
```

1. 点 **uploading an existing file**
2. 打开你解压出来的 `star-farmer` 文件夹，**全选里面的所有内容**（含 `.github`、`app`、`tools`、`ref`、`README.md` 等），拖到浏览器的虚线框里
3. 等文件列表刷完（应该有 40 多个文件），在底部 **Commit changes** 点下去

> 如果 `.github` 文件夹没被带上去（上传列表里看不到 `.github`），补一下：
> 仓库页 → **Add file** → **Create new file** → 文件名输入
> `.github/workflows/build.yml`（输入 `/` 会自动建目录）→ 把本文件末尾的 YAML 内容粘进去 → Commit。
>
> 上传时如果提示文件太多，可以分两次：先传 `app`、`tools`、`ref`、根目录几个文件，再单独补 `.github`。

---

## 第 4 步：等它自动编译

1. 仓库顶部点 **Actions** 标签
2. 会看到一条 `build-apk` 正在跑（黄色小圆点 → 转圈）
3. 等 3~5 分钟变成**绿色对勾** ✅

> 第一次进去可能提示 *Workflows aren't being run…*，点绿色按钮 **I understand my workflows, go ahead and enable them** 即可。

---

## 第 5 步：下载 APK

1. 点进那条绿色的 `build-apk` 运行记录
2. 拉到页面**最下方**，找到 **Artifacts** 区域
3. 点 **star-farmer-debug-apk** 下载（会得到一个 zip）
4. 解压这个 zip，里面就是 **`app-debug.apk`**

---

## 第 6 步：装到手机

1. 把 `app-debug.apk` 传到手机（微信「文件传输助手」、QQ、数据线都行）
2. 手机上点开这个文件安装
3. Android 15 会拦一下：点「设置」→ 允许「来自此来源的应用」→ 返回继续安装
4. 装好后打开「星际农夫」

### 首次使用（顺序别错）

1. **横屏**拿着手机
2. 点 **① 无障碍** → 系统列表里找到「星际农夫 · 自动操作」→ 打开
   - 若提示「受限设置」，点允许后再回来开
3. 回 App → 点 **② 投屏授权** → 系统弹窗点「开始录制/立即开始」
   - 每次重启 App 都要重新授权，这是 Android 14+ 的系统限制
4. 切到游戏，停在**副本详情页（图一）**
5. 切回 App → 点 **③ 开始**
6. 再切回游戏，看着它干活；日志在 App 里能翻

---

## 以后想改配置

改完 `program.json` 或换了模板图后，在 GitHub 网页上删掉旧文件、上传新文件（同样一次 Commit），Actions 会自动再编译一版，重复第 4、5 步拿新 APK。

---

## 常见问题

| 现象 | 处理 |
| --- | --- |
| Actions 页面是灰的 / 没触发 | 点 Enable workflows；或到 Actions 里选 `build-apk` → **Run workflow** 手动跑 |
| 红色叉号（构建失败） | 点进去看红色那一步，把最后 30 行日志发我，我直接改代码 |
| 下载的 Artifacts 只有 build-reports | 说明编译失败，看它里面的报告；或者直接把日志发我 |
| APK 装上闪退 | 大概率是 Java 代码问题，把手机上的报错（或 `adb logcat`）发我 |
| 手机提示「应用未安装」 | 卸载旧版再装；或检查是不是下载到了 `build-reports` 那个包 |
| 脚本点不动 / 乱点 | 看 App 日志里哪一行分数低，把对应 `thr` 调低（见 README 第四节） |

---

## 附：.github/workflows/build.yml 的内容

如果需要在网页上手动创建这个文件，把下面整段粘进去：

```yaml
name: build-apk

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.9'

      - name: Build debug APK
        run: gradle :app:assembleDebug --no-daemon --stacktrace

      - name: Upload APK
        if: success()
        uses: actions/upload-artifact@v4
        with:
          name: star-farmer-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error

      - name: Upload build reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: build-reports
          path: |
            app/build/reports/
            app/build/outputs/logs/
          if-no-files-found: ignore
```
