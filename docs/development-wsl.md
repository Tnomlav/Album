# 在 WSL 里开发与构建 Album

这份仓库会同时被 Windows（Android Studio / `gradlew.bat`）和 WSL（`/mnt/c/Album`）打开。
WSL 侧的 JDK、Android SDK 和 adb 已经装好（最早是给 Zippest 配的），所以 Album 在 WSL 里
也能构建、跑单测、装到真机；Windows 侧的工作方式不受影响。

## 工具链在哪

| 用途 | 位置 | 说明 |
| --- | --- | --- |
| JDK 17 | `~/.local/java/jdk-17.0.21+9` | 编译用 |
| JDK 21 | `~/.local/java/jdk-21.0.13+9` | **Gradle 守护进程要求 21**（`gradle/gradle-daemon-jvm.properties` 里 `toolchainVersion=21`），缺了会去 foojay 下载，网络不通就直接失败 |
| Android SDK | `~/Android/Sdk` | `platforms/android-37.0`、`build-tools/36.0.0`、`platform-tools` 都在 |
| adb | `~/Android/Sdk/platform-tools/adb` | 不在 `PATH` 上，按下面的命令显式加进去 |
| Gradle | `~/.gradle` | 9.5 已由 wrapper 缓存，不用再下载 |

`~/.gradle/gradle.properties` 里登记了 `org.gradle.java.installations.paths`，Gradle 才能
发现上面两个 JDK。

## 构建

`local.properties` 不用改。AGP 9 先读 `ANDROID_HOME`，读不到才看 `local.properties` 的
`sdk.dir`：只留 Windows 路径时会报 `SDK location not found`，补上 `ANDROID_HOME` 就通过
（已在本机实测）。所以两边共用同一个工作区时，Windows 继续用 `local.properties`，WSL 只用
环境变量：

```bash
export JAVA_HOME="$HOME/.local/java/jdk-21.0.13+9"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

cd /mnt/c/Album
./gradlew testDebugUnitTest lintDebug :app:assembleDebug
```

- 仓库在 `/mnt/c`（drvfs）上，比 Windows 本机和 ext4 慢。只想快一点验证时，可以用
  `git worktree add /tmp/album-build HEAD` 把源码放到 ext4 上构建，产物照样能装到手机。
- 不要和 Android Studio 的构建同时跑：`app/build/` 和 `.gradle/` 是同一份目录。

## 真机

WSL 看不到 USB 设备（没有 `/dev/bus/usb`）。本机 WSL 是 **mirrored 网络**，Windows 的
adb server 监听在共享的 `127.0.0.1:5037`，所以不用 usbipd，也不用改 adb bridge：

```powershell
# Windows：手机 USB 连上后，先让 adb server 起来
adb devices
```

```bash
# WSL：连同一个回环，别让 Linux 版 adb 自己再起一个
adb -H 127.0.0.1 devices
# 或者
export ADB_SERVER_SOCKET=tcp:127.0.0.1:5037
adb devices
```

- **不要在 WSL 里 `adb kill-server`**：回环是共享的，这条命令会把 Windows 的 server 一起
  杀掉，之后 WSL 侧只剩 `Connection refused`。
- Windows 侧 server 没起来时，WSL 的 adb 会尝试自己启动并失败（`ADB server didn't ACK`），
  回 Windows 跑一次 `adb devices` 即可。
- 无线调试也可以：手机开「无线调试」后 `adb mdns services` 能看到服务，端口每次重开都会变，
  不要硬编码；IP 要用局域网地址，不是热点/VPN 网段。
- 装包与冒烟：

  ```bash
  adb -H 127.0.0.1 install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
  python3 scripts/device-smoke.py --apk app/build/outputs/apk/debug/app-arm64-v8a-debug.apk --workdir /tmp
  ```

  `device-smoke.py` 自己会去 `~/Android/Sdk/platform-tools/adb` 找 adb（见该文件
  `adb_path()`），也可以显式传 `--serial`。

## 换行符

仓库用 [`.gitattributes`](../.gitattributes) 固定索引为 LF（`*.bat`/`*.ps1` 是 CRLF）。
WSL 里不要再设 `core.autocrlf=true`，否则状态会显示整棵树被改过。
