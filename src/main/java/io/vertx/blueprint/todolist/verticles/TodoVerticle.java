package io.vertx.blueprint.todolist.verticles;

import io.vertx.blueprint.todolist.Constants;
import io.vertx.blueprint.todolist.entity.Todo;
import io.vertx.blueprint.todolist.service.JdbcTodoService;
import io.vertx.blueprint.todolist.service.RedisTodoService;
import io.vertx.blueprint.todolist.service.TodoService;
import io.vertx.core.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.redis.client.RedisOptions;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Vert.x Blueprint Application - Todo Backend
 * Application Verticle
 *
 * @author Eric Zhao
 */
public class TodoVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(TodoVerticle.class);

  private static final String HOST = "0.0.0.0";
  private static final int PORT = 8082;

  private TodoService service;

  private void initData() {
    final String serviceType = config().getString("service.type", "redis");
    LOGGER.info("Service Type: " + serviceType);
    switch (serviceType) {
      case "jdbc":
        service = new JdbcTodoService(vertx, config());
        break;
      case "redis":
      default:
        String connectionString = String.format("redis:%s:%s",
          config().getString("redis.host", "127.0.0.1"),
          config().getString("redis.port", "6379"));
        String password = config().getString("password");
        RedisOptions config = new RedisOptions().addConnectionString(connectionString)
                .setPassword(password);
        service = new RedisTodoService(vertx, config);
    }

    service.initData().onFailure(res -> {
        LOGGER.error("Persistence service is not running!");
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
      .listen(config().getInteger("http.port", PORT),
        config().getString("http.address", HOST), result -> {
          if (result.succeeded())
            promise.complete();
          else
            promise.fail(result.cause());
        });
  }

  /**
   * Wrap the result handler with failure handler (503 Service Unavailable)
   */
  private <T> Handler<AsyncResult<T>> resultHandler(RoutingContext context, Consumer<T> consumer) {
    return res -> {
      if (res.succeeded()) {
        consumer.accept(res.result());
      } else {
        serviceUnavailable(context);
      }
    };
  }

  private void handleCreateTodo(RoutingContext context) {
    try {
      final Todo todo = wrapObject(new Todo(context.getBodyAsString()), context);
      final String encoded = Json.encodePrettily(todo);

      service.insert(todo).onComplete(resultHandler(context, res -> {
        if (res) {
          context.response()
            .setStatusCode(201)
            .putHeader("content-type", "application/json")
            .end(encoded);
        } else {
          serviceUnavailable(context);
        }
      }));
    } catch (DecodeException e) {
      sendError(400, context.response());
    }
  }

  private void handleGetTodo(RoutingContext context) {
    String todoID = context.request().getParam("todoId");
    if (todoID == null) {
      sendError(400, context.response());
      return;
    }

    service.getCertain(todoID).onComplete(resultHandler(context, res -> {
      if (res.isEmpty())
        notFound(context);
      else {
        final String encoded = Json.encodePrettily(res.get());
        context.response()
          .putHeader("content-type", "application/json")
          .end(encoded);
      }
    }));
  }

  private void handleGetAll(RoutingContext context) {
    service.getAll().onComplete(resultHandler(context, res -> {
      if (res == null) {
        serviceUnavailable(context);
      } else {
        final String encoded = Json.encodePrettily(res);
        context.response()
          .putHeader("content-type", "application/json")
          .end(encoded);
      }
    }));
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
      service.update(todoID, newTodo)
        .onComplete(resultHandler(context, res -> {
          if (res == null)
            notFound(context);
          else {
            final String encoded = Json.encodePrettily(res);
            context.response()
              .putHeader("content-type", "application/json")
              .end(encoded);
          }
        }));
    } catch (DecodeException e) {
      badRequest(context);
    }
  }

  private Handler<AsyncResult<Boolean>> deleteResultHandler(RoutingContext context) {
    return res -> {
      if (res.succeeded()) {
        if (res.result()) {
          context.response().setStatusCode(204).end();
        } else {
          serviceUnavailable(context);
        }
      } else {
        serviceUnavailable(context);
      }
    };
  }

  private void handleDeleteOne(RoutingContext context) {
    String todoID = context.request().getParam("todoId");
    service.delete(todoID)
      .onComplete(deleteResultHandler(context));
  }

  private void handleDeleteAll(RoutingContext context) {
    service.deleteAll()
      .onComplete(deleteResultHandler(context));
  }

  private void sendError(int statusCode, HttpServerResponse response) {
    response.setStatusCode(statusCode).end();
  }

  private void notFound(RoutingContext context) {
    context.response().setStatusCode(404).end();
  }

  private void badRequest(RoutingContext context) {
    context.response().setStatusCode(400).end();
  }

  private void serviceUnavailable(RoutingContext context) {
    context.response().setStatusCode(503).end();
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
