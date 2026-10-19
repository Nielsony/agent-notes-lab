# Agent Notes Lab

通过开发一个个人学习笔记助手，学习 Java Agent 开发。

当前处于第一阶段 T1：命令行单次模型问答。搜索笔记、读取笔记和工具调用循环是后续任务，尚未实现。

## 准备环境

- JDK 17 或以上版本。
- Maven 3.8.8 或以上版本。
- 能访问 OpenAI Responses API 的网络和账户，以及账户可用的 API 密钥和模型。

当前程序固定请求 `https://api.openai.com/v1/responses`，默认模型为 `gpt-4.1-mini`。模型是否可用取决于账户。
其他模型服务的协议、地址和认证方式需要在确认后适配；不能直接把其他服务的密钥填进此程序。

## 构建与验证

在项目根目录运行：

```sh
mvn -q verify dependency:copy-dependencies -DincludeScope=runtime
```

测试使用本地 HTTP 服务验证请求和响应处理，不调用真实模型、不需要 API 密钥。
测试通过不能证明真实服务连接成功。

## 运行一次问答

macOS / Linux：

```sh
java -cp "target/classes:target/dependency/*" lab.Main
```

Windows PowerShell：

```powershell
java -cp "target/classes;target/dependency/*" lab.Main
```

在交互式终端中按提示输入问题，再输入 OpenAI API 密钥。密钥输入不显示，也不会被程序写入文件。
程序每次执行处理一个问题，结束后退出；本步骤没有多轮对话记忆。

若要使用账户可用的其他 OpenAI 模型，可设置非敏感的模型名称：

```sh
export OPENAI_MODEL="账户可用的模型名称"
java -cp "target/classes:target/dependency/*" lab.Main
```

PowerShell 中对应为 `$env:OPENAI_MODEL = "账户可用的模型名称"`，再运行上面的 Windows 命令。

练习问题可以是：

> 用两句话说明 Java HttpClient 的作用。

预期过程是“输入问题 → 发送 HTTP 请求 → 解析 JSON → 显示模型回答”。具体回答每次可能不同。
收到真实服务的回答后，才算完成 T1 中的模型连接验证。

## 阅读代码

从 [Main.java](src/main/java/lab/Main.java) 开始，依次观察：

1. `main` 读取问题、隐藏输入密钥，并决定模型名称。
2. `ask` 用 Jackson 把 `model`、`instructions` 和 `input` 等字段序列化为 JSON。
3. Java `HttpClient` 发送带认证头的 POST 请求。
4. 检查 HTTP 状态与响应完成状态，遍历 `output` 中的消息并收集 `output_text`。

连接超时为 10 秒，请求超时为 60 秒；程序不自动重试。
请求带 `store: false`，用于关闭 Responses 的响应存储；它不等于对整个服务的数据保留政策作出保证。
响应原始 JSON 和认证头不输出到终端，避免错误响应回显敏感内容。

## 排查失败

- 没有交互式终端：按运行命令在终端启动，密钥输入需要终端支持。
- HTTP 401 / 403：检查密钥所属服务、密钥有效性和账户权限。
- HTTP 404：检查账户是否可用当前模型。
- HTTP 429：检查账户配额或限流状态，确认原因后再重试。
- HTTP 5xx：检查服务状态，不要连续盲目重试。
- 网络或响应格式错误：检查网络可达性、超时以及接口协议是否匹配。
- 响应未完成或没有文本：本次问答不算成功，检查模型适配和输出限制。

共享诊断时保留错误类别和必要的脱敏信息，不上传密钥、认证头或个人资料。

## 学习路线

- T1：三篇以上短笔记、三个能回答的问题、一个资料不足的问题，以及真实命令行模型问答。
- T2：实现 `search_notes` 和 `read_note` 两个只读工具。
- T3：连接模型选择工具、程序执行工具、结果回传的调用循环。
- T4：验证单篇问答、综合两篇、资料不足、读取失败、越界读取和调用超限。

使用 [学习导师提示词](docs/mentor-prompt.md) 带学。完成标准和学习记录模板在其中定义。
第一阶段暂不引入向量数据库、Web 界面或多 Agent。

## Git 与学习记录

- 默认分支为 `main`，每个可验证的学习任务形成一个清晰的提交。
- 公共内容包括代码、示例、测试和教学说明。
- 私人学习记录使用 `.learning/progress.md`，整个 `.learning/` 目录被 Git 忽略。
- `target/`、IDE 配置、`.env` 和日志文件也被忽略。
- 不把凭据或真实个人笔记加入提交；示例资料使用可公开的内容。

本地初始化命令为 `git init -b main`；提交前先查看 `git status --short` 和 `git diff --cached`。
远程仓库创建后，再将确定的 GitHub 地址配置为 `origin` 并推送。

## 贡献

提交前运行上面的构建与验证命令。问题报告说明 Java 版本、复现步骤和脱敏错误信息；功能改动同时更新相关测试和说明。

## 许可证

[MIT](LICENSE)

接口字段与响应结构参考 [OpenAI 文本生成文档](https://developers.openai.com/api/docs/guides/text)。
