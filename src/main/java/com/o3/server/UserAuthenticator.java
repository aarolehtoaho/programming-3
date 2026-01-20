package com.o3.server;

import java.util.Hashtable;
import java.util.Map;

import com.sun.net.httpserver.BasicAuthenticator;

public class UserAuthenticator extends BasicAuthenticator {
    private Map<String, String> users = null;
    
    public UserAuthenticator() {
        super("datarecord");
        this.users = new Hashtable<>();
        users.put("dummy", "passwd");
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        String pass = users.get(username);
        return pass != null && pass.equals(password);
    }
}
