package io.vertx.blueprint.todolist.verticles;


import io.vertx.blueprint.todolist.Constants;
import io.vertx.blueprint.todolist.entity.Todo;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vert.x Blueprint Application - Todo Backend
 * Single Application Verticle (Redis)
 * Service and controller logic in one class
 *
 * @author Eric Zhao
 */
public class SingleApplicationVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(SingleApplicationVerticle.class);

  private static final String HTTP_HOST = "0.0.0.0";
  private static final String REDIS_HOST = "127.0.0.1";
  private static final int HTTP_PORT = 8082;
  private static final int REDIS_PORT = 6379;

  private RedisAPI redisAPI;

  /**
   * Init persistence
   */
  private void initData() {
    RedisOptions config;
    // this is for OpenShift Redis Cartridge
    String osPort = System.getenv("OPENSHIFT_REDIS_PORT");
    String osHost = System.getenv("OPENSHIFT_REDIS_HOST");
    String connectionString;
    if (osPort != null && osHost != null)
      connectionString = String.format("redis:%s:%s",osHost,osPort);
    else
      connectionString = String.format("redis:%s:%s",
              config().getString("redis.host", REDIS_HOST),
              config().getString("redis.port", String.valueOf(REDIS_PORT)));

      config = new RedisOptions()
        .setConnectionString(connectionString);

    Redis redis = Redis.createClient(vertx, config);
    this.redisAPI = RedisAPI.api(redis);
    List<String> params = new ArrayList<>();
    params.add(Constants.REDIS_TODO_KEY);
    params.add("24");
    params.add(Json.encodePrettily(new Todo(24, "Something to do...", false, 1, "todo/ex")));
    redisAPI.hset(params).onFailure(res -> {
      LOGGER.error("Redis service is not running!");
      res.printStackTrace();
    });

  }

  @Override
  public void start(Promise<Void> promise) throws Exception {
    initData();
    Router router = Router.router(vertx);
    // CORS support
    Set<String> allowHeaders = new HashSet<>();
    allowHeaders.add("x-requested-with");
    allowHeaders.add("Access-Control-Allow-Origin");
    allowHeaders.add("origin");
    allowHeaders.add("Content-Type");
    allowHeaders.add("accept");
    Set<HttpMethod> allowMethods = new HashSet<>();
    allowMethods.add(HttpMethod.GET);
    allowMethods.add(HttpMethod.POST);
    allowMethods.add(HttpMethod.DELETE);
    allowMethods.add(HttpMethod.PATCH);

    router.route().handler(BodyHandler.create());
    router.route().handler(CorsHandler.create("*")
      .allowedHeaders(allowHeaders)
      .allowedMethods(allowMethods));

    // routes
    router.get(Constants.API_GET).handler(this::handleGetTodo);
    router.get(Constants.API_LIST_ALL).handler(this::handleGetAll);
    router.post(Constants.API_CREATE).handler(this::handleCreateTodo);
    router.patch(Constants.API_UPDATE).handler(this::handleUpdateTodo);
    router.delete(Constants.API_DELETE).handler(this::handleDeleteOne);
    router.delete(Constants.API_DELETE_ALL).handler(this::handleDeleteAll);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(config().getInteger("http.port", HTTP_PORT),
        config().getString("http.address", HTTP_HOST), result -> {
          if (result.succeeded())
            promise.complete();
          else
            promise.fail(result.cause());
        });
  }

  private void handleCreateTodo(RoutingContext context) {
    try {
      final Todo todo = wrapObject(new Todo(context.getBodyAsString()), context);
      final String encoded = Json.encodePrettily(todo);
      List<String> params = new ArrayList<>();
      params.add(Constants.REDIS_TODO_KEY);
      params.add(String.valueOf(todo.getId()));
      params.add(encoded);
      redisAPI.hset(params).onSuccess(res -> context.response()
        .setStatusCode(201)
        .putHeader("content-type", "application/json")
        .end(encoded)).onFailure(res -> sendError(503, context.response()));
    } catch (DecodeException e) {
      sendError(400, context.response());
    }
  }

  private void handleGetTodo(RoutingContext context) {
    String todoID = context.request().getParam("todoId");
    if (todoID == null)
      sendError(400, context.response());
    else {
      redisAPI.hget(Constants.REDIS_TODO_KEY, todoID)
        .onFailure(res -> sendError(503, context.response()))
        .onSuccess(res -> {
          String result = res.toString();
          if (result == null)
            sendError(404, context.response());
          else {
            context.response()
              .putHeader("content-type", "application/json")
              .end(result);
          }
      });
    }
  }

  private void handleGetAll(RoutingContext context) {
    redisAPI.hvals(Constants.REDIS_TODO_KEY)
      .onFailure(res -> sendError(503, context.response()))
      .onSuccess(res -> {
        String encoded = Json.encodePrettily(res.stream()
          .map(x -> new Todo(x.toString()))
          .collect(Collectors.toList()));
        context.response()
          .putHeader("content-type", "application/json")
          .end(encoded);
      });
  }

  private void handleUpdateTodo(RoutingContext context) {
    try {
      String todoID = context.request().getParam("todoId");
      final Todo newTodo = new Todo(context.getBodyAsString());
      // handle error
      if (todoID == null) {
        sendError(400, context.response());
        return;
      }


      redisAPI.hget(Constants.REDIS_TODO_KEY, todoID)
          .onFailure(res -> sendError(503, context.response()))
          .onSuccess(res -> {
            String result = res.toString();
            if (result == null)
              sendError(404, context.response());
            else {
              Todo oldTodo = new Todo(result);
              String response = Json.encodePrettily(oldTodo.merge(newTodo));
              List<String> params = new ArrayList<>();
              params.add(Constants.REDIS_TODO_KEY);
              params.add(todoID);
              params.add(response);
              redisAPI.hset(params)
                .onSuccess(x -> context.response()
                  .putHeader("content-type", "application/json; charset=utf-8")
                  .end(response)
                );
            }
          });
    } catch (DecodeException e) {
      sendError(400, context.response());
    }
  }

  private void handleDeleteOne(RoutingContext context) {
    String todoID = context.request().getParam("todoId");
    List<String> params = new ArrayList<>();
    params.add(Constants.REDIS_TODO_KEY);
    params.add(todoID);
    redisAPI.hdel(params)
      .onSuccess(res -> context.response().setStatusCode(204).end())
      .onFailure(res -> sendError(503, context.response()));
  }

  private void handleDeleteAll(RoutingContext context) {
    List<String> params = new ArrayList<>();
    params.add(Constants.REDIS_TODO_KEY);
    redisAPI.del(params)
      .onSuccess(res -> context.response().setStatusCode(204).end())
      .onFailure(res -> sendError(503, context.response()));
  }

  private void sendError(int statusCode, HttpServerResponse response) {
    response.setStatusCode(statusCode).end();
  }

  /**
   * Wrap the Todo entity with appropriate id and url
   *
   * @param todo    a todo entity
   * @param context RoutingContext
   * @return the wrapped todo entity
   */
  private Todo wrapObject(Todo todo, RoutingContext context) {
    int id = todo.getId();
    if (id > Todo.getIncId()) {
      Todo.setIncIdWith(id);
    } else if (id == 0)
      todo.setIncId();
    todo.setUrl(context.request().absoluteURI() + "/" + todo.getId());
    return todo;
  }

}
