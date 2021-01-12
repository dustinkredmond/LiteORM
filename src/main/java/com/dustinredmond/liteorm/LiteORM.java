package com.dustinredmond.liteorm;

/*
 *  Copyright 2020  Dustin K. Redmond
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
public abstract class LiteORM<T> {

    public LiteORM() {
        createTableIfNotExists(getClass());
    }

    /**
     * Find and retrieve an entity by its database
     * primary key (ID or id property).
     * @param id The entity's primary key.
     * @return An instance of the entity.
     */
    public T findById(long id) {
        try {
            T obj;
            try {
                //noinspection unchecked
                obj = (T) getClass().getDeclaredConstructor().newInstance();
            } catch (InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException("Unable to instantiate LiteORM. "
                    + "Ensure a default no-argument constructor is provided.", e);
            }
            if (!populateObjectValues(obj, getTableName(), getProperties(), id)) {
                return null;
            }
            return obj;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a List of all entities of this class.
     * @return List of all entities
     */
    public List<T> findAll() {
        return findAll(getClass(), getTableName());
    }

    /**
     * Updates the existing entity in the SQLite
     * database. This object's ID (id) property must
     * correspond to an entry in the SQLite database.
     */
    public void update() {
        update(getTableName(), getProperties());
    }

    /**
     * Creates a records in the SQLite database
     * for this entity.
     * <p>ID will be created automatically by SQLite</p>
     */
    public void create() {
        create(getTableName(), getProperties());
    }

    /**
     * Attempts to delete the current entity that this method
     * is called on. Deleting is accomplished via the ID (id)
     * property or field being used as a search.
     */
    public void delete() {
        delete(getTableName(), getProperties());
    }

    /**
     * Attempts to retrieve a list of objects from a {@code ResultSet}
     * @param rs A SQL {@code ResultSet}
     * @return A list of objects as contained in the {@code ResultSet}
     * @throws SQLException If a database access error occurs
     */
    public List<T> toObjects(ResultSet rs) throws SQLException {
        return toObjects(rs, getClass());
    }


    /**
     * Attempts to retrieve a list of objects from a {@code PreparedStatement}
     * @param ps A SQL {@code PreparedStatement}
     * @return A list of objects contained via querying the {@code PreparedStatement}
     * @throws SQLException If a database access error occurs
     */
    public List<T> toObjects(PreparedStatement ps) throws SQLException {
        try (Connection conn = connect(); ResultSet rs = ps.executeQuery()) {
            return toObjects(rs);
        } finally {
            ps.close();
        }
    }

    /**
     * Attempts to retrieve a list of objects from a SQL query as a String
     * @param query A SQL query as a String
     * @return A list of objects returned by the query.
     * @throws SQLException If a database access error occurs
     */
    public List<T> toObjects(String query) throws SQLException {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(query)) {
            return toObjects(ps);
        }
    }

    private HashMap<String, Object> getProperties() {
        HashMap<String, Object> params = new HashMap<>();
        for (Field field: this.getClass().getDeclaredFields()) {
            //noinspection deprecation
            boolean wasAccessible = field.isAccessible();
            field.setAccessible(true);
            try {
                if (field.get(this) == null) {
                    params.put(camelToUpperSnakeCase(field.getName()), null);
                    continue;
                }
                if (field.getGenericType().getTypeName().equals("java.util.Date")) {
                    long date = ((Date) field.get(this)).toInstant().toEpochMilli();
                    params.put(camelToUpperSnakeCase(field.getName()), date);
                } else {
                    params.put(camelToUpperSnakeCase(field.getName()), field.get(this));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Unable to access field value.");
            }
            field.setAccessible(wasAccessible);
        }
        return params;
    }

    private String getTableName() {
        if (getClass().isAnonymousClass()) {
            throw new RuntimeException("Class must not be anonymous.");
        } else {
            return camelToUpperSnakeCase(getClass().getSimpleName());
        }
    }

    private static String camelToUpperSnakeCase(String s) {
        StringBuilder sb = new StringBuilder();
        boolean skipFirst = true;
        for (char c : s.toCharArray()) {
            if (skipFirst) {
                skipFirst = false;
                sb.append(c);
                continue;
            }
            if (Character.isUpperCase(c)) {
                sb.append("_").append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString().toUpperCase();
    }

    private static void create(String tableName, HashMap<String, Object> params) {
        params.remove("ID"); // let SQLite figure this out

        StringJoiner sjField = new StringJoiner(",");
        StringJoiner sjValue = new StringJoiner(",");
        params.forEach((k,v) -> {
            if (v == null) {
                return;
            }
            sjField.add(k);
            if (v instanceof Number) {
                sjValue.add(v.toString());
            } else if (v instanceof java.sql.Date) {
                long date = ((java.sql.Date) v).toInstant().getEpochSecond();
                sjValue.add(String.format("%s", date));
            } else {
                sjValue.add("\""+v.toString()+"\"");
            }
        });

        final String sql = String.format("INSERT INTO %s (%s) VALUES (%s);",
            tableName, sjField.toString(), sjValue.toString());
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            if (printSql) {
                System.out.println(sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void update(String tableName, HashMap<String,Object> params) {
        StringJoiner sj = new StringJoiner(",");
        params.forEach((k,v) -> sj.add(" " + k + " = ?"));
        final String sql = String.format("UPDATE %s SET%s WHERE ID = %s",
            tableName, sj.toString(), params.get("ID"));
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            AtomicInteger i = new AtomicInteger(1);
            params.forEach((k,v) -> {
                try {
                    ps.setObject(i.getAndIncrement(), params.get(k));
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });
            ps.executeUpdate();
            if (printSql) {
                System.out.println(sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void delete(String tableName, HashMap<String, Object> params) {
        if (!params.containsKey("ID")) {
            throw new UnsupportedOperationException("A LiteORM entity must contain a property "
                + "'ID' that uniquely identifies it.");
        }
        final String sql = String.format("DELETE FROM %s WHERE ID = %s",
            tableName,
            params.get("ID"));
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            if (printSql) {
                System.out.println(sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> boolean populateObjectValues(T obj, String tableName, HashMap<String, Object> params, long id) {
        if (!params.containsKey("ID")) {
            throw new UnsupportedOperationException("A LiteORM entity must contain a property "
                + "'ID' that uniquely identifies it.");
        }
        StringJoiner sj = new StringJoiner(", ");
        params.keySet().forEach(sj::add);
        final String sql = String.format("SELECT %s FROM %s WHERE ID = %s",sj.toString(), tableName, id);
        if (printSql) {
            System.out.println(sql);
        }

        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            if (rs.isClosed()) {
                return false;
            }
            rs.next();
            for (String p : params.keySet()) {
                params.put(p, rs.getObject(p));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // params has been updated with values from query
        // update object from query
        for (Field field : obj.getClass().getDeclaredFields()) {
            //noinspection deprecation
            boolean wasAccessible = field.isAccessible();
            String sqlFieldName = camelToUpperSnakeCase(field.getName());
            try {
                field.setAccessible(true);
                field.set(obj, params.get(sqlFieldName));
            } catch (IllegalArgumentException e) {
                // yet another hacky date workaround
                try {
                    field.set(obj, java.sql.Date
                        .from(Instant.ofEpochMilli(Long.parseLong(params.get(sqlFieldName).toString()))));
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } finally {
                field.setAccessible(wasAccessible);
            }
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> List<T> findAll(Class<? extends LiteORM> theClass, String tableName) {
        final String sql = String.format("SELECT * FROM %s", tableName);
        if (printSql) {
            System.out.println(sql);
        }

        List<T> list = new ArrayList<>();
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData md = ps.getMetaData();
            while (rs.next()) {
                T obj = (T) theClass.getDeclaredConstructor().newInstance();
                for (Field field : obj.getClass().getDeclaredFields()) {
                    for (int i = 0; i < md.getColumnCount(); i++) {
                        if (camelToUpperSnakeCase(field.getName())
                            .equals(md.getColumnName(i + 1))) {
                            field.setAccessible(true);
                            try {
                                field.set(obj, rs.getObject(md.getColumnName(i+1)));
                            } catch (IllegalArgumentException ex) {
                                // hacky fix for dates
                                field.set(obj, rs.getDate(md.getColumnName(i+1)));
                            }
                            field.setAccessible(false);
                        }
                    }
                }
                list.add(obj);
            }
        } catch (SQLException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    /**
     * Attempts to create a SQLite table for the passed entity class.
     * @param modelClass Class for which to create a database table.
     */
    public static void createTableIfNotExists(Class<?> modelClass) {
        String tableName = camelToUpperSnakeCase(modelClass.getSimpleName());
        boolean containsId = false;

        StringJoiner sj = new StringJoiner(",\n");
        sj.add("ID INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT");
        for (Field declaredField : modelClass.getDeclaredFields()) {
            if (declaredField.getName().equalsIgnoreCase("ID")) {
                containsId = true;
                continue;
            }
            String type = declaredField.getType().getTypeName();
            String name = camelToUpperSnakeCase(declaredField.getName());
            switch (type) {
                case "java.lang.String":
                    sj.add(name + " VARCHAR NULL");
                    break;
                case "java.math.BigDecimal":
                    sj.add(name + " NUMERIC NULL");
                    break;
                case "boolean":
                case "java.lang.Boolean":
                    sj.add(name + " BIT NULL");
                    break;
                case "byte":
                case "java.lang.Byte":
                    sj.add(name + " TINYINT NULL");
                    break;
                case "short":
                case "java.lang.Short":
                    sj.add(name + " SMALLINT NULL");
                    break;
                case "int":
                case "java.lang.Integer":
                case "java.sql.Date":
                case "java.util.Date":
                    sj.add(name + " INTEGER NULL");
                    break;
                case "long":
                case "java.lang.Long":
                    sj.add(name + " BIGINT NULL");
                    break;
                case "float":
                case "java.lang.Float":
                    sj.add(name + " REAL NULL");
                    break;
                case "double":
                case "java.lang.Double":
                    sj.add(name + " DOUBLE NULL");
                    break;
                case "byte[]":
                    sj.add(name + " BINARY NULL");
                    break;
                case "java.sql.Time":
                    sj.add(name + " TIME NULL");
                    break;
                case "java.sql.Timestamp":
                    sj.add(name + " TIMESTAMP NULL");
                    break;
                default:
                    sj.add(name + " BLOB NULL");
                    break;
            }
        }

        if (!containsId) {
            throw new UnsupportedOperationException("Class must contain an id field.");
        }

        final String sql = String.format("CREATE TABLE IF NOT EXISTS %s(\n%s\n);",
            tableName, sj.toString());

        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            if (printSql) {
                System.out.println(sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("rawtypes")
    private List<T> toObjects(ResultSet rs, Class<? extends LiteORM> theClass) throws SQLException {
        List<T> objects = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        while (rs.next()) {
            try {
                //noinspection unchecked
                T obj = (T) theClass.getDeclaredConstructor().newInstance();
                for (int i = 1; i < md.getColumnCount()+1; i++) {
                    String colName = md.getColumnName(i);
                    for (Field field : obj.getClass().getDeclaredFields()) {
                        String fieldName = camelToUpperSnakeCase(field.getName());
                        if (colName.equals(fieldName)) {
                            field.setAccessible(true);
                            try {
                                field.set(obj, rs.getObject(i));
                            } catch (IllegalArgumentException ex) {
                                field.set(obj, rs.getDate(md.getColumnName(i)));
                            }
                        }
                    }
                }
                objects.add(obj);
            } catch (NoSuchMethodException | IllegalAccessException
                | InstantiationException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return objects;
    }

    /**
     * Attempts to return a Connection object to the SQLite database
     * managed by LiteORM.
     * @return {@code java.sql.Connection} used by LiteORM
     * @throws SQLException if unable to get a {@code Connection}
     */
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(LiteORM.dbUrl);
    }

    /**
     * Overrides the default path for the SQLite database.
     * By default, LiteORM saves all entities in a file called
     * 'LiteORM.db' located in the working directory.
     * @param path Path where the SQLite database
     *             will be created.
     */
    public static void setDatabasePath(String path) {
        LiteORM.dbUrl = String.format("jdbc:sqlite:%s", path);
    }

    /**
     * If set to true, prints all SQL executed to System.out
     * @param enabled Whether or not to enable printing of SQL
     */
    public static void setSqlPrinting(boolean enabled) {
        LiteORM.printSql = enabled;
    }

    private static String dbUrl = "jdbc:sqlite:LiteORM.db";
    private static boolean printSql;

}
