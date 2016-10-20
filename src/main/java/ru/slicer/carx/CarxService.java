package ru.slicer.carx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class CarxService {
    //1) Работа с SQL могла бы быть из сервиса вынесена в repository, можно было бы тестировать логику сервиса отдельно,
    //а репозиторий подставить моковый. Тогда тут бы не пришлось возиться с SQL. Но в сервисе я уже написал, почему
    //не стал так делать.
    //Но получилась бы такая тонкая прослойка в сервисе, что городить ради этого
    //бойлерплейт жалко. Хотя вот тестировать было бы удобнее. Но и тестировать тогда было бы нечего, т.к. каждый вызов
    //метода сервиса один к одному транслируется в вызов метода репозитария :)
    //
    //2) Я сильно подозреваю, что в условиях неограниченных возможностей горизонтального масштабирования
    //преимущество будет не за postgre и даже не за ораклом, а в пределе скорее за системами, специально
    //рассчитанными на такой сценарий. Проблема лишь в том, что я с ними (как я понимаю, Cassandra из таких,
    //насчет остальных из top-10 навскидку не помню) совершенно не работал. Поэтому обойдусь тем, что знаю.
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    public void setDatasource(final DataSource datasource) {
        jdbcTemplate = new NamedParameterJdbcTemplate(datasource);
    }

    @Transactional
    public User createUser() {
        //todo Switch to numeric IDs
        final UUID id = UUID.randomUUID();
        //todo we might want to make query texts configurable
        jdbcTemplate.update("insert into public.User (id) values (:id)", Collections.singletonMap("id", id.toString()));
        return new User(id);
    }

    @Transactional
    public void putData(final Data data) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", data.getUserId().toString());
        params.addValue("clob", data.getClob());
        params.addValue("money", Integer.valueOf(data.getMoney()));
        params.addValue("country", data.getCountry());
        final int changes = jdbcTemplate.update(
                "update Data set clob= (:clob), money=(:money), country=(:country) where userid = (:id)",
                params);
        if (changes == 0) {
            jdbcTemplate.update(
                    "insert into Data (userid, clob, money, country) values (:id, :clob, :money, :country)",
                    params);
        }
    }

    @Transactional(readOnly = true)
    public String getClobData(final UUID uuid) {
        final String clob = jdbcTemplate.queryForObject("select clob from Data where userid=:id",
                Collections.singletonMap("id", uuid.toString()), String.class);
        return clob;
    }

    @Transactional
    public void storeActivity(final UUID uuid, final int activity, Date date) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", uuid.toString());
        params.addValue("activity", Integer.valueOf(activity));
        params.addValue("date", date);
        jdbcTemplate.update(
                "insert into Activity (userid, activity, timestamp) values (:id, :activity, :date)",
                params);
    }

    @Transactional(readOnly = true)
    //Actually we probably want to return not just User but also its country and money, but since there is
    //no such requirement in CR I'll skip that
    public List<User> getTopBuyers(final int cut) {
        //Индекса по (country,money) должно быть достаточно, чтобы для каждой строки мы более-менее точно
        //попадали в интересующий диапазон строк по money, без seq scan.
        //Но вот проблема, снаружи-то всё равно будет seq scan: мы не можем просто вычеркнуть какие-то строки
        //из поиска, пока не узнаем точно, что они дальше чем N в своей стране.
        //
        //todo Такой запрос почему-то не додумывается, что можно сразу ограничить subquery условием rank <= :cut
        //и судя по плану вроде бы сначала получает результат со всеми rank, а затем фильтрует его. Можно было бы
        //проверить, так ли это, но нет времени. В крайнем случае можно было бы воспользоваться тем, что стран должно
        //быть не так уж много, и на стороне сервера задать хранимую процедуру, которая в цикле по ним проходит и
        //выдергивает выборку по каждой стране, а затем их объединяет.
        //И еще в плане почему-то присутствует сортировка для образования окон и расчета аналитической функции,
        //хотя под это есть подходящий ключ.
        return jdbcTemplate.query("select * from" +
                        "  (select userid, country, money, rank() OVER (partition by country order by money, userid desc) from Data) sq" +
                        " where sq.rank <= :cut",
                Collections.singletonMap("cut", Integer.valueOf(cut)),
                (ResultSet resultSet, int i) ->
                        new User(UUID.fromString(resultSet.getString(1))));
    }

    public List<Activity> getActivities(final UUID uuid, final Date start, final Date end) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", uuid.toString());
        params.addValue("start", start);
        params.addValue("end", end);
        return jdbcTemplate.query("select activity,timestamp from Activity " +
                        "where userid = :id " +
                        "and timestamp between :start and :end",
                params,
                (ResultSet resultSet, int i) ->
                        new Activity(uuid, resultSet.getInt(1), resultSet.getDate(2)));
    }
}
