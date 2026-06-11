# Screenshot (长截屏工具)

一款专为 Android (特别是三星 S24 等设备) 设计的自动向下长截屏软件。

## 项目要求与规范

- **文档同步**：每次程序修改后，必须更新本 `README.md` 说明文档。
- **代码托管**：每次修改完成后，上传更新项目到 GitHub 仓库。
  - GitHub 地址：[https://github.com/grenvill-create/Screenshot.git](https://github.com/grenvill-create/Screenshot.git)

## 核心功能规划

1. **悬浮窗控制**：提供在任意界面上启动和停止长截屏的悬浮按钮。
2. **屏幕捕捉**：使用 Android 系统的屏幕共享/录制接口捕获画面。
3. **自动滚动**：通过无障碍服务自动向下滚动列表或页面。
4. **图像拼接**：将捕获的多张截图通过算法（重叠区域匹配）无缝拼接成一张长图。
5. **相册保存**：将最终生成的长图保存至手机系统相册。

## 更新日志

### 2026-06-11
- **初始化项目结构**：创建了标准 Android Gradle 项目骨架。
- **权限与 UI 配置**：完成了 `MainActivity`，配置了悬浮窗权限、无障碍权限和录屏权限的动态申请。
- **核心逻辑与联动循环建立**：
  - `CaptureService` 通过 `ImageReader` 捕获当前屏幕高清帧。
  - 截帧完成后发送 `ACTION_PERFORM_SCROLL` 广播触发无障碍服务执行上滑手势（翻页）。
  - 手势完成后发送 `ACTION_SCROLL_FINISHED` 广播触发下一次截帧。
- **纯 Java 图像拼接引擎 (`ImageStitcher`)**：
  - 实现了基于纯 Java 的像素色差行匹配算法。
  - 自动规避顶部状态栏与底部导航栏。
  - 扫描新捕获帧的重叠区域，计算出误差最小的拼接偏移量，生成一张不断变大的高清长图。
- **存储模块 (`BitmapUtils`)**：
  - 用户点击停止后，自动调用 `MediaStore API` 将最终长图保存至手机系统相册（Pictures/Screenshots 目录下）。
