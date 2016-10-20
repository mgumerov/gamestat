package ru.slicer.carx;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.slicer.carx.dto.JsonData;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static spark.Spark.get;
import static spark.Spark.put;
import static spark.Spark.post;

@Component
public class WebConfig implements InitializingBean {

    @Autowired
    private CarxService service;

    private final static String OK = "OK";

    @Override
    public void afterPropertiesSet() throws Exception {
        get("/newUser", (req, res) -> {
            final User user = service.createUser();
            return user.getId();
        });

        //На самом деле, чтобы не верить всем на слово, было бы не лишним принимать от каждого юзера (предположительно,
        //прошедшего аутентификацию) только данные по этому самому юзеру. Но т.к. аутентификацию в рамках ТЗ не
        //заказывали, то понять, в чьей сессии пришел запрос - не получится.
        put("/data", (req, res) -> {
            final UUID uuid = UUID.fromString(req.queryParams("uuid"));
            final String clob = req.body();

            final JsonData parsed;
            final ObjectMapper mapper = new ObjectMapper();
            try {
                parsed = mapper.readValue(clob, JsonData.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            final Data data = new Data(uuid, parsed.getMoney(), clob, parsed.getCountry());
            service.putData(data);
            return OK;
        });

        //По условиям задания не описан контракт на способ возврата ошибок, так что будет HTTP 500 :)
        get("/data", (req, res) -> {
            final UUID uuid = UUID.fromString(req.queryParams("uuid"));
            final String clob = service.getClobData(uuid);
            return clob;
        });

        post("/activity", (req, res) -> {
            final UUID uuid = UUID.fromString(req.queryParams("uuid"));
            final int activity = Integer.parseInt(req.queryParams("activity"));
            service.storeActivity(uuid, activity, new Date());
            //Хотелось бы вернуть 204, но при этом мой rest-клиент как-то странно себя ведет, ну или Spark
            //некогда дебажить.
            return OK;
        });

        get("/analytics/top", (req, res) -> {
            final int cut = Integer.parseInt(req.queryParams("cut"));
            final List<User> users = service.getTopBuyers(cut);
            return new ObjectMapper().writeValueAsString(users);
        });

        get("/analytics/new", (req, res) -> {
            //В постановке не описан прецедент создания пользователя.
            //То ли это некий отдельный эндпойнт, то ли создание происходит при сохранении данных по юзеру,
            //ID которого ранее не встречался, то ли происходит оно вне рамок рассматриваемой подсистемы.
            //Соответственно, не описано, что считать датой создания пользователя, и тогда если "количество новых
            //пользователей за период" - как раз про даты создания, то постановки недостаточно для реализации.
            return "Not implemented";
        });

        get("/analytics/activities", (req, res) -> {
            final UUID uuid = UUID.fromString(req.queryParams("uuid"));
            final Date start = new Date(Long.valueOf(req.queryParams("start")).longValue());
            final Date end = new Date(Long.valueOf(req.queryParams("end")).longValue());

            final List<Activity> result = service.getActivities(uuid, start, end);
            return new ObjectMapper().writeValueAsString(result);
        });
    }
}