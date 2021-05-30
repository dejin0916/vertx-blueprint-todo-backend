package io.vertx.blueprint.todolist.entity;

/**
 * Mapper for {@link Todo}.
 * NOTE: This class has been automatically generated from the {@link Todo} original class using Vert.x codegen.
 */
@io.vertx.codegen.annotations.VertxGen
public interface TodoParametersMapper extends io.vertx.sqlclient.templates.TupleMapper<Todo> {

  TodoParametersMapper INSTANCE = new TodoParametersMapper() {};

  default io.vertx.sqlclient.Tuple map(java.util.function.Function<Integer, String> mapping, int size, Todo params) {
    java.util.Map<String, Object> args = map(params);
    Object[] array = new Object[size];
    for (int i = 0;i < array.length;i++) {
      String column = mapping.apply(i);
      array[i] = args.get(column);
    }
    return io.vertx.sqlclient.Tuple.wrap(array);
  }

  default java.util.Map<String, Object> map(Todo obj) {
    java.util.Map<String, Object> params = new java.util.HashMap<>();
    params.put("completed", obj.isCompleted());
    params.put("id", obj.getId());
    params.put("order", obj.getOrder());
    params.put("title", obj.getTitle());
    params.put("url", obj.getUrl());
    return params;
  }
}
