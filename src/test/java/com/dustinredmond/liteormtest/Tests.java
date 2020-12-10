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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class Tests {

    @Before
    public void setup() {
        LiteORM.setDatabasePath("LiteORM-tests.db");
    }

    @After
    public void destroy() {
        try {
            Files.deleteIfExists(Paths.get("LiteORM-tests.db"));
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }
    }

    @Test
    public void testCreateRetrieve() {
        // persist in db
        new Employee("John", "Smith", Date.from(Instant.now())).create();

        Employee e1 = new Employee().findById(1);
        assertEquals("John", e1.getFirstName());
        e1.delete();
        destroy();
    }

    @Test
    public void testFindAll() {
        for (int i = 0; i < 50; i++) {
            new Employee("First"+i, "Last"+i, new Date()).create();
        }
        assertEquals(50, new Employee().findAll().size());
        destroy();
    }

    @Test
    public void testShouldReturnNull() {
        destroy();
        assertNull(new Employee().findById(1));
    }

    @Test
    public void testShouldReturnEmptyList() {
        destroy();
        assertEquals(0, new Employee().findAll().size());
    }

    @Test
    public void testUpdate() {
        destroy();
        new Employee("John", "Smith", new Date()).create();

        Employee e = new Employee().findById(1);
        e.setLastName("Johnson");
        e.update();

        assertEquals("Johnson", new Employee().findById(1).getLastName());
        destroy();
    }

}
