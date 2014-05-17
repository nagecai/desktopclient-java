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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kontalk.model.KontalkMessage;
import org.kontalk.model.KontalkThread;
import org.kontalk.model.User;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
public class Database {
    private final static Logger LOGGER = Logger.getLogger(Database.class.getName());
    private final static String DB_NAME = "kontalk.db";
    
    private static Database INSTANCE = null;

    private final MyKontalk mModel = MyKontalk.getInstance();
    private Connection mConn = null;
    
    private Database() {

        // load the sqlite-JDBC driver using the current class loader
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            LOGGER.log(Level.WARNING, "sqlite-JDBC driver not found", ex);
            mModel.shutDown();
        }

        // create database connection
        try {
          mConn = DriverManager.getConnection("jdbc:sqlite:"+DB_NAME);
        } catch(SQLException ex) {
          // if the error message is "out of memory", 
          // it probably means no database file is found
          LOGGER.log(Level.WARNING, "can't create database connection", ex);
          mModel.shutDown();
        }
        
        try {
            mConn.setAutoCommit(true);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't set autocommit", ex);
        }
        
        // make sure tables are created
        createTables();
    }
    
    private void createTables() {
        String create = "CREATE TABLE IF NOT EXISTS ";
        try (Statement stat = mConn.createStatement()) {
            stat.executeUpdate(create + User.TABLE + " " + User.CREATE_TABLE);
            stat.executeUpdate(create + 
                    KontalkThread.TABLE +
                    " " +
                    KontalkThread.CREATE_TABLE);
            stat.executeUpdate(create +
                    KontalkThread.TABLE_RECEIVER +
                    " " +
                    KontalkThread.CREATE_TABLE_RECEIVER);
            stat.executeUpdate(create +
                    KontalkMessage.TABLE +
                    " " +
                    KontalkMessage.CREATE_TABLE);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't create tables", ex);
            mModel.shutDown();
        }
    }

    public void close() {
        if(mConn!= null) {
            try {
                mConn.close();
            } catch(SQLException ex) {
                // connection close failed.
                System.err.println(ex);
            }
        }
    }
    
    public ResultSet execSelectAll(String table) {
        return execSelect("SELECT * FROM " + table);
    }
    
    public ResultSet execSelectWhereInsecure(String table, String where) {
        return execSelect("SELECT * FROM " + table + " WHERE " + where);
    }
    
    private ResultSet execSelect(String select) {
        try {
            PreparedStatement stat = mConn.prepareStatement(select);
            // does not work, i dont care
            //stat.closeOnCompletion();
            ResultSet resultSet = stat.executeQuery();
            return resultSet;
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't execute select: "+select, ex);
            return null;
        }
    }
    
//    public ResultSet execSelectWhere(String table, Map<String, Object> where) {
//        String select = "SELECT * FROM " + table + " WHERE ";
//        List<String> keys = new LinkedList(where.keySet());
//        for (String key: keys)
//            select += key + " = ?, ";
//        
//        try (PreparedStatement statement = mConn.prepareStatement(select)){
//            insertValues(statement, keys, where);
//            return statement.executeQuery();
//        } catch (SQLException ex) {
//            LOGGER.warning("can't prepare statement " + ex);
//            return null;
//        }
//    }

    /**
     *
     * @return id value of inserted row, 0 if something went wrong
     */
    public int execInsert(String table, List<Object> values) {
        // first column is the id
        String insert = "INSERT INTO " + table + " VALUES (NULL,";
        for (int i = 0; i < values.size(); i++) {
            insert += "?";
            if (i != values.size()-1)
                insert += ",";
        }
        insert += ")";
        
        try (PreparedStatement stat = mConn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)){
            insertValues(stat, values);
            stat.executeUpdate();
            ResultSet keys = stat.getGeneratedKeys();
            return keys.getInt(1);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't execute insert: " + insert + " " + values, ex);
            return 0;
        }
    }
    
    /**
     * Update values (at most one row)
     * @param table
     * @param set
     * @param id
     * @return id value of updated row, 0 if something went wrong
     */
    public int execUpdate(String table, Map<String, Object> set, int id) {
        String update = "UPDATE OR FAIL " + table + " SET ";
        List<String> setKeys = new LinkedList(set.keySet());
        for (int i = 0; i < setKeys.size(); i++) {
            update += setKeys.get(i) + " = ?";
            if (i != setKeys.size()-1)
                update += ", ";
        }
        // TODO
        update += " WHERE _id == " + id ;//+ " LIMIT 1";
        
        try (PreparedStatement stat = mConn.prepareStatement(update, Statement.RETURN_GENERATED_KEYS)){
            insertValues(stat, setKeys, set);
            stat.executeUpdate();
            ResultSet keys = stat.getGeneratedKeys();
            return keys.getInt(1);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't execute update: " + update + " " + set, ex);
            return 0;
        }
    }
    
    public void execDelete(String table, int id){
        try (Statement stat = mConn.createStatement()){
            stat.executeQuery("DELETE * FROM " + table + " WHERE _id = " + id);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "can't delete", ex);
        }
    }
    
    public static Database getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new Database();
        }
        return INSTANCE;
    }
    
    private static void insertValues(PreparedStatement stat, 
            List<String> keys, 
            Map<String, Object> map) throws SQLException {
        for (int i = 0; i < keys.size(); i++) {
            setValue(stat, i, map.get(keys.get(i)));
         }
    }
    
    private static void insertValues(PreparedStatement stat, 
            List<Object> values) throws SQLException {     
        for (int i = 0; i < values.size(); i++) {
            setValue(stat, i, values.get(i));
        }
    }
    
    private static void setValue(PreparedStatement stat, int i, Object value) 
            throws SQLException {
        if (value instanceof String) {
                stat.setString(i+1, (String) value);
            } else if (value instanceof Integer) {
                stat.setInt(i+1, (int) value);
            } else if (value instanceof Date) {
                stat.setLong(i+1, ((Date) value).getTime());
            } else if (value instanceof Boolean) {
                stat.setBoolean(i+1, (boolean) value);
            } else if (value == null){
                stat.setNull(i+1, Types.NULL);
            } else {
                LOGGER.warning("unknown type: " + value);
            }
    }
    
}