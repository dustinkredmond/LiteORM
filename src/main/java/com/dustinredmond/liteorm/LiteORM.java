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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class LiteORM {

    public static LiteORM getInstance() {
        if (instance == null) {
            instance = new LiteORM();
        }
        return instance;
    }

    private LiteORM() {
        dbUrl = "jdbc:sqlite:LiteORM.db";
    }

    @SuppressWarnings("unused")
    public void setDatabasePath(String path) {
        this.dbUrl = String.format("jdbc:sqlite:%s", path);
    }

    protected Connection connect() {
        try {
            return DriverManager.getConnection(this.dbUrl);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to get a SQL connection.", e);
        }
    }

    @SuppressWarnings("unused")
    public void createTableIfNotExists(Class<?> modelClass) {
        SQLExec.createTableIfNotExists(modelClass);
    }

    private static LiteORM instance;
    private String dbUrl;
}
