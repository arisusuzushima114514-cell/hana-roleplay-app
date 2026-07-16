# Changelog

## v1.7.2 (2026-07-16)

### 新功能
- 场景捕捉生图：角色卡对话右上角新增「场景捕捉（测试）」按钮，自动提取角色外貌与当前对话场景，生成 POV 视角提示词，生图结果直接嵌入聊天对话流中显示，带加载动画和进度提示
- 模型联网指示灯：模型选择器新增绿色/灰色圆点 + "联网"/"离线"标签

### UI 优化
- 角色卡聊天界面布局重构：将「角色模型设定」从底部移至顶部 FlowRow，回复栏简化为紧凑单行（输入框 + 发送按钮），圆角 30dp→24dp、阴影 10dp→6dp、padding 缩小
- Persona 编辑器字数限制解除：移除 2000 字上限

### Bug 修复
- Persona 优先级修复：Persona 预设不再被模型版本声明覆盖，确认身份时不再出现"我是 DeepSeek"
- 角色卡自动滚动：进入角色卡对话时自动滚动到最新消息（底部）
- 场景捕捉重复点击防护：生成中按钮禁用，防止触发多个并行生图请求
- 切换角色清除生图残留：切换角色卡时自动清空上一个角色的场景捕捉结果
- 内心想法消失修复：系统提示词从软性「需要内心时」改为强制「每轮回复末尾必须」
- 角色参数设置保存修复：温度、Top-P、Max Tokens、上下文轮数等参数正确持久化，不再自动返回默认值
- Persona 沉浸过度修复：优化 Persona 系统提示词，角色性格只影响语气而非内容，即使傲娇/吃醋/高冷也能完整回答问题

### 技术优化
- `extractThinking` 正则改为行锚点匹配，防止误匹配正文中的 thinking/response 单词
- 回复栏动画改用 `graphicsLayer { scaleX/scaleY }` 替代 `Modifier.scale()`

---

## Unreleased

- Added character card JSON import/export
- Added PNG character card import/export support
- Added local image selection for character avatar / cover usage
- Added native web-search capability badges for models/providers
- Reduced token-heavy prompt sections for lower running cost
- Prepared a GitHub-safe project copy for public release
