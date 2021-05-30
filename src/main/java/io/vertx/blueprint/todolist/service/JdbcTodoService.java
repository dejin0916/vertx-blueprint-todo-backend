package io.vertx.blueprint.todolist.service;

import io.vertx.blueprint.todolist.entity.Todo;
import io.vertx.blueprint.todolist.entity.TodoParametersMapper;
import io.vertx.blueprint.todolist.entity.TodoRowMapper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.templates.SqlTemplate;

import java.util.*;

/**
 * Vert.x Blueprint Application - Todo Backend
 * Todo Service JDBC Implementation
 *
 * @author Eric Zhao
 */
public class JdbcTodoService implements TodoService {

  private final Vertx vertx;
  private final JsonObject config;
  private final MySQLPool client;

  private static final String SQL_CREATE = "CREATE TABLE IF NOT EXISTS `todo` (\n" +
    "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
    "  `title` varchar(255) DEFAULT NULL,\n" +
    "  `completed` tinyint(1) DEFAULT NULL,\n" +
    "  `order` int(11) DEFAULT NULL,\n" +
    "  `url` varchar(255) DEFAULT NULL,\n" +
    "  PRIMARY KEY (`id`) )";
  // 注意如果是用sql-template执行sql,参数必须为"#{param}"的形式
  private static final String SQL_INSERT = "INSERT INTO `todo` " +
    "(`id`, `title`, `completed`, `order`, `url`) VALUES (#{id}, #{title}, #{completed}, #{order}, #{url})";
  private static final String SQL_QUERY = "SELECT * FROM todo WHERE id = #{id}";
  private static final String SQL_QUERY_ALL = "SELECT * FROM todo";
  private static final String SQL_UPDATE = "UPDATE `todo`\n" +
    "SET\n" +
    "`id` = #{id},\n" +
    "`title` = #{title},\n" +
    "`completed` = #{completed},\n" +
    "`order` = #{order},\n" +
    "`url` = #{url}\n" +
    "WHERE `id` = #{id};";
  private static final String SQL_DELETE = "DELETE FROM `todo` WHERE `id` = ?";
  private static final String SQL_DELETE_ALL = "DELETE FROM `todo`";

  public JdbcTodoService(JsonObject config) {
    this(Vertx.vertx(), config);
  }

  public JdbcTodoService(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.client = MySQLPool.pool(vertx, new MySQLConnectOptions(config),
            new PoolOptions().setMaxSize(config.getInteger("max_pool_size", 30)));
  }

  @Override
  public Future<Boolean> initData() {
    Promise<Boolean> result = Promise.promise();
    client.preparedQuery(SQL_CREATE).execute()
      .onSuccess(res -> {
        Todo todo = new Todo(Math.abs(new Random().nextInt()),
                "Something to do...", false, 1, "todo/ex");
        Future<Boolean> insertResult = insert(todo);
          result.complete(insertResult.result().equals(true));
      })
      .onFailure(res -> result.fail(res.getCause()));
    return result.future();
  }

  @Override
  public Future<Boolean> insert(Todo todo) {
    System.out.println(todo.toString());
    Promise<Boolean> result = Promise.promise();
    SqlTemplate.forUpdate(client, SQL_INSERT)
      .mapFrom(TodoParametersMapper.INSTANCE)
      .execute(todo)
      .onSuccess(res -> result.complete(true))
      .onFailure(Throwable::printStackTrace);
    return result.future();
  }

  @Override
  public Future<List<Todo>> getAll() {
    Promise<List<Todo>> result = Promise.promise();
    SqlTemplate.forQuery(client, SQL_QUERY_ALL)
            .mapTo(Todo.class)
            .execute(null)
            .onSuccess(todos -> {
              List<Todo> todoList = new ArrayList<>();
              todos.forEach(todoList::add);
              result.complete(todoList);
            })
            .onFailure(Throwable::printStackTrace);
    return result.future();
  }

  @Override
  public Future<Optional<Todo>> getCertain(String todoID) {
    Promise<Optional<Todo>> result = Promise.promise();
    SqlTemplate.forQuery(client, SQL_QUERY).mapTo(TodoRowMapper.INSTANCE)
      .execute(Collections.singletonMap("id", todoID))
      .onSuccess(todos -> {
        if (todos.size() > 0) {
          result.complete(Optional.of(todos.iterator().next()));
        } else {
          result.complete(Optional.empty());
        }
      })
      .onFailure(Throwable::printStackTrace);
    return result.future();
  }

  @Override
  public Future<Todo> update(String todoId, Todo newTodo) {
    Promise<Todo> result = Promise.promise();
    this.getCertain(todoId)
      .onFailure(res -> result.fail(res.getCause()))
      .onSuccess(oldTodo -> {
        if(oldTodo.isEmpty()) {
          result.complete(null);
          return;
        }
        Todo fnTodo = oldTodo.get().merge(newTodo);
        SqlTemplate.forUpdate(client, SQL_UPDATE)
          .mapFrom(TodoParametersMapper.INSTANCE)
          .execute(fnTodo)
          .onSuccess(res -> result.complete(fnTodo))
          .onFailure(Throwable::printStackTrace);
      });
    return result.future();
  }

  @Override
  public Future<Boolean> delete(String todoId) {
    Promise<Boolean> result = Promise.promise();
    client.preparedQuery(SQL_DELETE).execute(Tuple.of(todoId))
            .onSuccess(res -> result.complete(true))
            .onFailure(res -> result.complete(false));
    return result.future();
  }

  @Override
  public Future<Boolean> deleteAll() {
    Promise<Boolean> result = Promise.promise();
    client.preparedQuery(SQL_DELETE_ALL).execute()
            .onSuccess(res -> result.complete(true))
            .onFailure(res -> result.complete(false));
    return result.future();
  }
}
