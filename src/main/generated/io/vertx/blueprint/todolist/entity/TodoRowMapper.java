package io.vertx.blueprint.todolist.entity;

/**
 * Mapper for {@link Todo}.
 * NOTE: This class has been automatically generated from the {@link Todo} original class using Vert.x codegen.
 */
@io.vertx.codegen.annotations.VertxGen
public interface TodoRowMapper extends io.vertx.sqlclient.templates.RowMapper<Todo> {

  @io.vertx.codegen.annotations.GenIgnore
  TodoRowMapper INSTANCE = new TodoRowMapper() { };

  @io.vertx.codegen.annotations.GenIgnore
  java.util.stream.Collector<io.vertx.sqlclient.Row, ?, java.util.List<Todo>> COLLECTOR = java.util.stream.Collectors.mapping(INSTANCE::map, java.util.stream.Collectors.toList());

  @io.vertx.codegen.annotations.GenIgnore
  default Todo map(io.vertx.sqlclient.Row row) {
    Todo obj = new Todo();
    Object val;
    int idx;
    if ((idx = row.getColumnIndex("completed")) != -1 && (val = row.getBoolean(idx)) != null) {
      obj.setCompleted((java.lang.Boolean)val);
    }
    if ((idx = row.getColumnIndex("id")) != -1 && (val = row.getInteger(idx)) != null) {
      obj.setId((int)val);
    }
    if ((idx = row.getColumnIndex("order")) != -1 && (val = row.getInteger(idx)) != null) {
      obj.setOrder((java.lang.Integer)val);
    }
    if ((idx = row.getColumnIndex("title")) != -1 && (val = row.getString(idx)) != null) {
      obj.setTitle((java.lang.String)val);
    }
    if ((idx = row.getColumnIndex("url")) != -1 && (val = row.getString(idx)) != null) {
      obj.setUrl((java.lang.String)val);
    }
    return obj;
  }
}
