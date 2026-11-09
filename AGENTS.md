# 本仓库的工作约定

## 运行环境：Windows 与 WSL

同一份仓库会从 Windows 和 WSL 两边打开（WSL 里是 `/mnt/c/Album`）。改代码、读代码、提交两边
都能做，但**工具链只在 Windows 侧**：

- `gradlew.bat`、`scripts/*.ps1`、`adb`、签名和打包都依赖 Windows 上装的 JDK / Android SDK。
  WSL 里没有 JDK、没有 Android SDK，也看不到 `C:\Windows`、`C:\Program Files`，所以构建、
  单测、lint 和设备检查要在 Windows 侧执行；WSL 里只做代码改动、`git` 和文档。
- 在 WSL 里收尾的一轮，回答里要写清楚构建/Lint 没在本机跑过，不要写成已验证。
- 行尾由 [`.gitattributes`](.gitattributes) 统一：索引里一律 LF，Windows 检出时按 CRLF 落地。
  不要用 `core.autocrlf` 覆盖它；否则一次 `git add -A` 会把两百多个文件重写成 CRLF，产生
  约五万行与真实改动无关的噪声。

## 每轮改动都要本地存档，按需推送

1. 一轮工作做完后，把全部改动提交到 `main`：

   ```powershell
   git add -A
   git commit -m "<版本号或主题>: <一句话说明>"
   ```

2. **版本第三位是 `10` 的倍数（包括 `0`）时，推送 GitHub**。其它版本默认只保留
   本地提交；用户明确要求推送时仍可推送。需要推送时执行：

   ```powershell
   git push origin main
   ```

3. 改了版本号（`version.properties`）也必须完成本地提交，并在回答里给出提交号。
   推送规则以上一条为准。

4. 提交信息用英文或中文都可以，但要写清楚"改了什么、为什么"，与 `CHANGELOG.md`
   顶部那一节保持一致。

## 版本号

- 唯一来源是 `version.properties`。任何其它文件（README、`app/release/`）都不要再写
  "当前版本号"。
- 每次升版时 `VERSION_PATCH` 增加 `1`，`VERSION_CODE` 每次构建也按 `1` 递增。
- 签名 release 构建成功后 `packageRelease` 会自动把 patch 号加一；如果只是本地验证
  构建、并不打算发布，记得把 `version.properties` 改回本次目标版本。
- 发布流程见 [RELEASING.md](RELEASING.md)；更新清单必须作为 Release 资产上传，资产名
  固定为 `album-update.json`。

## 构建产物

- `app/release/` 与 `app/build/` 都不入库（`.gitignore` 已覆盖）。APK、更新清单、
  SHA-256 一律通过 GitHub Releases 分发。
- 提交前至少跑一次：

  ```powershell
  .\gradlew.bat testDebugUnitTest lintDebug
  ```

  lint 使用 `app/lint-baseline.xml` 锁定历史告警；新增告警会导致构建失败，请修掉或
  明确更新 baseline。

## 文案与测试

- 界面文案统一放在 `ui/AppLanguage.kt` 的单一字典里，新增 `appText("中文")` 调用点
  必须同时补英文，否则 `TranslationCoverageTest` 会失败。
- 关键逻辑（更新检查、收藏身份、传输冲突策略等）要有单元测试。
