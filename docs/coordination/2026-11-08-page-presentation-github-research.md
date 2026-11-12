# 页面展示方式 GitHub 调研

日期：2026-11-08。状态：调研建议，未实施。用户澄清目标为寻找底部弹出页面之外的展示方式；不是仅解决下拉关闭手势冲突。与UIX-CLOSE-v1证据收尾分开，不据此重启旧修复。

## 已检查的项目现状

`ReaderSheetHost.kt` 以一个ModalBottomSheet容器承载TOC、RULES、NOTES、AI_ASSIST、AI_EXPLAIN、INSPIRATION、SETTINGS、DICTIONARY、THEME、PROGRESS、SEARCH、BOOK_INFO共12种内容。这里的12是枚举分支数，不代表12个问题均已复现。`GlassDialogs.kt`为其他页面提供底部弹层包装。`ReaderSettingsCommon.kt`已有设置子页枚举，因此可以先复用现有分类与业务回调评估展示迁移，不必重写业务层。

## GitHub 一手代码来源与观察

1. Mihon `SettingsMainScreen.kt`：Scaffold与固定顶栏、返回和设置搜索；分类列表进入对应设置Screen；窄屏push，twoPane时replaceAll。
   https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsMainScreen.kt
   借鉴：设置目录→分类独立页；手机全屏、大屏再考虑双栏。不是引入其Voyager导航依赖。
2. ReadYou `SettingsPage.kt`：RYScaffold的返回入口与LazyColumn，颜色风格、交互、语言等各自独立navigate回调。
   https://github.com/Ashinch/ReadYou/blob/main/app/src/main/java/me/ash/reader/ui/page/settings/SettingsPage.kt
   借鉴：按用户任务分类页面，避免所有选项塞进一个长面板。
3. Android Now in Android README：可见For You、Interests与Topic detail的页面划分及自适应布局说明；本轮未取得其list-detail导航实现文件，不据README断言具体实现。
   https://github.com/android/nowinandroid
   借鉴限于列表/主题详情分离的产品结构。

以上为读取代码/项目资料所得，未安装运行外部应用，未做三方库兼容或性能验证。远端main可变，实施时需钉所参考提交。

## 适合本项目的展示建议（Root产品建议，非声称外部项目原样如此）

| 内容 | 建议展示 | 关键理由与约束 |
|---|---|---|
| 阅读器完整设置、规则管理 | 独立全屏页，分类进入子页 | 长期操作与多层内容需要稳定返回层级；保留书内/全局设置范围 |
| 搜索结果、笔记列表、导入历史 | 全屏列表→详情/回源 | 留出列表、筛选与键盘空间；返回恢复列表滚动和阅读位置 |
| 灵感编辑、长AI结果 | 全屏编辑/详情页 | 固定保存/取消或返回入口；保留草稿与真实保存结果，Back与键盘分层 |
| 目录、书签 | 手机优先全屏标签页；侧边目录作为对照原型 | 全屏宽度适合长章节名；侧栏须实测宽度和阅读上下文价值，非默认全改 |
| 字号、亮度、行距 | 阅读器内紧凑控制区，提供更多设置入口 | 需要保留正文预览；全屏适合完整配置，短控件留在阅读上下文 |
| 排序、少数操作 | 锚定菜单或短面板 | 无需变成独立全屏页，不全局替换所有弹层 |
| 大屏 | 后续可选分类/列表与详情双栏 | 当前先做好手机，不为未来大屏先扩展工程 |

## 推荐顺序与验证

先设计完整设置页、目录页、灵感编辑页三种代表性方案，再选择一个垂直切片实施。用户本轮要求调研，不执行代码迁移、不派发外部任务。展示改造必须另立合同，限定路由、文件所有权、设备/构建owner与验收输入。

验证不只看打开动画：从阅读器进入→操作→Back返回应保持书籍/章节/位置与已有设置语义；系统Back先处理键盘/子页，再回阅读器；不重复压栈、不把临时查阅计入正常进度。字体放大、长章节名、横屏和减少动态效果场景需要检查。新页面无障碍复用现有规则，避免再次为视觉包装引入额外语义层。
