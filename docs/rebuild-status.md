# 重建状态（Phase 0–6）

这份文档记录 2026-11 这次分阶段重建：每个阶段做了什么、提交号、验证方式，以及
**留到真机统一验证**的清单。原则是 strangler 模式——新结构包住旧实现，逐块替换，
每一阶段都能单独编译、提交、回退，行为改动尽量为零。

## 阶段总览

| 阶段 | 提交 | 内容 | 验证 |
| --- | --- | --- | --- |
| Phase 0 工程基线 | `2656cb3` | 版本号单一来源：删除构建时自增；`bumpVersion`/`printVersion`；`scripts/check_apk_version.py` + CI 步骤 | 构建前后版本号不变；checker 通过 |
| Phase 1 加载状态机 | `8f9189e` | `LoadState(initialScanComplete/active/queued)` + `RefreshCoordinator`；删 3 个补丁参数 | 单测 `LoadStateTest`、lint、release |
| Phase 2 数据源与增量刷新 | `260b79c` | `MediaSource` 接缝；ContentObserver 增量（差量 + aliveKeys 校对，失败回退全量）；快照 schemaVersion/savedAt/原子写；列表复用 | `IncrementalMergeTest`、lint、release |
| Phase 3 操作事务化 | `278d295` | `MediaCapabilities`/`storageKindOf`/`planTransfer`；MediaStore 写事务合并为 `insertIntoMediaStore` | `MediaCapabilitiesTest`、lint、release |
| Phase 4 外壳拆分（一） | `4745ee9` | Pixiv 页加载状态 → `PixivPageController` | 构建、单测、lint |
| Phase 4 外壳拆分（二） | `b302883` | 底栏 + 选择条 + 拖拽状态 → `AppBottomBar` | 同上 |
| Phase 5 壁纸队列 | `3ed0323` | `WallpaperQueueController`：单一修改入口 + 必落盘 | 同上 |
| Phase 5 编辑器 | `18e3f56` | `EditorHistory<T>`：撤销/重做规则、60 步上限集中 | `EditorHistoryTest` |
| Phase 6 快照测试 | `5152e16` | 原子写与 schema 版本判断可测；README 增加文档入口 | `MediaSnapshotStoreFileTest` |
| Phase 6 传输文案 |  | `TransferSummary`：toast 文案独立，顺带修英文模式漏翻 | `TransferSummaryTest` |

## 架构上的变化

- **加载**：`MediaLibraryState.loadState` 是唯一状态；UI 只读 `isUserPull` / `initialScanComplete`。
  刷新请求统一经过 `RefreshCoordinator.request(source)`，来源只有 Startup / UserPull /
  Observer / Operation 四种。
- **数据**：MediaStore 读写走 `MediaSource`；后台刷新用差量，合并结果与存活集合不一致时
  自动全量。快照带 schema 版本，未来升级不会误读。
- **操作**：传输能力由 `MediaCapabilities` 描述，`planTransfer` 给出策略顺序；MediaStore
  写入只有一份事务实现（临时名 → IS_PENDING → 复制 → 发布 → 删旧 → 改名）。
- **外壳**：Pixiv 页、底栏、壁纸队列各自有 controller；`AlbumApp` 从 5016 行降到约 4670 行。

## 取舍与遗留（明确不做或未做）

- **AlbumApp 没有拆到 1500 行**：完整 ViewModel 拆分需要多轮并配合真机回归，当前按内聚
  单元迁移（Pixiv/底栏/壁纸），剩余的大块是传输/删除编排与文件选择器接线。
- **缩略图没有单独“服务化”**：`ThumbnailRepository` 本来就是进程级单例，再包一层没有收益；
  真正缺的是配额与命中率的可观测性，留到需要时再加。
- **播放器**：之前已用 `SimpleBasePlayer` 把 LibVLC 接入 Media3，本次未动；后台服务职责与
  设备矩阵仍待真机验证。
- **Pixiv 标签索引**：构建流程依赖前台服务与对话框状态，继续留在 UI 层；只把标签数据
  归到 `PixivPageController`。
- **壁纸服务**：静态/动态壁纸服务读取的是既有偏好与队列文件，本次只收敛 UI 侧队列状态，
  服务侧协议未改。

## 真机验证清单（留到有设备时统一做）

1. **启动**：冷启动第一帧不白屏（骨架屏），媒体权限被撤销后回到应用能重新提示。
2. **下拉刷新**：圆环连续转动；松手后刷新完成即复位，不再停在下拉状态；连续两次下拉正常。
3. **后台刷新**：在其他应用里新增/删除图片，回到 Album 时列表自动更新且**没有**下拉圈；
   删除后该图不再出现在相册/时间轴。
4. **增量刷新**（`incremental_refresh` 默认开）：新增一张图 → 只出现一次增量更新；删除 →
   立即消失；把设置关掉后行为退回全量。
5. **Pixiv 页**：下拉强制重走目录；页面刷新后新增/删除的文件夹以当前状态显示；切页离开
   Pixiv 时正在跑的 walk 能取消。
6. **传输**：移动/复制到 MediaStore 目录、SAF 目录、物理目录各一次；覆盖同名文件后目标
   完整、旧文件消失；传输中取消后目标不留半成品。
7. **回收站**：删除进回收站、还原、永久删除；空间不足时提示不吞文件。
8. **壁纸**：从壁纸页加入队列、应用静态/动态壁纸、重启后队列与已应用状态一致。
9. **编辑器**：撤销/重做 10 步以上、60 步上限、导出后文件可读。
10. **底栏**：长按拖拽换位、重启后顺序保持、点当前页签先回顶再刷新。

验证命令：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
python scripts\check_apk_version.py app\build\outputs\apk\release\app-arm64-v8a-release.apk
```
