package com.o3.server;

import java.sql.SQLException;

import com.sun.net.httpserver.BasicAuthenticator;

public class UserAuthenticator extends BasicAuthenticator {
    public UserAuthenticator() {
        super("datarecord");
    }

    public boolean addUser(String userName, String password, String email, String nickname) {
        User newUser = new User(userName, password, email, nickname, false);
        try {
            MessageDatabase.getInstance().insertUser(newUser);
        } catch (SQLException e) {
            return false;
        }

        return true;
    }

    public String getNickname(String username) {
        User user;
        try {
            user = MessageDatabase.getInstance().getUser(username);
        } catch (SQLException e) {
            user = null;
        }
        
        if (user != null) {
            return user.getNickname();
        }
        return null;
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        User user;
        try {
            user = MessageDatabase.getInstance().getUser(username);
        } catch (SQLException e) {
            user = null;
        }
        return user != null && MessageDatabase.getInstance().validPassword(user, password);
    }
}
