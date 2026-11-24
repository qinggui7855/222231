# 星际农夫 · 情系刀塔 自动刷图 + 星际商人采购

一个纯 Java、零第三方依赖的安卓自动化 App：**投屏抓帧 → 灰度模板匹配 → 无障碍手势点击**。
不 root、不需要电脑常驻、targetSdk 35（Android 15 可用），横屏运行。

按你的描述实现的一整套循环：

```
图一 点右下大箭头开打
   → 图二 结算，点右上循环图标回图一
   → 一直重复，直到出现图三/图四 的黄色「星际商人来访」横幅
   → 回图一 → 点左上返回 → 主城(图五) → 向右滑到图六 点商人
   → 图七 先关掉「是否将无用物品卖给商人」弹窗（点「好的」）
     → 采购三个箱子（有确认框就点「确定」，售罄的没有确认框，自动跳过）
   → 图九 点左上返回 → 图六 → 向左滑回图五 → 点「战役」
   → 章节地图，点左箭头翻到第七章 → 点右下角那个关卡节点（图一 的墓碑节点）
   → 回到图一，开始下一轮
```

---

## 一、出 APK（不用装 Android Studio）

本机没有 JDK/SDK、也出不了网，所以走 GitHub Actions 云端编译。

1. 解压本目录，推到任意一个 GitHub 仓库（private 也行）：
   ```bash
   cd star-farmer
   git init && git add . && git commit -m "init"
   git remote add origin https://github.com/<你的账号>/<仓库名>.git
   git push -u origin main
   ```
2. 仓库 **Actions** 页等 `build-apk` 跑完（首次约 3~5 分钟）。
3. 该次运行底部 **Artifacts** → 下载 `star-farmer-debug-apk` → 解压得 `app-debug.apk`。
4. 传到手机安装（Android 15 会拦，允许「来自此来源的应用」）。

以后改 `program.json` 或换模板，push 一下就会重新出包。

---

## 二、手机上的使用顺序

1. **横屏拿着手机**打开 App → 点 **① 无障碍** → 系统设置里找到「星际农夫 · 自动操作」打开。
   - Android 15 若提示「受限设置」，先点允许再回来开开关。
   - 部分系统（MIUI/ColorOS 等）还要给「后台弹出界面」「锁屏显示」权限。
2. 回到 App → 点 **② 投屏授权** → 系统弹窗点「开始录制 / 立即开始」。
   - **Android 14+ 每次启动投屏都要重新授权**，这是系统限制。
3. 切到游戏，停在**图一（副本详情）**或任意一个你认得出的界面，再切回 App 点 **③ 开始**。
4. 点完开始就可以切回游戏看着它干活了。状态行会显示 `无障碍 ✓ 投屏 ✓ 引擎 ▶ 运行中`。
5. 电量策略给「无限制」，否则息屏后投屏会被系统掐掉。

日志窗口会实时打出每一步：命中了哪个模板、分数多少、点了哪里、跳到了哪个标签。出问题时先看日志最后几行。

---

## 三、工作原理

| 环节 | 实现 |
| --- | --- |
| 抓屏 | `CaptureService`：MediaProjection + ImageReader，按 1/2 分辨率转灰度 |
| 找按钮 | `Template`：ZNCC 零均值归一化互相关；积分图预筛 + 步长 4 粗定位 + ±4 精修 |
| 点击 | `BotAccessibilityService`：`dispatchGesture`，纯手势，不读窗口内容 |
| 决策 | `BotEngine`：解释执行 `program.json`（可 goto 的指令流） |

模板是在 **2510×1156** 的参考分辨率下裁的，运行时按你手机的实际分辨率自动缩放
（宽度比、高度比两个比例各试一次，取分数高的），所以换分辨率不用重新裁图。

---

## 四、program.json 指令集

```jsonc
{ "op": "label",  "name": "battle" }                       // 定义跳转标签
{ "op": "goto",   "label": "battle" }                      // 无条件跳转
{ "op": "if",     "t": ["a.png","b.png"], "timeoutMs": 2000, "thr": 0.88, "goto": "x" }
        // 等最多 timeoutMs，找到(任一)就跳到 x，否则往下走
{ "op": "ifnot",  "t": ["a.png"], "goto": "x" }            // 没找到就跳
{ "op": "wait",   "t": ["a.png"], "timeoutMs": 5000, "goto": "x" }
        // 等它出现，出现就往下走，超时才跳到 x
{ "op": "tap",    "t": ["a.png"], "timeoutMs": 1500, "goto": "x" }
        // 找到就点它（点模板中心），找不到跳到 x
{ "op": "tapxy",  "x": 0.301, "y": 0.551 }                 // 点屏幕比例坐标
{ "op": "swipe",  "x": 0.28, "y": 0.55, "x2": 0.82, "y2": 0.55, "ms": 350 }
{ "op": "sleep",  "ms": 800 }
{ "op": "back" }                                           // 系统返回
{ "op": "set",    "var": "c", "val": 0 }                   // 变量赋值
{ "op": "add",    "var": "c", "val": 1 }
{ "op": "iflt",   "var": "c", "val": 20, "goto": "x" }     // 小于则跳
{ "op": "ifgt",   "var": "c", "val": 20, "goto": "x" }     // 大于则跳
{ "op": "log",    "msg": "打印一行中文日志" }
{ "op": "stop",   "msg": "停止脚本" }
```

`roi`（可选）= `[x, y, w, h]` 比例，限定只在这一块找，能显著提速并防误判。

### 关键可调项

| 想改什么 | 改哪里 |
| --- | --- |
| 误点/乱跳 | 把对应指令的 `thr` 调高（0.90~0.95） |
| 该点没点 | `thr` 调低到 0.82~0.85，或重裁模板 |
| 商人出现后没反应 | 看日志分数：低于 0.90 就把 `battle` 里那条 `if` 的 `thr` 调低 |
| 翻章翻过头 | 改 `fl_loop` 的 `ifgt c 20`（左翻上限）与 `fr_loop` 的 24 |
| 主城滑不到商人 | 改 `hub_loop` 的滑动次数上限 6 / `hub_back_loop` 的 12 |
| 点得太快/太慢 | 各处的 `sleep` 值，默认已偏保守 |

---

## 五、改了游戏界面怎么办

模板全是从 `ref/` 里的参考截图裁的，改坐标后一条命令重生成：

```bash
python tools/make_templates.py      # 重新裁全部模板 + 输出对照图 ref/_templates_sheet.png
python tools/validate_program.py    # 检查标签/模板/坐标是否都合法
python tools/selftest_match.py      # 把每个模板在全部参考截图上跑一遍，看会不会误认
python tools/simulate_program.py    # 用假的游戏状态机空跑一遍脚本，验证流程不卡死
```

`selftest_match.py` 的输出很有用：每行是「这个模板在各张截图上的最高分」。
自己截图的分数应该接近 1.00，别的界面应该明显低（一般 < 0.7），
两者之间取中间值当 `thr` 最稳。

`simulate_program.py` 会模拟「打图→结算→商人到访→采购→回主城→翻到第七章→回图一」全流程，
确认两个商人周期都能顺利完成（这是改完 program.json 后最快的回归测试）。

---

## 六、已知限制（先看，别当 bug）

1. **没有 OCR**，读不了文字，所以「三个箱子都买完没」是靠「点了有没有确认框」判断的：
   售罄的箱子点了不出确认框，自然被跳过。灵魂石不够时同理（点了可能弹「不足」提示，不影响流程）。
2. **第七章靠 `chapter7_node.png` 识别**（已用真实第七章地图裁剪，实测自匹配 0.996，其它章节地图最高 0.34，
   不会误认别的章节）。若哪天地图换皮，把 `thr` 降到 0.78 或重裁节点图。
3. **章节地图的左右箭头只裁了三角形本身**，跨章节背景都能认（两套地图实测 0.94~0.99）；
   若游戏改了箭头样式或位置，重裁 `chapter_left/right.png`。
4. **投屏遇到 FLAG_SECURE 会全黑**（部分游戏防截屏）。真黑屏就只能改成固定坐标盲点模式，
   那套方案要先确认游戏没开防截屏。
5. **每次启动都要点一次投屏授权**（Android 14+ 系统限制）。
6. **体力耗尽会卡住**：体力没了游戏会弹「购买体力」，脚本没有 OCR 读不出对话框文字，
   只会反复点箭头/按返回空转。你的号 494/500 体力、每场 6 点，大约 80 场后要注意；
   想跑更久就自己定量体力药水，或把 `maxRunMs` 调到对应时长。
7. 游戏切后台 / 息屏 / 转竖屏都会让抓帧失效；请保持横屏前台 + 允许后台运行。
8. 脚本只做「点击和等待」，不含任何内存修改、注入或外挂行为；但**第三方自动化大概率违反游戏用户协议**，
   被判定脚本有封号风险，请自行评估，别用在有真人对抗或涉及充值的场景。

---

## 七、目录结构

```
star-farmer/
├─ .github/workflows/build.yml         云打包
├─ ref/                                你给的 14 张参考截图（裁模板用）
├─ app/src/main/
│  ├─ AndroidManifest.xml              横屏、无障碍声明、前台服务类型
│  ├─ assets/program.json              ★ 流程脚本（主要改这个）
│  ├─ assets/templates/*.png           ★ 模板图（由 tools/make_templates.py 生成）
│  └─ java/com/starfarmer/bot/
│     ├─ MainActivity.java             授权面板 + 日志
│     ├─ BotEngine.java                program.json 解释器
│     ├─ Program.java                  脚本模型与解析
│     ├─ Matcher.java                  分辨率换算 + 多模板匹配
│     ├─ Template.java                 ZNCC 匹配、模板缓存
│     ├─ Integral.java                 积分图（O(1) 窗口均值/方差）
│     ├─ CaptureService.java           投屏抓帧
│     ├─ BotAccessibilityService.java  点击/滑动/返回
│     ├─ Gray.java / Logs.java
└─ tools/
   ├─ make_templates.py                从 ref/ 重裁全部模板
   ├─ validate_program.py              program.json 静态校验
   ├─ selftest_match.py                模板离线自测（numpy 复刻 ZNCC）
   ├─ simulate_program.py              流程离线空跑（假游戏状态机）
   └─ crop_templates.py                交互式裁剪工具（游戏更新后手裁用）
```
