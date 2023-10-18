# LiteORM

LiteORM is a minimalistic persistence library for working with SQLite.
Sometimes other frameworks, such as Hibernate, are too heavy for smaller projects.
If you are only dealing with a table or two and don't care about data normalization,
then LiteORM can make development much quicker.

LiteORM handles creating tables for POJOs and provides basic CRUD functionality.

---
### Functionalities

LiteORM purposely ignores database relations, foreign keys, constraints, etc. in order
to provide for an extremely tiny library.

If your application only contains a single table, or very few, LiteORM is the way to go.
Spend more time writing application logic, than writing JDBC method calls and SQL.

**Features**
- Automatic table creation
- CRUD (Create,Read,Update,Delete)
  - create()
  - update()
  - delete()
  - findAll(), findById() etc.
- Mapping of ResultSets/PreparedStatements/queries to Objects

---
### What does LiteORM do?

First and foremost, for an entity, we need a table.

Let's create a POJO (EmployeeInfo) and extend the LiteORM class,
this will provide us with nifty functionality.

```java
import com.dustinredmond.liteorm.LiteORM;

public class EmployeeInfo extends LiteORM<EmployeeInfo> {
  private long id;
  private String firstName;
  private String lastName;
  private Date hireDate;

  // LiteORM needs a default no-argument constructor
  public EmployeeInfo() { super(); }

  // getters and setters here
}
```

LiteORM will generate a table like below.

```sqlite
CREATE TABLE EMPLOYEE_INFO (
    ID INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    FIRST_NAME VARCHAR,
    LAST_NAME  VARCHAR,
    HIRE_DATE  DATE
);
```

Now, we have access to all the LiteORM functionality directly from
the instance methods of our POJO that extended `LiteORM`.


```java
import com.dustinredmond.liteorm.EmployeeInfo;
import java.sql.PreparedStatement;
import java.util.Date;

public class Test {

    public void testORM() {
        EmployeeInfo i = new EmployeeInfo();
        i.setFirstName("John");
        i.setLastName("Smith");
        i.setHireDate(new Date());
        // i.setId(...) not necessary, handled by SQLite

        i.create(); // creates entity in SQLite table

        EmployeeInfo e = i.findById(1); // find existing entry
        e.setFirstName("Jane");
        e.update(); // updates already existing entity 

        e.delete(); // removes DB entry

        PreparedStatement ps = ... // create some prepared statement
        // get objects based on prepared statement
        List<EmployeeInfo> employees = e.toObjects(ps);
        
        // or based on query as a String value
        List<EmployeeInfo> employees2 = e.toObjects("SELECT * FROM EMPLOYEE WHERE ...");
    }

}
```

By default, the SQLite Database is created as "LiteORM.db" in the 
current working directory of the application.

This can be overridden by the below:
```java
import com.dustinredmond.liteorm.LiteORM;

// Then call the below
LiteORM.setDatabasePath("/path/to/MyDatabase.db");
``` 


---
### What does LiteORM NOT do?

LiteORM only supports persisting and retrieving POJOs.
No attempts are made to maintain database relations or to persist
Objects other than built-in Java types.

The way that the library is written, if you attempt to persist anything
other than a built-in Java type, it will be persisted as a BLOB type
in SQLite. This is probably not what you want.

Mainly, I use this library myself to write application skeletons/wireframes 
with a bit of functionality, for a serious production scenario, I would 
stick with Hibernate, native JDBC, or a more comprehensive ORM library.
