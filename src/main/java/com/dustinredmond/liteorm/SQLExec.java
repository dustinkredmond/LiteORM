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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

public class SQLExec {

    protected static void create(String tableName, HashMap<String, Object> params) {
        params.remove("ID"); // let SQLite figure this out

        StringJoiner sjField = new StringJoiner(",");
        StringJoiner sjValue = new StringJoiner(",");
        params.forEach((k,v) -> {
            sjField.add(k);
            if (v instanceof Number) {
                sjValue.add(v.toString());
            } else {
                sjValue.add("\""+v.toString()+"\"");
            }
        });

        final String sql = String.format("INSERT INTO %s (%s) VALUES (%s);",
            tableName, sjField.toString(), sjValue.toString());
       try (Connection conn = LiteORM.getInstance().connect();
           PreparedStatement ps = conn.prepareStatement(sql)) {
           ps.executeUpdate();
       } catch (SQLException e) {
           throw new RuntimeException(e);
       }
    }

    protected static void update(String tableName, HashMap<String,Object> params) {
        StringJoiner sj = new StringJoiner(",");
        params.forEach((k,v) -> sj.add(" " + k + " = ?"));
        final String sql = String.format("UPDATE %s SET%s WHERE ID = %s",
            tableName, sj.toString(), params.get("ID"));
        try (Connection conn = LiteORM.getInstance().connect();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            AtomicInteger i = new AtomicInteger(1);
            params.forEach((k,v) -> {
                try {
                    ps.setObject(i.getAndIncrement(), params.get(k));
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected static void delete(String tableName, HashMap<String, Object> params) {
        if (!params.containsKey("ID")) {
            throw new UnsupportedOperationException("An Entity must contain a property "
                + "'ID' that uniquely identifies it.");
        }
        final String sql = String.format("DELETE FROM %s WHERE ID = %s",
            tableName,
            params.get("ID"));
        try (Connection conn = LiteORM.getInstance().connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> boolean populateObjectValues(T obj, String tableName, HashMap<String, Object> params, long id) {
        if (!params.containsKey("ID")) {
            throw new UnsupportedOperationException("An Entity must contain a property "
                + "'ID' that uniquely identifies it.");
        }
        StringJoiner sj = new StringJoiner(", ");
        params.keySet().forEach(sj::add);
        final String sql = String.format("SELECT %s FROM %s WHERE ID = %s",sj.toString(), tableName, id);

        try (Connection conn = LiteORM.getInstance().connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
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
            boolean wasAccessible = field.isAccessible();
            String sqlFieldName = TextUtils.camelToUpperSnakeCase(field.getName());
            try {
                field.setAccessible(true);
                field.set(obj, params.get(sqlFieldName));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } finally {
                field.setAccessible(wasAccessible);
            }
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> List<T> findAll(Class<? extends Entity> theClass, String tableName) {
        final String sql = String.format("SELECT * FROM %s", tableName);

        List<T> list = new ArrayList<>();
        try (Connection conn = LiteORM.getInstance().connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData rsmd = ps.getMetaData();
            while (rs.next()) {
                T obj = (T) theClass.newInstance();
                for (Field field : obj.getClass().getDeclaredFields()) {
                    for (int i = 0; i < rsmd.getColumnCount(); i++) {
                        if (TextUtils.camelToUpperSnakeCase(field.getName())
                            .equals(rsmd.getColumnName(i + 1))) {
                            field.setAccessible(true);
                            field.set(obj, rs.getObject(rsmd.getColumnName(i+1)));
                            field.setAccessible(false);
                        }
                    }
                }
                list.add(obj);
            }
        } catch (SQLException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public static void createTableIfNotExists(Class<?> modelClass) {
        String tableName = TextUtils.camelToUpperSnakeCase(modelClass.getSimpleName());
        boolean containsId = false;

        StringJoiner sj = new StringJoiner(",\n");
        sj.add("ID INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT");
        for (Field declaredField : modelClass.getDeclaredFields()) {
            if (declaredField.getName().toUpperCase().equals("ID")) {
                containsId = true;
                continue;
            }
            String type = declaredField.getType().getTypeName();
            String name = TextUtils.camelToUpperSnakeCase(declaredField.getName());
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
                case "java.sql.Date":
                case "java.util.Date":
                    sj.add(name + " DATE NULL");
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

        try (Connection conn = LiteORM.getInstance().connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
