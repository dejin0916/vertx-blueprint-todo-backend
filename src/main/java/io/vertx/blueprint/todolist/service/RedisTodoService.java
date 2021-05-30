package io.vertx.blueprint.todolist.service;

import io.vertx.blueprint.todolist.Constants;
import io.vertx.blueprint.todolist.entity.Todo;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Vert.x Blueprint Application - Todo Backend
 * Todo Service Redis Implementation
 *
 * @author Eric Zhao
 */
public class RedisTodoService implements TodoService {

  private final RedisAPI redisAPI;

  public RedisTodoService(RedisOptions config) {
    this(Vertx.vertx(), config);
  }

  public RedisTodoService(Vertx vertx, RedisOptions config) {
    Redis redis = Redis.createClient(vertx, config);
    this.redisAPI = RedisAPI.api(redis);
  }

  @Override
  public Future<Boolean> initData() {
    return insert(new Todo(Math.abs(new java.util.Random().nextInt()),
      "Something to do...", false, 1, "todo/ex"));
  }

  @Override
  public Future<Boolean> insert(Todo todo) {
    Promise<Boolean> result = Promise.promise();
    final String encoded = Json.encodePrettily(todo);
    List<String> params = new ArrayList<>();
    params.add(Constants.REDIS_TODO_KEY);
    params.add(String.valueOf(todo.getId()));
    params.add(encoded);
    redisAPI.hset(params)
      .onSuccess(res -> result.complete(true))
      .onFailure(res -> result.fail(res.getCause()));
    return result.future();
  }

  @Override
  public Future<List<Todo>> getAll() {
    Promise<List<Todo>> result = Promise.promise();
    redisAPI.hvals(Constants.REDIS_TODO_KEY)
      .onSuccess(res -> result.complete(
        res.stream().map(x -> new Todo(x.toString()))
        .collect(Collectors.toList())
      ))
      .onFailure(res -> result.fail(res.getCause()));
    return result.future();
  }

  @Override
  public Future<Optional<Todo>> getCertain(String todoID) {
    Promise<Optional<Todo>> result = Promise.promise();
    redisAPI.hget(Constants.REDIS_TODO_KEY, todoID)
      .onSuccess(res -> result.complete(Optional.ofNullable(
        res == null ? null : new Todo(res.toString()))))
    .onFailure(res -> result.fail(res.getCause()));
    return result.future();
  }

  @Override
  public Future<Todo> update(String todoId, Todo newTodo) {
    return this.getCertain(todoId).compose(old -> {
      if (old.isPresent()) {
        Todo fnTodo = old.get().merge(newTodo);
        return this.insert(fnTodo)
          .map(r -> r ? fnTodo : null);
      } else {
        return Future.succeededFuture();
      }
    });
  }

  @Override
  public Future<Boolean> delete(String todoId) {
    List<String> params = new ArrayList<>();
    params.add(Constants.REDIS_TODO_KEY);
    params.add(todoId);
    return deleteProcess(params);
  }

  @Override
  public Future<Boolean> deleteAll() {
    List<String> params = new ArrayList<>();
    params.add(Constants.REDIS_TODO_KEY);
    return deleteProcess(params);
  }

  private Future<Boolean> deleteProcess(List<String> params) {
    Promise<Boolean> result = Promise.promise();
    redisAPI.del(params)
      .onSuccess(res -> result.complete(true))
      .onFailure(res -> result.complete(false));
    return result.future();
  }
}
