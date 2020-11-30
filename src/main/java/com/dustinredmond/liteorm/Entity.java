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
import java.util.Date;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("unused")
public abstract class Entity<T> {

    public T findById(long id) {
        createTableForEntity();
        try {
            //noinspection unchecked
            T obj = (T) getClass().newInstance();
            if (!SQLExec.populateObjectValues(obj, getTableName(), getProperties(), id)) {
                return null;
            }
            return obj;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public List<T> findAll() {
        createTableForEntity();
        return SQLExec.findAll(getClass(), getTableName());
    }

    public void update() {
        createTableForEntity();
        SQLExec.update(getTableName(), getProperties());
    }

    public void create() {
        createTableForEntity();
        SQLExec.create(getTableName(), getProperties());
    }

    public void delete() {
        createTableForEntity();
        SQLExec.delete(getTableName(), getProperties());
    }

    private HashMap<String, Object> getProperties() {
        HashMap<String, Object> params = new HashMap<>();
        for (Field field: this.getClass().getDeclaredFields()) {
            boolean wasAccessible = field.isAccessible();
            field.setAccessible(true);
            try {
                if (field.get(this) == null) {
                    params.put(TextUtils.camelToUpperSnakeCase(field.getName()), null);
                    continue;
                }
                if (field.getGenericType().getTypeName().equals("java.util.Date")) {
                    long date = ((Date) field.get(this)).toInstant().toEpochMilli();
                    params.put(TextUtils.camelToUpperSnakeCase(field.getName()), date);
                } else {
                    params.put(TextUtils.camelToUpperSnakeCase(field.getName()), field.get(this));
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
            return TextUtils.camelToUpperSnakeCase(getClass().getSimpleName());
        }
    }

    private boolean isTableCreated = false;
    private void createTableForEntity() {
        if (!isTableCreated) {
            LiteORM.getInstance().createTableIfNotExists(getClass());
            isTableCreated = true;
        }
    }
}
