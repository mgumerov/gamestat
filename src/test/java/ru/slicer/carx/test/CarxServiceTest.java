package ru.slicer.carx.test;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.slicer.carx.CarxService;
import ru.slicer.carx.Data;
import ru.slicer.carx.User;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CarxServiceTest extends Assert {

    private final DataSource ds;

    private CarxService service;

    private final static UUID USERID_A = UUID.fromString("ffcda962-c0e9-42d2-b5c2-354be69bab0a");
    private final static UUID USERID_B = UUID.fromString("ffcda962-c0e9-42d2-b5c2-354be69bab0b");
    private final static UUID USERID_C = UUID.fromString("ffcda962-c0e9-42d2-b5c2-354be69bab0c");
    private final static UUID USERID_D = UUID.fromString("ffcda962-c0e9-42d2-b5c2-354be69bab0d");
    private final static UUID USERID_E = UUID.fromString("ffcda962-c0e9-42d2-b5c2-354be69bab0e");

    public CarxServiceTest() {
        //Если бы работа с SQL была из сервиса вынесена в repository, можно было бы тестировать логику сервиса отдельно,
        //а репозиторий подставить моковый. Тогда тут бы не пришлось возиться с SQL. Но в сервисе я уже написал, почему
        //не стал так делать - в сервисе почти ничего тогда бы не осталось. Да и тестировать тогда нечего было бы.
        ds = new DriverManagerDataSource("jdbc:hsqldb:mem:aname", "sa", "");
    }

    @Before
    public void setup() {
        final Connection connection;
        try {
            connection = ds.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try {
            final Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                    new JdbcConnection(connection));
            final Liquibase liquibase = new liquibase.Liquibase("db-changelog.xml",
                    new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());
        } catch (LiquibaseException e) {
            throw new RuntimeException(e);
        }
        service = new CarxService();
        service.setDatasource(ds);
    }

    @After
    public void teardown() {
        try {
            ds.getConnection().createStatement().execute("drop table public.Data");
            ds.getConnection().createStatement().execute("drop table Activity");
            ds.getConnection().createStatement().execute("drop table public.User");
            ds.getConnection().createStatement().execute("drop table databaseChangeLog");
            ds.getConnection().createStatement().execute("drop table databaseChangeLogLock");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        service = null;
    }

    @Test
    //В идеале, нам бы тут только сохранение проверять, потом вызывать сервис и так же SQL-кой проверять,
    //что добавление реально произошло. Но на это жалко времени, поэтому напишу более "интеграционный" тест -
    //проверю сразу и добавление, и успешное чтение.
    public void shouldAcceptData() {
        try {
            ds.getConnection().createStatement().execute("insert into public.User(id) values ('" + USERID_A.toString() + "')");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        final String sdata = "{\"money\":\"5\"}";
        final Data data = new Data(USERID_A, 5, sdata, "RU");
        service.putData(data);

        //если бы был метод, достающий Data целиком, надо было бы и его задействовать, но я решил сэкономить на нем
        assertEquals(service.getClobData(USERID_A), sdata);
    }

    @Test(expected = Exception.class)
    public void shouldFailOnGetDataOnInvalidUser() {
        service.getClobData(USERID_A);
    }

    @Test(expected = Exception.class)
    public void shouldFailOnPutDataOnInvalidUser() {
        final String sdata = "{\"money\":\"5\"}";
        final Data data = new Data(USERID_A, 5, sdata, "RU");
        service.putData(data);
    }

    //@Test
    //Я было уже разошелся и начал писать тест для другого эндпойнта. И даже написал, но когда
    //оптимизировал реализацию, наткнулся на проблему: window functions не поддерживаются в HSQLDB :)
    //Поэтому этот тест придется исключить.
    public void shouldGetTopTwoBuyers() {
        try {
            ds.getConnection().createStatement().execute("insert into public.User(id) values " +
                    "('" + USERID_A.toString() + "')," +
                    "('" + USERID_B.toString() + "')," +
                    "('" + USERID_C.toString() + "')," +
                    "('" + USERID_D.toString() + "')," +
                    "('" + USERID_E.toString() + "')");
            ds.getConnection().createStatement().execute("insert into Data(userid, money, country, clob) values " +
                    "('" + USERID_A.toString() + "',5,'RU','X')," +
                    "('" + USERID_B.toString() + "',5, 'RU', 'Y')," +
                    "('" + USERID_C.toString() + "',1, 'RU', 'Z')," +
                    "('" + USERID_D.toString() + "',10, 'CN', 'W')," +
                    "('" + USERID_E.toString() + "',7, 'US', 'Q')");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        final List<User> users = service.getTopBuyers(2);
        assertArrayEquals(users.stream().map(User::getId).sorted().toArray(),
                Arrays.asList(new UUID[] {USERID_A, USERID_B, USERID_D, USERID_E}).stream().sorted().toArray());
    }
}
