# 铁境竞技场 / Iron Arena

基于原始 Java 文字格斗 demo 完善的 Swing 桌面回合制游戏。保留控制台入口，桌面版与控制台版共用战斗引擎和局内成长逻辑。

## 启动

Windows 下双击根目录 `start.bat`，或者运行：

```powershell
.\run.ps1
```

需要 JDK 17 或更高版本。脚本依次查找 `JAVA_HOME`、`.tools/jdk` 和 PATH 中的 `javac`，自动编译并生成 `build/fightinggame.jar`，然后打开桌面窗口。当前机器已将 `.tools/jdk` 关联到已有的 PyCharm Java 环境；这个本机关联不会提交到 Git，换电脑后请自行配置 JDK。

```powershell
.\run.ps1 -Console    # 控制台版
.\run.ps1 -Test       # 战斗、成长、存储、输入回归测试
.\run.ps1 -GuiTest    # 打开真实窗口，测试交互并生成截图
.\run.ps1 -BuildOnly  # 只编译和打包
```

如果 PowerShell 阻止脚本执行，可使用 `start.bat`，或单次执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run.ps1
```

已编译的 JAR 也可以在带图形桌面的 Java 17+ 环境中运行：

```text
java -jar build/fightinggame.jar
java -jar build/fightinggame.jar --console
java -Dfightinggame.dataDir=D:/arena-data -jar build/fightinggame.jar
```

在 IntelliJ IDEA 中打开 `fightinggame`，选择 JDK 17+，运行 `com.itheima.App` 即可。建议将运行配置的工作目录设置为仓库根目录，使存档位置与脚本一致。不要运行仓库原有 `out/production` 中的旧 `.class` 文件。

## 玩法

- **十关挑战**：连续击败十个对手，第十关为守关者。
- **无尽试炼**：保留原 demo 的连续挑战方式，敌人随胜场成长。
- **属性分配**：共20点，基础生命100、攻击10、防御0；每点分别增加10生命、2攻击或1防御。可选均衡、猛攻或自定义，剩余点数自动投入防御。
- **局内成长**：每3胜生命上限与当前生命增加30、攻击增加5、防御增加3，补充1瓶药水，上限3瓶。非最终关胜利后随机恢复20-40生命，提示显示实际回复量。
- **结算**：战斗中可撤退，胜利后可继续或结束；失败或通关后可以再来一局。正常关闭窗口会询问是否结算当前挑战。

| 行动 | 规则 |
| --- | --- |
| 普通攻击 | 攻击减防御，最低1点伤害 |
| 强力一击 | 消耗10生命，180%攻击力减防御；当前生命必须大于10 |
| 生命汲取 | 120%攻击力减防御，回复实际伤害的50%，向下取整且至少1点；使用后间隔2个有效回合 |
| 防御 | 下一次命中伤害减半，最低1点；多段攻击只抵挡第一段，不叠加 |
| 治疗药水 | 回复最多50生命；满血或没有药水时不能使用；使用会消耗回合 |

敌人保留战士、刺客、坦克和法师四种类型，分别使用猛击、两段攻击、防御姿态和火球术。敌方每回合有50%概率使用技能。敌人被击败后不会继续反击。技能冷却和防御状态会在新一场战斗时重置。

## 账号与存档

- 可直接以游客身份游玩，游客战绩不保存。
- 用户名为3-16位字母数字，至少包含一个字母，区分大小写；密码为6-64位字母数字，必须同时包含两者。
- 注册账号后保存挑战局数、累计胜场、最高连胜和十关通关次数；游戏内可查看本地战绩榜。
- 存档默认位于工作目录的 `data/accounts.properties`。密码使用独立随机盐与 PBKDF2-HMAC-SHA256 摘要保存，不写入明文密码。
- 保存先写临时文件，再替换目标文件；文件系统支持时使用原子替换。存档损坏会报告错误并保留原文件；同一数据目录限制为一个运行实例。
- 当前保存的是账号与已结算战绩，**不支持恢复未完成的一局**。强制结束进程、断电或直接关闭控制台，可能丢失本局战绩。
- 这是离线单机存档，不提供联网认证、找回密码或防作弊服务。

## 代码结构

```text
fightinggame/src/com/itheima/
  App.java                  应用入口、存档目录与实例锁
  doman/                    角色、敌人、玩家与用户模型
  game/Battle.java          单场战斗、行动合法性、冷却与敌方行动
  game/GameSession.java     挑战状态、胜场、成长、通关与结算
  game/Encounters.java      敌人生成与难度成长
  storage/                  密码摘要、本地账号和战绩存储
  ui/GameFrame.java         Swing 桌面界面与账号对话框
  ui/ArenaPanel.java        原创像素角色、竞技场、血条与待机动画
  ui/ConsoleInput.java      可重试输入与 EOF 处理
  ui/Login.java             控制台账号菜单
  ui/FightingGame.java      控制台游戏流程
fightinggame/test/com/itheima/
  GameTests.java            核心回归测试
  GuiSmokeTest.java         桌面交互与布局检查
```

保留原来的 `doman` 包名，避免无关目录迁移。项目没有第三方运行时依赖，也不需要联网加载美术资源。角色与场景通过 Java2D 绘制。

原始代码目的、缺陷和改动说明见 [项目分析](docs/PROJECT_ANALYSIS.md)。
