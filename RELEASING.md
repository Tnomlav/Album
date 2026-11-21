# 发布流程

`version.properties` 是版本号的唯一来源。不要在 `README.md`、`app/release/` 或
`CHANGELOG.md` 里单独维护版本号。

发布由 **tag 触发的 GitHub Actions** 完成：推送 `v<versionName>` 后，
[`.github/workflows/release.yml`](.github/workflows/release.yml) 会构建签名包、跑单测/lint、
校验三个 ABI 的版本、生成更新清单与 SHA-256、创建 GitHub Release 并上传资产。
`.github/workflows/android.yml` 的日常构建仍只上传 CI artifact，不发布。

## 0. 一次性配置

在 GitHub 仓库 **Settings → Secrets and variables → Actions** 添加四个 secrets：

| Secret | 内容 |
| --- | --- |
| `ALBUM_STORE_BASE64` | `key/albumiv-release.jks` 的 Base64（不要提交密钥库） |
| `ALBUM_STORE_PASSWORD` | keystore 密码 |
| `ALBUM_KEY_ALIAS` | key alias（当前为 `album`） |
| `ALBUM_KEY_PASSWORD` | key 密码 |

生成 Base64（PowerShell）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("key\albumiv-release.jks")) | Set-Clipboard
```

`GITHUB_TOKEN` 由 Actions 自动提供，不需要手工创建；release job 已声明
`permissions: contents: write`。

## 1. 准备版本与发布说明

1. 编辑 `version.properties`：`VERSION_CODE` 加一，`VERSION_PATCH` 加一。
   一次发布只改这里；构建过程不会改版本号。
2. 在 `CHANGELOG.md` 顶部新增/更新对应 `## v<versionName>` 一节。
   `scripts/extract-release-notes.py` 会把它同时用于 Release 正文和更新清单的 `notes`。
3. 真机验证通过后，把代码和版本提交到 `main`。

## 2. 本地验证（推荐在打 tag 前跑）

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleRelease
python scripts\check_apk_version.py --all
```

通过标准：

- `BUILD SUCCESSFUL`；205+ 个单测 0 失败；lint 0 error / 0 warning。
- `--all` 对三个 ABI 都输出 `version ok`，且版本等于 `version.properties`。

如果本机有签名配置（`~/.gradle/gradle.properties` 或命令行 `-P`），`assembleRelease`
产出的就是签名包；否则 Gradle 会产出未签名包，**不要发布未签名包**。

## 3. 生成更新清单（可与 CI 同源）

```powershell
.\scripts\write-update-manifest.ps1 -Notes "这一版的变化"
```

产物：

- `app/release/album-update.json`
- `app/release/SHA256SUMS`

清单字段：

```json
{
  "versionCode": 318,
  "versionName": "1.3.65",
  "downloadUrl": "https://github.com/Tnomlav/Albumiv/releases/tag/v1.3.65",
  "notes": "……",
  "generatedAt": "2026-11-21T00:00:00Z",
  "assets": [
    {"abi": "arm64-v8a", "name": "app-arm64-v8a-release.apk", "size": 0, "sha256": "…", "url": "…"}
  ],
  "sha256": {"arm64-v8a": "…", "armeabi-v7a": "…", "x86_64": "…"}
}
```

`downloadUrl` 指向 **Release 页面**，因为发布物是三个 ABI 分包；应用打开的是浏览器，
由用户在 Release 页面选择对应架构。`assets` 里同时给出每个 APK 的直接地址、大小和
SHA-256，方便手工核对。AppUpdateChecker 只读取前四个字段，额外字段会被忽略。

## 4. 打 tag 并发布

```powershell
git tag -a v1.3.65 -m "Album v1.3.65"
git push origin v1.3.65
```

推送后：

1. Actions 运行 **Release** workflow。
2. 校验 tag 与 `version.properties` 一致；不一致直接失败。
3. 解码 keystore，构建 `assembleRelease`，跑单测/lint。
4. 校验三个 ABI APK 的版本。
5. 生成 `album-update.json` 与 `SHA256SUMS`。
6. 创建 Release，上传：
   - `app-arm64-v8a-release.apk`
   - `app-armeabi-v7a-release.apk`
   - `app-x86_64-release.apk`
   - `album-update.json`
   - `SHA256SUMS`
7. `mapping.txt` 作为 workflow artifact 上传，不放进公开 Release。

**如果 secrets 未配置，workflow 会在构建前失败，不会发布未签名包。**
修好 secrets 后重新运行失败 job 或重推 tag。

## 5. 发布后自检

```powershell
curl -s https://api.github.com/repos/Tnomlav/Albumiv/releases/latest
curl.exe -s -o NUL -w "%{http_code}" https://github.com/Tnomlav/Albumiv/releases/latest/download/album-update.json
```

通过标准：

- 最新 Release 的 `tag_name` 是 `v1.3.65`。
- 更新清单返回 `200`，`versionCode` / `versionName` 与 `version.properties` 一致。
- 安装上一个版本，在设置页点「应用版本」→「检查更新」，能看到 1.3.65 并打开
  Release 页面；不是 404，也不是「更新源落后」。
- Release 资产包含三个 ABI APK、`album-update.json`、`SHA256SUMS`。

## 6. 发布后推进版本

发布成功后，再改 `version.properties` 准备下一个 patch：

```powershell
.\gradlew.bat :app:bumpVersion
git add version.properties
git commit -m "chore: prepare v1.3.66"
```

构建不会自动加一；`bumpVersion` 只在发布完成后手动执行。这样装上的 APK、仓库、
Release 资产和更新清单始终是同一个版本。

## 手工兜底（只有 Actions 不可用时）

1. 本地跑第 2、3 步，确认 `app/build/outputs/apk/release/` 下三个包存在且已签名。
2. 在 GitHub 网页创建 `v<versionName>` Release，上传第 4 步列出的五个资产。
3. 用第 5 步自检。
4. 不要把 `app/release/` 里的旧 APK 或旧清单当发布物；它只是本地验证目录，已被
   `app/.gitignore` 排除。
