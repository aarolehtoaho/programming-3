package com.o3.server;

public class User {
    private String username;
    private String password;
    private String email;
    private String nickname;
    private boolean isHashed;

    public User(String username, String password, String email, String nickname, boolean isHashed) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.nickname = nickname;
        this.isHashed = isHashed;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public boolean isHashed() { return isHashed; }
}