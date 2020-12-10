package com.dustinredmond.liteormtest;

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

import com.dustinredmond.liteorm.LiteORM;
import java.util.Date;

/**
 * Test POJO extending LiteORM to test
 * functionality
 */
@SuppressWarnings("unused")
public class Employee extends LiteORM<Employee> {

    private long id;
    private String firstName;
    private String lastName;
    private Date hireDate;

    public Employee() {
        super();
    }

    public Employee(String firstName, String lastName, Date hireDate) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.hireDate = hireDate;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Date getHireDate() {
        return hireDate;
    }

    public void setHireDate(Date hireDate) {
        this.hireDate = hireDate;
    }

    @Override
    public String toString() {
        return String.format("[%s,%s,%s,%s]",id,firstName,lastName,hireDate);
    }
}
