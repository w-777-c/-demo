package com.itheima.doman;

import java.util.Random;

public class User {
    //id,用户名，密码，状态
    private String id;
    private String username;
    private String password;
    private boolean status;//false 禁用 true 可用
    //无参构造
    public User() {
        //调用createID方法，设置id
        id = createID();
        //修改status的值
        status = true;
    }
    //有参构造
    public User(String username, String password) {
        id = createID();
        this.username = username;
        this.password = password;
        status = true;
    }
    //用户无法设置，是自动生成的，格式为：heima+5位数字的随机数
    public String createID(){
        StringBuilder sb = new StringBuilder("黑马");
        Random r = new Random();
        for (int i = 0; i < 5; i++) {
            int num = r.nextInt(10);//0-9
            sb.append(num);
        }
        return sb.toString();
    }


    //get和set方法

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }
}
