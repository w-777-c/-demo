package com.itheima.ui;

import com.itheima.doman.EnemyCharacter;
import com.itheima.doman.HeroCharacter;

import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

public class FightingGame {
    //游戏开始
    public void gameStart(String username) {
        //显示游戏的标题
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("🎮 " + username + "欢迎来到文字格斗游戏 🎮   ");
        System.out.println("╚════════════════════════════════════════╝");

        //2.创建玩家角色（名字+属性分配）
        HeroCharacter player = createPlayerCharacter(username);
        //3.显示创建角色的信息和技能列表
        System.out.println("角色创建成功！");
        System.out.println("🌟 初始属性:  " + player.show());
        System.out.println("🌟 初始技能:  " + player.showSkill());

        //4.创建多个敌人列表
        //name hp atk def skill
        //初级战士 80 15 10 猛击
        //敏捷刺客 60 20 5  快速攻击
        //重装坦克 120 10 20 防御姿态
        //神秘法师 70 25 8   火球术（180%伤害）
        ArrayList<EnemyCharacter> enemyList = new ArrayList<>();
        enemyList.add(new EnemyCharacter("初级战士", 80, 15, 10, "猛击"));
        enemyList.add(new EnemyCharacter("敏捷刺客", 60, 20, 5, "快速攻击"));
        enemyList.add(new EnemyCharacter("重装坦克", 120, 10, 20, "防御姿态"));
        enemyList.add(new EnemyCharacter("神秘法师", 70, 25, 8, "火球术"));

        //5.准备战斗（依次跟多个敌人战斗）
        int count = 1;
        int wins = 0;

        //第一个敌人
        //第二个敌人
        //。。。。
        //循环来进行表示
        while (player.isAlive()) {
            //进入循环，开始准备战斗
            if (wins != 0) {
                //获取到每一个敌人的信息，进行属性点的增加
                for (int i = 0; i < enemyList.size(); i++) {
                    EnemyCharacter c = enemyList.get(i);
                    //每场maxHP + 10
                    c.maxHP = c.maxHP + 10;
                    c.HP = c.maxHP;
                    //Attack + 3
                    c.attack = c.attack + 3;
                    //Defense + 2
                    c.defense = c.defense + 2;
                    //每场战斗之前，如果有减伤的buff，我们需要清空
                    c.defending = false;
                }
            }
            //5.2 随机选择敌人（Random）
            Random r = new Random();
            int index = r.nextInt(enemyList.size());
            EnemyCharacter enemy = enemyList.get(index);
            System.out.println(enemy.show());

            //5.3 开始战斗
            System.out.println("═══════════════════════════════════════");
            //⚔️ 第 1 场战斗开始！对手: 初级战士
            System.out.println("⚔️ 第 " + count + " 场战斗开始！对手: " + enemy.name);

            //跟当前的敌人是第几回合
            int round = 1;
            while (player.isAlive()) {
                //显示双方的状态（生命值）
                System.out.println("-----------------------------------");
                System.out.println("⚔️第 " + round + " 回合");
                //打印敌我双方的血条
                System.out.println(getHeathBar(player.name, player.HP, player.maxHP));
                System.out.println(getHeathBar(enemy.name, enemy.HP, enemy.maxHP));

                //5.4 玩家回合： 选择行动（1.普通攻击 2.强力一击 3.生命汲取 ）
                playerTurn(player, enemy);

                //5.5 判断敌人是否被击败（判断敌方的血量是否为0）
                if (!enemy.isAlive()) {
                    System.out.println("🎉恭喜你，击败了" + enemy.name + "！");
                    wins++;
                    System.out.println("当前已击败" + wins + "个敌人！");
                    break;
                }

                //5.6 敌人回合：敌人对玩家进行普通攻击
                enemyTurn(enemy, player);

                //5.7 判断玩家是否被击败（判断玩家的血量是否为0）
                if (!player.isAlive()) {
                    System.out.println("很遗憾，你被击败了！");
                    System.out.println("游戏结束！");
                    break;
                }

                //如果玩家没有被击败，继续战斗
                round++;
            }//内循环

            //5.8 跟一个敌人的战斗结束之后，玩家胜利（继续战斗）玩家失败（游戏结束）
            if(player.isAlive()){
                //计算玩家要恢复多少点血（20-40）
                int healHP = r.nextInt(21) + 20;
                //恢复血量
                player.heal(healHP);
                //💚 战斗结束！你恢复了 36 点生命值
                //🏆 当前胜场: 1
                System.out.println("💚 战斗结束！你恢复了 " + healHP + " 点生命值");
                System.out.println("🏆 当前胜场: " + wins);
                System.out.println("═══════════════════════════════════════");
            }

            //5.9 每胜利三场，人物的属性就需要增加
            if (player.isAlive() && wins % 3 == 0){
                System.out.println("恭喜你，通过了三场战斗，你的属性将得到提升！");
                //属性提升（HP + 20, ATK + 5, DEF + 3）
                player.HP = player.HP + 20;
                player.attack = player.attack + 5;
                player.defense = player.defense + 3;
                //提示
                System.out.println("最大生命值 + 30， 攻击力 + 5， 防御力 + 3");
                System.out.println("当前属性: " + player.show());
            }

            //5.10询问玩家是否继续
            if (player.isAlive()){
                System.out.println("是否继续游戏？（y/n）");
                Scanner sc = new Scanner(System.in);
                String choose = sc.next();
                if("y".equalsIgnoreCase(choose)){
                    //继续游戏
                    count++;
                    //循环继续执行，开始跟下一个敌人进行战斗
                    continue;
                } else if ("n".equalsIgnoreCase(choose)) {
                    //表示玩家不想继续游戏，跳出循环，整个游戏全部结束
                    break;
                }else {
                    System.out.println("没有这个选项，游戏继续");
                    count++;
                    continue;
                }

            }
        }

        //6.游戏的最终结算
        System.out.println("═══════════════════════════════════════");
        System.out.println("游戏结束");
        System.out.println("你的最终胜场是：" + wins);
        System.out.println("感谢游玩！");
        //停止虚拟机运行
        System.exit(0);
    }



    //定义一个敌我双方的血条打印
    //满血：[████████████████████]
    //半血：[█████████           ]
    //残血：[█                ]

    public String getHeathBar(String name,int HP, int maxHP) {
        //满血状态下，打印20个方块
        int barLength = 20;

        //计算在不同的血量当中，一共打印多少个方块
        int filled = (int)((HP * 1.0 / maxHP) * barLength);

        StringBuilder sb = new StringBuilder();
        sb.append(name).append(": [");
        //利用循环拼接方块和空格
        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                sb.append("█");
            } else {
                sb.append(" ");
            }
        }
        sb.append("]").append(HP).append("/").append(maxHP).append("HP");
        return sb.toString();
    }


    //作用：用来创建一个玩家的角色🌟 初始属性: zhangsan [HP: 100/100, ATK: 10, DEF: 0]
    //参数：用户名
    //返回值：玩家角色对象
    public HeroCharacter createPlayerCharacter(String username) {
        System.out.println("创建您的角色");
        System.out.println("您的角色名为：" + username);

        //属性分配
        int points = 20;

        //提示：
        System.out.println("请分配属性点 (共20点):");
        System.out.println("1. 生命值 (每点+10 HP)");
        System.out.println("2. 攻击力 (每点+2 ATK)");
        System.out.println("3. 防御力 (每点+1 DEF)");

        Scanner sc = new Scanner(System.in);

        //定义数组把要提示的语句存起来
        String[] attributes = {"生命值", "攻击力", "防御力"};
        //定义数组记录三个属性分配的属性点
        int[] values = new int[3];

        //利用一个循环分配属性点
        for (int i = 0; i < attributes.length; i++) {
            System.out.print("分配点数到" + attributes[i] + "（剩余点数：" + points + "）：");
            //input表示当前用户键盘录入的数据（要分配的属性点）
            int input = sc.nextInt();

            //如果要分配的属性点为负数
            if (input < 0) {
                System.out.println("无效输入！默认分配0点");
                input = 0;
            }

            //如果分配的点数超出剩余的点数
            if (input > points) {
                System.out.println("属性点不足！剩余属性点全部分配到" + attributes[i]);
                input = points;
            }
            //计算一下剩余还有多少个属性点
            points = points - input;
            //记录当前属性点数
            values[i] = input;
        }

        //我已经知道了用户要分配的属性点 ————> values【i】

        //创建玩家角色对象
        HeroCharacter player = new HeroCharacter(
                username, //角色的名字
                100+values[0] * 10, //生命值
                10+values[1] * 2, //攻击力
                0+values[2] * 1 //防御力
        );

        //添加玩家的技能
        player.skillList.add("普通攻击");
        player.skillList.add("强力一击");
        player.skillList.add("生命汲取");

        //返回玩家角色对象
        return player;



    }


    //玩家回合： 选择行动（1.普通攻击 2.强力一击 3.生命汲取 ）
    //参数：玩家角色对象，敌人角色对象
    public void playerTurn(HeroCharacter player, EnemyCharacter enemy) {
        System.out.println("===你的回合===");
        System.out.println("1.普通攻击");
        System.out.println("2.强力一击");
        System.out.println("3.生命汲取");
        System.out.println("请选择行动（1-3）：");
        Scanner sc = new Scanner(System.in);
        String choose = sc.next();
        switch (choose) {
            default:
                System.out.println("没有这个操作，默认普通攻击");
            case "1":
                int damage = calculateDamage(player.attack, enemy.defense);
                System.out.println("⚔️ 你对" + enemy.name + " 使用了普通攻击，造成 " + damage + " 点伤害！");
                //扣血的操作
                enemy.takeDamage(damage);
                break;
            case "2":
                if(player.HP > 10){
                    //消耗我方10hp
                    player.takeDamage(10);
                    //计算我方技能给对方造成了多少点伤害
                    int damage2 = calculateDamage((int)(player.attack * 1.8), enemy.defense);
                    System.out.println("💥 消耗10HP，你对"  + enemy.name + " 使用了强力一击，造成 " + damage2 + " 点伤害！");
                    enemy.takeDamage(damage2);
                }else {
                    System.out.println("HP不足，无法使用强力一击");
                }
                break;
            case "3":
                System.out.println("3.生命汲取");
                if(player.HP > 10)  {
                    //消耗我方10hp
                    player.takeDamage(10);
                    //计算我回复的血量
                    Random r = new Random();
                    int healHP = r.nextInt(21); // 0-20
                    //真正的给我方回复血量
                    player.heal(healHP);
                    System.out.println("(*)(*) 消耗10HP，你回复了 " + healHP + " 点血量！");
                }else {
                    System.out.println("HP不足，无法使用生命汲取");
                }
                break;

        }
    }

    //作用：用来计算双方战斗的时候，造成的伤害
    //- 基础伤害公式：伤害 = 攻击力 - 防御力:calculateDamage(我方攻击力，敌人防御力)
    //- 最小伤害：1点
    //- 技能伤害：伤害 = 攻击力 * n% - 防御力：calculateDamage(我方攻击力 * n%,敌人防御力)
    public int calculateDamage(int attack, int defense) {
        //计算伤害
        int damage = attack - defense;
        //如果伤害小于0，那么伤害为0
        if (damage < 1) {
            damage = 1;
        }
        return damage;
    }

    private void enemyTurn(EnemyCharacter enemy, HeroCharacter player) {
        System.out.println("===敌人回合===");

        //计算当前是普通攻击50%
        //还是技能攻击50% --- 猛击 快速攻击 防御姿态 火球术
        //表示：敌人要采取的手段
        String action = "普通攻击";

        //进行几率计算
        Random r = new Random();
        int num = r.nextInt(10);//0 1 2 3 4(普通攻击) 5 6 7 8 9（技能攻击
        if (num >= 5) {
            action = enemy.skill;
        }
        //根据不同的情况，采取不同的攻击手段
        switch (action) {
            case "普通攻击":
                int damage = calculateDamage(enemy.attack, player.defense);
                System.out.println("⚔️ 敌人对你"+ " 使用了普通攻击，造成 " + damage + " 点伤害！");
                player.takeDamage(damage);
                break;
            case "猛击":
                int damage2 = calculateDamage((int)(enemy.attack * 1.5), player.defense);
                System.out.println("💥 敌人对你"+ " 使用了猛击，造成 " + damage2 + " 点伤害！");
                player.takeDamage(damage2);
                break;
            case "快速攻击":
                int damage3 = 0;
                for (int i = 0; i < 2; i++) {
                    int temp = calculateDamage(enemy.attack / 2, player.defense);
                    damage3 += temp;
                }
                System.out.println("💨 敌人对你"+ " 使用了快速攻击，造成 " + damage3 + " 点伤害！");
                player.takeDamage(damage3);
                break;
            case "防御姿态":
                System.out.println("🛡 敌人对你"+ " 使用了防御姿态，提高了防御力！");
                enemy.defending = true;
                break;
            case "火球术":
                int damage4 = calculateDamage((int)(enemy.attack * 1.8), player.defense);
                System.out.println("🔥 敌人对你"+ " 使用了火球术，造成 " + damage4 + " 点伤害！");
                player.takeDamage(damage4);
                break;
        }

    }
}
