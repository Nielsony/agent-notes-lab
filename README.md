# Agent Notes Lab

通过开发一个个人学习笔记助手，学习 Java Agent 开发。

当前处于第一阶段 T1：命令行单次模型问答。搜索笔记、读取笔记和工具调用循环是后续任务，尚未实现。

## 准备环境

- JDK 17 或以上版本。
- Maven 3.8.8 或以上版本。
- Ollama，以及已下载的 `qwen3:4b` 本地模型。

当前程序固定请求本机 `http://127.0.0.1:11434/api/chat`，默认模型为 `qwen3:4b`，不读取 API 密钥、不发送认证头。模型在本机推理，没有云端 API 账单，但会占用磁盘、内存和计算资源；首次安装和下载需要网络，运行速度取决于硬件和模型。

### 安装并启动本地模型

macOS 已安装 Homebrew 时，安装 [Ollama](https://formulae.brew.sh/formula/ollama)：

```sh
brew install ollama
```

在一个独立终端启动服务，并保持该终端运行：

```sh
OLLAMA_NO_CLOUD=1 ollama serve
```

此变量关闭这个服务进程的 Ollama 云功能；服务默认只绑定 `127.0.0.1:11434`。如果已运行 Ollama 应用或其他服务进程，先退出它再按上述命令启动，避免端口占用，也确保设置生效。参见 [Ollama 服务配置](https://docs.ollama.com/faq)。

在另一个终端下载模型并检查列表：

```sh
ollama pull qwen3:4b
ollama ls
```

Windows / Linux 可按 [Ollama 官方安装说明](https://docs.ollama.com/quickstart)安装，再将 `OLLAMA_NO_CLOUD=1` 设置到服务环境并重启服务。各平台的环境变量设置方法见上述服务配置文档。

本项目的问答请求直连本机服务，不经过 Java 代理，不需要此前调用云 API 时使用的 `-Dhttps.proxyHost`、`-Dhttps.proxyPort` 参数。本地推理不等于整个安装、下载或运行环境完全离线；共享诊断时仍应检查个人内容。

## 构建与验证

在项目根目录运行：

```sh
mvn -q verify dependency:copy-dependencies -DincludeScope=runtime
```

测试使用本地 HTTP 服务验证请求和响应处理，不调用真实模型，也不要求运行 Ollama。
测试通过不能证明真实模型问答成功。

## 运行一次问答

macOS / Linux：

```sh
java -cp "target/classes:target/dependency/*" lab.Main
```

Windows PowerShell：

```powershell
java -cp "target/classes;target/dependency/*" lab.Main
```

按提示输入一行问题并回车，无需登录或输入密钥。程序以 UTF-8 读取输入，每次执行处理一个问题，结束后退出；本步骤没有多轮对话记忆。

也可以通过管道输入一行问题（macOS / Linux）：

```sh
printf '%s\n' '用两句话说明 Java HttpClient 的作用。' | java -cp "target/classes:target/dependency/*" lab.Main
```

若要使用其他已下载的本地模型，可设置模型名称：

```sh
export OLLAMA_MODEL="已下载的模型名称"
java -cp "target/classes:target/dependency/*" lab.Main
```

PowerShell 中对应为 `$env:OLLAMA_MODEL = "已下载的模型名称"`，再运行上面的 Windows 命令。不同模型的能力和资源需求不同；第一阶段先使用默认模型。

练习问题可以是：

> 用两句话说明 Java HttpClient 的作用。

预期过程是“输入问题 → 发送 HTTP 请求 → 解析 JSON → 显示模型回答”。具体回答每次可能不同。
收到真实本地模型的回答后，才算完成 T1 中的模型连接验证；三篇笔记和四个问题仍需单独准备。

## 阅读代码

从 [Main.java](src/main/java/lab/Main.java) 开始，依次观察：

1. `main` 读取一行问题，并根据 `OLLAMA_MODEL` 或默认值决定模型名称。
2. `ask` 用 Jackson 把 `model`、`messages` 等字段序列化为 JSON。`messages` 包含规定回答方式的 `system` 消息和保存问题的 `user` 消息。
3. Java `HttpClient` 向本机 Ollama 发送 POST 请求。`stream: false` 要求返回完整 JSON，使用模型默认的思考设置；`options.num_predict: 2048` 限制生成长度，思考输出也会消耗预算。程序只显示最终的 `message.content`。
4. 检查 HTTP 状态、`done: true` 和 `done_reason: stop`，再读取非空的 `message.content`；消息角色必须是 `assistant`。

连接超时为 10 秒，请求超时为 120 秒；程序不自动重试。原始响应 JSON 不输出到终端。

接口字段参考 [Ollama Chat API](https://docs.ollama.com/api/chat)，默认模型参见 [Qwen3:4b](https://ollama.com/library/qwen3:4b)。

## 排查失败

- `问题不能为空`：输入一行非空问题；通过管道输入时也要提供内容。
- 连接失败：检查启动 Ollama 的终端是否仍在运行，以及本机 `11434` 端口是否可用。
- `address already in use`：已经有服务占用端口；先确认并退出既有 Ollama 进程，再按上述方式启动，不要重复启动多个服务。
- HTTP 404：用 `ollama ls` 核对模型名；缺失时运行 `ollama pull qwen3:4b`，自定义模型需下载对应名称。
- HTTP 5xx：查看 Ollama 服务终端中的错误和可用内存，确认原因后再重试。
- 超时或响应格式错误：检查本机服务和模型状态，以及所用 Ollama 版本是否支持上述 Chat API 参数。
- 响应未完成、停止原因异常或没有文本：本次问答不算成功；检查模型兼容性和生成长度限制，输出被截断也不算完整回答。

共享诊断时保留错误类别和必要的脱敏信息，不上传凭据或个人资料。

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
远程仓库为 [Nielsony/agent-notes-lab](https://github.com/Nielsony/agent-notes-lab)。提交前确认 `origin` 和当前分支，再推送需要发布的提交。

## 贡献

提交前运行上面的构建与验证命令。问题报告说明 Java 版本、复现步骤和脱敏错误信息；功能改动同时更新相关测试和说明。

## 许可证

[MIT](LICENSE)
