/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk;

import org.kontalk.client.Client;
import org.kontalk.model.UserList;
import org.kontalk.model.Account;
import com.alee.laf.WebLookAndFeel;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.logging.Logger;

import java.util.logging.Level;
import org.kontalk.crypto.PGP;
import org.kontalk.model.KontalkThread;
import org.kontalk.model.MessageList;
import org.kontalk.model.ThreadList;
import org.kontalk.model.User;
import org.kontalk.view.View;

/**
 * @author Alexander Bikadorov
 */
public class MyKontalk {
    private final static Logger LOGGER = Logger.getLogger(MyKontalk.class.getName());

    private final KontalkConfiguration mConfig = KontalkConfiguration.getConfiguration();
    
    private static MyKontalk INSTANCE;
    
    public enum Status {
        DISCONNECTING, DISCONNECTED, CONNECTING, CONNECTED, SHUTTING_DOWN 
    }
    
    private final Client mClient;
    private final View mView;
    private final UserList mUserList;
    private final ThreadList mThreadList;
    private final MessageList mMessageList;
    
    static {
        // register provider
        PGP.registerProvider();
    }
    
    private MyKontalk(String[] args){
        
        INSTANCE = this;
        
        parseArgs(args);

        mUserList = UserList.getInstance();
        mThreadList = ThreadList.getInstance();
        mMessageList = MessageList.getInstance();
        
        mClient = new Client();
        mView = new View(this);
        
        // order matters!
        mUserList.load();
        mThreadList.load();
        mMessageList.load();
    }
    
    // parse optional arguments 
    private void parseArgs(String[] args){
        if (args.length == 0)
            return;
        if (args.length == 2 && Pattern.matches(".*:\\d*", args[1])){
            String[] argsegs = args[1].split(Pattern.quote(":"));
            if (argsegs[0].length() != 0)
                mConfig.setProperty("server.host", argsegs[0]);
            if (argsegs[1].length() != 0)
                mConfig.setProperty("server.port", Integer.valueOf(argsegs[1]));
            //client.setUsername(args[0]);
        } else if (args.length == 1 && !Pattern.matches(".*:\\d*", args[0])){
            //client.setUsername(args[0]);
        } else {
            String className = this.getClass().getEnclosingClass().getName();
            LOGGER.log(Level.WARNING, "Usage: java {0} [USERNAME [SERVER:PORT]]", className);
        }
    }
    
    public void shutDown(){
        LOGGER.info("Shutting down...");
        mView.statusChanged(Status.SHUTTING_DOWN);
        mUserList.save();
        mThreadList.save();
        mClient.disconnect();
        Database.getInstance().close();
        mConfig.saveToFile();
        System.exit(0);
    }
    
    public void connect() {
        Account account;
        // TODO new account for each connection?
        try {
            account = new Account(mConfig);
        } catch (KontalkException ex) {
            // something wrong with the account, tell view
            mView.connectionProblem(ex);
            return;
        }
        mView.statusChanged(Status.CONNECTING);
        mClient.connect(account.getPersonalKey());
    }
    
    public void disconnect() {
        mView.statusChanged(Status.DISCONNECTING);
        mClient.disconnect();
    }
    
    public void userListChanged(){
        mView.userListChanged();
    }
    
    public void threadListChanged(){
        mView.threadListChanged();
    }
    
    public void threadChanged(KontalkThread thread) {
        mView.threadChanged(thread);
    }
    
    public void statusChanged(Status status){
        mView.statusChanged(status);
    }
    
    public void sendText(KontalkThread thread, String text) {
        boolean encrypted = false; // TODO
        // TODO no group chat support yet
        Set<User> user = thread.getUser();
        for (User oneUser: user) {
            String xmppID = mMessageList.addTo(thread, oneUser, text, encrypted);
            mClient.sendText(xmppID, oneUser.getJID(), text);
        }
    }
    
    public KontalkThread getThreadByID(int id) {
        return mThreadList.getThreadByID(id);
    }
    
    public UserList getUserList() {
        return mUserList;
    }
    
    public static MyKontalk getInstance() {
        return INSTANCE;
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        LOGGER.setLevel(Level.ALL);
        
        WebLookAndFeel.install();
        
        MyKontalk model = new MyKontalk(args);
        //model.connect();
    }

}