# QueueWhitelist

Spigot 1.17 队列白名单插件。当服务器在线人数达到指定阈值后，新玩家必须持有未过期的限时白名单才能进入。

## 功能

- 玩家加入时自动检测当前在线人数
- 达到阈值时要求有效白名单，否则踢出
- 白名单支持限时（分钟/小时/天）和永久
- OP 和权限玩家可配置绕过
- 过期白名单自动清理
- SQLite 本地存储，无需外部数据库
- 命令自动补全，中文日志

## 环境要求

| 组件 | 版本 |
|------|------|
| Java | 17+ |
| Spigot | 1.17+ |
| Gradle | 9.2（构建用） |

## 安装

1. 从 [Releases](../../releases) 下载最新 jar
2. 放入服务器 `plugins/` 目录
3. 重启服务器或 `reload`

## 命令

| 命令 | 说明 | 示例 |
|------|------|------|
| `/queuewl add <玩家> <时长>` | 添加限时白名单 | `/queuewl add Steve 7d` |
| `/queuewl remove <玩家>` | 移除白名单 | `/queuewl remove Steve` |
| `/queuewl check <玩家>` | 查看白名单状态 | `/queuewl check Steve` |
| `/queuewl list [页码]` | 查看白名单列表 | `/queuewl list 2` |
| `/queuewl threshold [人数]` | 查看/设置触发人数 | `/queuewl threshold 100` |
| `/queuewl reload` | 重载配置文件 | |
| `/queuewl cleanup` | 清理所有过期白名单 | |

别名：`/qwl`

### 时长格式

| 格式 | 含义 |
|------|------|
| `30m` | 30 分钟 |
| `1h` | 1 小时 |
| `12h` | 12 小时 |
| `1d` | 1 天 |
| `7d` | 7 天 |
| `forever` | 永久 |

## 权限

| 权限 | 默认 | 说明 |
|------|------|------|
| `queuewhitelist.admin` | OP | 使用所有管理命令 |
| `queuewhitelist.bypass` | OP | 绕过队列白名单检查 |

## 配置

首次启动后在 `plugins/QueueWhitelist/config.yml` 生成配置：

```yaml
# 在线人数达到该值时需要队列白名单，0 = 所有人都需要
threshold: 100

# OP 是否绕过检查
bypass-op: true

# queuewhitelist.bypass 权限玩家是否绕过
bypass-permission: true

# 登录时自动删除过期记录
remove-expired-on-login: true

messages:
  kick: "服务器当前人数已达到 {threshold} 人，你需要有效的队列白名单才能进入。"
  kick-expired: "你的队列白名单已经过期，请重新获取后再进入服务器。"
```

## 构建

```bash
# Linux/macOS
./gradlew build

# Windows
.\gradlew.bat build
```

产物位于 `build/libs/QueueWhitelist-1.0.0.jar`。

## 工作原理

1. 玩家尝试加入服务器
2. 插件读取当前在线人数，与数据库阈值对比
3. 未达到阈值 → 正常放行
4. 达到阈值 → 检查该玩家是否有未过期的白名单
5. 有白名单 → 放行；无/过期 → 踢出并提示

## License

MIT
