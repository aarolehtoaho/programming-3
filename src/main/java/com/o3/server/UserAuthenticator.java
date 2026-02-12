package com.o3.server;

import java.sql.SQLException;

import com.sun.net.httpserver.BasicAuthenticator;

public class UserAuthenticator extends BasicAuthenticator {
    private static MessageDatabase database = MessageDatabase.getInstance();
    
    public UserAuthenticator() {
        super("datarecord");
    }

    public boolean addUser(String userName, String password, String email, String nickname) {
        if (database.userRegistered(userName)) {
            return false;
        }
        
        User newUser = new User(userName, password, email, nickname, false);
        try {
            database.insertUser(newUser);
        } catch (SQLException e) {
            return false;
        }

        return true;
    }

    public String getNickname(String username) {
        User user;
        try {
            user = database.getUser(username);
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
            user = database.getUser(username);
        } catch (SQLException e) {
            user = null;
        }
        return user != null && database.validPassword(user, password);
    }
}
