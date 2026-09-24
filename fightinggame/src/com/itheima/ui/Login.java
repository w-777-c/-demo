package com.itheima.ui;

import com.itheima.doman.User;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

public class Login {
    //这个方法表示的就是登录注册的主页面（是以控制台的形式进行展示的）
    public void start() {
        System.out.println("游戏的登录页面打开了");

        ArrayList<User> list = new ArrayList<>();

        //ctrl + alt + t 选择对应的语句包裹代码
        while (true) {
            System.out.println("╔════════════════════════════════╗");
            System.out.println("    🎮 欢迎来到文字格斗游戏 🎮   ");
            System.out.println("╚════════════════════════════════╝");
            System.out.println("请选择操作：1登录 2注册 3退出");

            Scanner sc = new Scanner(System.in);
            String choose = sc.next();

            switch (choose) {
                case "1"->login(list);
                case "2"->register(list);
                case "3"-> {
                    System.out.println("用户选择了退出操作");
                    System.exit(0);
                }
                default->System.out.println("输入有误，请重新输入");
            }
        }
    }


    //登录的操作
    public void login(ArrayList<User> list) {
        System.out.println("用户登录操作");
        //判断用户名是否存在
        //  不存在：提示未注册
        //  存在：禁用，提示联系客服-
        //  存在：验证验证码（用机器直接注册）
        //  验证密码是否正确（三次）

        //1.键盘录入用户名
        Scanner sc = new Scanner(System.in);
        System.out.println("请输入用户名：");
        String username = sc.next();
        //2.不存在：提示未注册
        if (!contains(list, username)) {
            System.out.println("用户名" + username + "用户未注册，请先注册");
            //如果用户名不存在，结束登录的行为，回到选择界面当中注册
            return;
        }
        //3.存在：禁用，提示联系客服-
        //通过username，获取当前的用户对象，再看账户状态
        int index = findIndex(list, username);
        User u = list.get(index);
        if (!u.isStatus()) {
            System.out.println("用户名" + username + "已禁用，请联系客服");
            //如果用户名禁用，结束登录的行为，回到选择界面当中注册
            return;
        }

        //4.让用户继续键盘录入验证码和密码
        //验证密码是否正确（三次）
        String rightPassword = u.getPassword();
        for (int i = 0; i < 3; i++) {
            System.out.println("请输入密码:");
            String password = sc.next();
            //每次验证密码的时候，都需要输入验证码（人机）
            while (true) {
                //先生成一个正确的验证码
                String rightCode = getCode();
                System.out.println("验证码是：" + rightCode);

                System.out.println("请输入验证码:");
                String code = sc.next();

                if (rightCode.equalsIgnoreCase(code)) {
                    System.out.println("验证码正确");
                    break;
                }else {
                    System.out.println("验证码错误，请重新输入");
                    //如果验证码输入错误，需要重新生成一个新的验证码，并且让用户重新输入
                    continue;
                }
            }

            if (password.equals(rightPassword)) {
                System.out.println("登录成功，游戏启动-");
                //创建FightingGame类对象，并调用方法启动游戏
                FightingGame fg = new FightingGame();
                fg.gameStart(username);
                break;
            }else {
                System.out.println("密码错误，请重新输入");
                if(i==2){
                    //三次机会用完了
                    u.setStatus(false);//锁定账号
                    System.out.println("密码错误次数过多，请联系客服");
                    return;
                }else{
                    //三次机会还没用完
                    System.out.println("密码错误，还剩下" + (2-i) + "次机会");
                }
            }
        }


    }

    //注册的操作
    public void register(ArrayList<User> list) {
        System.out.println("用户注册操作");
        //1.创建User对象（空参）
        User u = new User();

        //2.键盘录入用户名
        //校验用户名是否符合要求
        //开发细节：
        // 在验证数据的时候，先验证格式是否正确，在验证是否唯一
        // 先判断异常的数据，剩下来的都是正确的数据 （好处：避免if的嵌套）
        //u.setUsername();
        Scanner sc = new Scanner(System.in);



        while (true) {

            System.out.println("请输入用户名：");
            String username = sc.next();
            //  1.长度必须在3 ~ 16位
            if (!checkLen(3, 16, username)) {
                System.out.println("用户名长度必须在3 ~ 16位之间，请重新输入");
                continue;
            }
            // 2.只能由字母、数字组成，不能是纯数字
            if(!checkUsername(username)) {
                System.out.println("用户名只能由字母、数字组成，不能是纯数字，请重新输入");
                continue;
            }
            // 3.用户名唯一
            //username 到list当中判断是否包含
            //包含重复
            //不包含：唯一
            if(contains(list, username)) {
                System.out.println("用户名已存在，请重新输入");
                continue;
            }
            //当代码执行到这说明用户名符合要求
            u.setUsername(username);
            break;
        }


        //3.键盘录入密码
        while (true) {
            //校验密码是否符合要求
            //u.setPassword();
            System.out.println("请输入密码：");
            String password1 = sc.next();
            System.out.println("请再次输入密码：");
            String password2 = sc.next();
            //  - 长度3 ~ 8位

            if (!checkLen(3, 8, password1)) {
                System.out.println("密码长度必须在3 ~ 8位之间，请重新输入");
                continue;
            }
            //  - 只能是字母加数字的组合，不能有其他字母
            if(!checkPassword(password1)){
                System.out.println("密码只能是字母加数字的组合，不能有其他字母，请重新输入");
                continue;
            }
            //校验两次输入的密码是否一致
            if(!password1.equals(password2)){
                System.out.println("两次输入的密码不一致，请重新输入");
                continue;
            }
            //把密码设置到对象当中
            u.setPassword(password1);
            break;

        }

        //4.把User对象添加到集合当中
        list.add(u);
        System.out.println("用户" + u.getUsername() + "注册成功");


    }

    //作用：在集合当中去找username所在的索引
    public int findIndex(ArrayList<User> list,String username){
        for (int i = 0; i < list.size(); i++) {
            User u = list.get(i);
            if (u.getUsername().equals(username)) {
                return i;
            }
        }
        return -1;
    }

    //作用：判断用户名在集合当中是否包含
    public boolean contains(ArrayList<User> list,String username){
        for (int i = 0; i < list.size(); i++) {
            User u = list.get(i);
            if (u.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }


    public int[] getCount(String userInfo){
        int charCount = 0;
        int numCount = 0;
        int otherCount = 0;
        for (int i = 0; i < userInfo.length(); i++) {
            char c = userInfo.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                charCount++;
            } else if (c >= '0' && c <= '9') {
                numCount++;
            } else {
                otherCount++;
            }
        }

        return new int[]{charCount, numCount, otherCount};

    }


    //作用：校验用户名是否符合要求
    //只能由字母、数字组成，不能是纯数字
    //字母至少有一个
    //数字可以有，也可以没有
    //其他字符一定不能有
    public boolean checkUsername(String username){
        int[] arr = getCount(username);
        //arr[0] 表示字母的个数
        // arr[1] 表示数字的个数
        // arr[2] 表示其他字符的个数

        //对三个变量进行判断
        //字母至少有一个 数字可以有，也可以没有 其他字符一定不能有
        return arr[0] > 0 && arr[1] >= 0 && arr[2] == 0;


    }

    //作用：校验密码是否符合要求
    //只能是字母加数字的组合，不能有其他字母
    //字母至少有一个
    // 数字至少有一个
    // 其他字符一定不能有
    public boolean checkPassword(String password){
        int[] arr = getCount(password);
        return arr[0] > 0 && arr[1] > 0 && arr[2] == 0;

    }

    //作用：判断字符串的长度是否在指定的范围之内
    //字符串。 指定的范围（最小值 最大值）
    public boolean checkLen(int minLen,int maxLen,String str){
//        if (str.length() > minLen || str.length() < maxLen) {
//            return true;
//        }else {
//            return false;
//        }
        return str.length() >= minLen && str.length() <= maxLen;

    }

//    长度为5
//    由4位大写或者小写字母和1位数字组成，同一个字母可重复
//    数字可以出现在任意位置
//    比如：aQa1K
    public static String getCode(){
        //1.把所有的大写和小写的字母都放到一个容器
        ArrayList<Character> list = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            list.add((char)('A' + i));
            list.add((char)('a' + i));
        }
        //2.从集合当中随机抽取字母（4位）
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 4; i++) {
            int index = random.nextInt(list.size());
            char c = list.get(index);
            sb.append(c);
        }
        //3.生成一个随机的数字
        sb.append(random.nextInt(10));

        //4.数字的位置可以是任意的
        //先把sb变成字符串，调用字符串的toCharArray方法，变成字符数组
        char[] arr = sb.toString().toCharArray();
        //把最大索引上的数据，跟一个的索引进行交换
        int i = random.nextInt(arr.length);
        //交换
        //最大索引：arr.length-1
        //随机索引i
        char temp = arr[i];
        arr[i] = arr[arr.length-1];
        arr[arr.length-1] = temp;

        //5，把字符数组当中的数据，再变回字符串
        String code = new String(arr);

        return code;
    }
}
