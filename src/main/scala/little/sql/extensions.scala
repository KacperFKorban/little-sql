/*
 * Copyright 2021 Carlos Conyers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package little.sql

import java.sql.{ Connection, PreparedStatement, ResultSet, Statement, Date, Time, Timestamp, Types }
import java.time.{ Instant, LocalDate, LocalDateTime, LocalTime }

import javax.sql.DataSource

import scala.collection.mutable.ListBuffer
import scala.language.{ higherKinds, implicitConversions }
import scala.util.Try

import TimeConverters.*

/** Provides extension methods for `javax.sql.DataSource`. */
implicit class DataSourceMethods(dataSource: DataSource) extends AnyVal:
  /**
   * Creates Connection and passes it to supplied function. Connection is
   * closed on function's return.
   *
   * @param f function
   *
   * @return value from supplied function
   */
  def withConnection[T](f: Connection => T): T =
    val conn = dataSource.getConnection()
    try f(conn)
    finally Try(conn.close())

  /**
   * Creates Connection and passes it to supplied function. Connection is
   * closed on function's return.
   *
   * @param user database user
   * @param password database password
   * @param f function
   *
   * @return value from supplied function
   */
  def withConnection[T](user: String, password: String)(f: Connection => T): T =
    val conn = dataSource.getConnection(user, password)
    try f(conn)
    finally Try(conn.close())

/**
 * Provides extension methods for `java.sql.Connection`.
 *
 * {{{
 * import scala.language.implicitConversions
 *
 * import little.sql.{ *, given }
 *
 * val connector = Connector("jdbc:h2:~/test", "sa", "s3cr3t", "org.h2.Driver")
 *
 * connector.withConnection { conn =>
 *   val statements = Seq(
 *     "drop table prog_lang if exists",
 *     "create table prog_lang (id int, name text)",
 *     "insert into prog_lang (id, name) values (1, 'basic'), (2, 'pascal'), (3, 'c')",
 *     "select * from prog_lang"
 *   )
 *
 *   statements.foreach { sql =>
 *     // Execute SQL and handle execution result accordingly
 *     conn.execute(sql) {
 *       // If update is executed print update count
 *       case Update(count) ⇒ println(s"Update Count: \$count")
 *
 *       // If query is executed print values of each row in ResultSet
 *       case Query(resultSet) =>
 *         while (resultSet.next())
 *           printf("id: %d, name: %s%n", resultSet.getInt("id"), resultSet.getString("name"))
 *     }
 *   }
 * }
 * }}}
 */
implicit class ConnectionMethods(connection: Connection) extends AnyVal:
  /**
   * Executes SQL and passes Execution to supplied function.
   *
   * @param sql SQL
   * @param params parameters
   * @param queryTimeout maximum number of seconds to wait for execution
   * @param maxRows maximum number of rows to return in result set
   * @param fetchSize number of result set rows to fetch on each retrieval
   *   from database
   * @param f function
   */
  def execute[T](sql: String, params: Seq[InParam] = Nil, queryTimeout: Int = 0, maxRows: Int = 0, fetchSize: Int = 0)(f: Execution => T): T =
    QueryBuilder(sql)
      .params(params)
      .queryTimeout(queryTimeout)
      .maxRows(maxRows)
      .fetchSize(fetchSize)
      .execute(f)(using connection)

  /**
   * Executes query and passes ResultSet to supplied function.
   *
   * @param sql SQL query
   * @param params parameters
   * @param queryTimeout maximum number of seconds to wait for execution
   * @param maxRows maximum number of rows to return in result set
   * @param fetchSize number of result set rows to fetch on each retrieval
   *   from database
   * @param f function
   */
  def query[T](sql: String, params: Seq[InParam] = Nil, queryTimeout: Int = 0, maxRows: Int = 0, fetchSize: Int = 0)(f: ResultSet => T): T =
    QueryBuilder(sql)
      .params(params)
      .queryTimeout(queryTimeout)
      .maxRows(maxRows)
      .fetchSize(fetchSize)
      .withResultSet(f)(using connection)

  /**
   * Executes update and returns update count.
   *
   * @param sql SQL update
   * @param params parameters
   * @param queryTimeout maximum number of seconds to wait for execution
   */
  def update(sql: String, params: Seq[InParam] = Nil, queryTimeout: Int = 0): Long =
    QueryBuilder(sql)
      .params(params)
      .queryTimeout(queryTimeout)
      .getUpdateCount(using connection)

  /**
   * Executes batch of generated statements and returns results.
   *
   * @param generator SQL generator
   */
  def batch(generator: () => Iterable[String]): Array[Int] =
    val stmt = connection.createStatement()

    try
      generator().foreach(sql => stmt.addBatch(sql))
      stmt.executeBatch()
    finally
      Try(stmt.close())

  /**
   * Executes batch of statements with generated parameter values and returns
   * results.
   *
   * The generator must return sets of parameter values that satisfy the
   * supplied SQL.
   *
   * @param sql SQL from which prepared statement is created
   * @param generator parameter value generator
   */
  def batch(sql: String)(generator: () => Iterable[Seq[InParam]]): Array[Int] =
    val stmt = connection.prepareStatement(sql)

    try
      generator().foreach(params => stmt.addBatch(params))
      stmt.executeBatch()
    finally
      Try(stmt.close())

  /**
   * Executes query and invokes supplied function for each row of ResultSet.
   *
   * @param sql SQL query
   * @param params parameters
   * @param queryTimeout maximum number of seconds to wait for execution
   * @param maxRows maximum number of rows to return in result set
   * @param fetchSize number of result set rows to fetch on each retrieval
   *   from database
   * @param f function
   */
  def foreach(sql: String, params: Seq[InParam] = Nil, queryTimeout: Int = 0, maxRows: Int = 0, fetchSize: Int = 0)(f: ResultSet => Unit): Unit =
    QueryBuilder(sql)
      .params(params)
      .queryTimeout(queryTimeout)
      .maxRows(maxRows)
      .fetchSize(fetchSize)
      .foreach(f)(using connection)

  /**
   * Executes query and maps first row of ResultSet using supplied function.
   *
   * If the result set is not empty, and if the supplied function's return
   * value is not null, then `Some` value is returned; otherwise, `None` is
   * returned.
   *
   * @param sql SQL query
   * @param params parameters
   * @param queryTimeout maximum number of seconds to wait for execution
   * @param f function
   *
   * @return value from supplied function
   */
  def first[T](sql: String, params: Seq[InParam] = Nil, queryTimeout: Int = 0)(f: ResultSet => T): Option[T] =
    QueryBuilder(sql)
      .params(params)
      .queryTimeout(queryTimeout)
      .first(f)(using connection)

  /**
   * Executes query and maps each row of ResultSet using supplied function.
   *
   * @param sql SQL query
   * @param params parameters
   * @param queryTimeout maximum number of seconds to wait for execution
   * @param maxRows maximum number of rows to return in result set
   * @param fetchSize number of result set rows to fetch on each retrieval
   *   from database
   * @param f map function
   */
  def map[T](sql: String, params: Seq[InParam] = Nil, queryTimeout: Int = 0, maxRows: Int = 0, fetchSize: Int = 0)(f: ResultSet => T): Seq[T] =
    QueryBuilder(sql)
      .params(params)
      .queryTimeout(queryTimeout)
      .maxRows(maxRows)
      .fetchSize(fetchSize)
      .map(f)(using connection)

  /**
   * Executes query and builds a collection using the elements mapped from
   * each row of ResultSet.
   *
   * @param sql SQL query
   * @param params parameters
   * @param queryTimeout maximum number of seconds to wait for execution
   * @param maxRows maximum number of rows to return in result set
   * @param fetchSize number of result set rows to fetch on each retrieval
   *   from database
   * @param f map function
   */
  def flatMap[T](sql: String, params: Seq[InParam] = Nil, queryTimeout: Int = 0, maxRows: Int = 0, fetchSize: Int = 0)(f: ResultSet => Iterable[T]): Seq[T] =
    QueryBuilder(sql)
      .params(params)
      .queryTimeout(queryTimeout)
      .maxRows(maxRows)
      .fetchSize(fetchSize)
      .flatMap(f)(using connection)

  /**
   * Creates Statement and passes it to supplied function. Statement is closed
   * on function's return.
   *
   * @param f function
   *
   * @return value from supplied function
   */
  def withStatement[T](f: Statement => T): T =
    val stmt = connection.createStatement()
    try f(stmt)
    finally Try(stmt.close())

  /**
   * Creates PreparedStatement and passes it to supplied function. Statement
   * is closed on function's return.
   *
   * @param sql SQL statement
   * @param f function
   *
   * @return value from supplied function
   */
  def withPreparedStatement[T](sql: String)(f: PreparedStatement => T): T =
    val stmt = connection.prepareStatement(sql)
    try f(stmt)
    finally Try(stmt.close())

/**
 * Provides extension methods for `java.sql.Statement`.
 *
 * @see [[PreparedStatementMethods]]
 */
implicit class StatementMethods(statement: Statement) extends AnyVal:
  /**
   * Executes SQL and passes Execution to supplied function.
   *
   * @param sql SQL statement
   * @param f function
   */
  def execute[T](sql: String)(f: Execution => T): T =
    statement.execute(sql) match
      case true =>
        val rs = statement.getResultSet
        try f(Query(rs))
        finally Try(rs.close())
      case false =>
        f(Update(statement.getUpdateCount))

  /**
   * Executes query and passes ResultSet to supplied function.
   *
   * @param sql SQL query
   * @param f function
   */
  def query[T](sql: String)(f: ResultSet => T): T =
    val rs = statement.executeQuery(sql)
    try f(rs)
    finally Try(rs.close())

  /**
   * Executes query and invokes supplied function for each row of ResultSet.
   *
   * @param sql SQL query
   * @param f function
   */
  def foreach(sql: String)(f: ResultSet => Unit): Unit =
    query(sql) { _.foreach(f) }

  /**
   * Executes query and maps first row of ResultSet using supplied function.
   *
   * If the result set is not empty, and if the supplied function's return
   * value is not null, then `Some` value is returned; otherwise, `None` is
   * returned.
   *
   * @param sql SQL query
   * @param f function
   */
  def first[T](sql: String)(f: ResultSet => T): Option[T] =
    Try(statement.setMaxRows(1))
    query(sql) { _.next(f) }

  /**
   * Executes query and maps each row of ResultSet using supplied function.
   *
   * @param sql SQL query
   * @param params parameters
   * @param f map function
   */
  def map[T](sql: String)(f: ResultSet => T): Seq[T] =
    fold(sql)(new ListBuffer[T]) { _ += f(_) }.toSeq

  /**
   * Executes query and builds a collection using the elements mapped from
   * each row of ResultSet.
   *
   * @param sql SQL query
   * @param params parameters
   * @param f map function
   */
  def flatMap[T](sql: String)(f: ResultSet => Iterable[T]): Seq[T] =
    fold(sql)(new ListBuffer[T]) { (buf, rs) =>
      f(rs).foreach(buf.+=)
      buf
    }.toSeq

  private def fold[T](sql: String)(z: T)(op: (T, ResultSet) => T): T =
    val rs = statement.executeQuery(sql)
    try rs.fold(z)(op)
    finally Try(rs.close())

/**
 * Provides extension methods for `java.sql.PreparedStatement`.
 *
 * @see [[StatementMethods]]
 */
implicit class PreparedStatementMethods(statement: PreparedStatement) extends AnyVal:
  /**
   * Executes statement with parameters and passes Execution to supplied
   * function.
   *
   * @param params parameters
   * @param f function
   */
  def execute[T](params: Seq[InParam])(f: Execution => T): T =
    set(params)

    statement.execute() match
      case true =>
        val rs = statement.getResultSet
        try f(Query(rs))
        finally Try(rs.close())
      case false =>
        f(Update(statement.getUpdateCount))

  /**
   * Executes query with parameters and passes ResultSet to supplied function.
   *
   * @param params parameters
   * @param f function
   */
  def query[T](params: Seq[InParam])(f: ResultSet => T): T =
    set(params)

    val rs = statement.executeQuery()
    try f(rs)
    finally Try(rs.close())

  /**
   * Executes update with parameters and returns update count.
   *
   * @param params parameters
   */
  def update(params: Seq[InParam]): Int =
    set(params)
    statement.executeUpdate()

  /**
   * Sets parameter at index to given value.
   *
   * @param index parameter index
   * @param value parameter value
   */
  def set(index: Int, value: InParam): Unit =
    if value == null then
      statement.setNull(index, Types.NULL)
    else
      value.isNull match
        case true  => statement.setNull(index, value.sqlType)
        case false => statement.setObject(index, value.value, value.sqlType)

  /**
   * Sets parameters.
   *
   * @param index parameter index
   * @param value parameter value
   */
  def set(params: Seq[InParam]): Unit =
    params.zipWithIndex.foreach { (param, index) => set(index + 1, param) }

  /**
   * Adds parameters to batch of commands.
   *
   * @param params parameters
   */
  def addBatch(params: Seq[InParam]): Unit =
    set(params)
    statement.addBatch()

  /**
   * Executes query with parameters and invokes supplied function for each row
   * of ResultSet.
   *
   * @param params parameters
   * @param f function
   */
  def foreach(params: Seq[InParam])(f: ResultSet => Unit): Unit =
    query(params) { _.foreach(f) }

  /**
   * Executes query with parameters and maps first row of ResultSet using
   * supplied function.
   *
   * If the result set is not empty, and if the supplied function's return
   * value is not null, then `Some` value is returned; otherwise, `None` is
   * returned.
   *
   * @param params parameters
   * @param f map function
   */
  def first[T](params: Seq[InParam])(f: ResultSet => T): Option[T] =
    Try(statement.setMaxRows(1))
    query(params) { _.next(f) }

  /**
   * Executes query with parameters and maps each row of ResultSet using
   * supplied function.
   *
   * @param params parameters
   * @param f map function
   */
  def map[T](params: Seq[InParam])(f: ResultSet => T): Seq[T] =
    fold(params)(new ListBuffer[T]) {_ += f(_) }.toSeq

  /**
   * Executes query and builds a collection using the elements mapped from
   * each row of ResultSet.
   *
   * @param params parameters
   * @param f map function
   */
  def flatMap[T](params: Seq[InParam])(f: ResultSet => Iterable[T]): Seq[T] =
    fold(params)(new ListBuffer[T]) { (buf, rs) =>
      f(rs).foreach(buf.+=)
      buf
    }.toSeq

  /**
   * Sets parameter to given `LocalDate`.
   *
   * @param index parameter index
   * @param value parameter value
   */
  def setLocalDate(index: Int, value: LocalDate): Unit =
    statement.setDate(index, Date.valueOf(value))

  /**
   * Sets parameter to given `LocalTime`.
   *
   * @param index parameter index
   * @param value parameter value
   */
  def setLocalTime(index: Int, value: LocalTime): Unit =
    statement.setTime(index, Time.valueOf(value))

  /**
   * Sets parameter to given `LocalDateTime`.
   *
   * @param index parameter index
   * @param value parameter value
   */
  def setLocalDateTime(index: Int, value: LocalDateTime): Unit =
    statement.setTimestamp(index, Timestamp.valueOf(value))

  /**
   * Sets parameter to given `Instant`.
   *
   * @param index parameter index
   * @param value parameter value
   */
  def setInstant(index: Int, value: Instant): Unit =
    statement.setTimestamp(index, Timestamp.from(value))

  private def fold[T](params: Seq[InParam])(z: T)(op: (T, ResultSet) => T): T =
    set(params)

    val rs = statement.executeQuery()
    try rs.fold(z)(op)
    finally Try(rs.close())

/** Provides extension methods for `java.sql.ResultSet`. */
implicit class ResultSetMethods(resultSet: ResultSet) extends AnyVal:
  /** Gets column count. */
  def getColumnCount(): Int =
    resultSet.getMetaData.getColumnCount()

  /** Gets column labels. */
  def getColumnLabels(): Seq[String] =
    val metaData = resultSet.getMetaData()
    (1 to getColumnCount()).map(metaData.getColumnLabel).toSeq

  /**
   * Gets column value in current row.
   *
   * @tparam T type of value to return
   *
   * @param index column index
   */
  def get[T](index: Int)(using getValue: GetValueByIndex[T]): T =
    getValue(resultSet, index)

  /**
   * Gets column value in current row.
   *
   * @tparam T type of value to return
   *
   * @param label column label
   */
  def get[T](label: String)(using getValue: GetValueByLabel[T]): T =
    getValue(resultSet, label)

  /**
   * Gets column value in current row, or returns default if value is null.
   *
   * @tparam T type of value to return
   *
   * @param index column index
   * @param default default value
   */
  def getOrElse[T](index: Int, default: => T)(using getValue: GetValueByIndex[T]): T =
    getOption(index).getOrElse(default)

  /**
   * Gets column value in current row, or returns default if value is null.
   *
   * @tparam T type of value to return
   *
   * @param label column label
   * @param default default value
   */
  def getOrElse[T](label: String, default: => T)(using getValue: GetValueByLabel[T]): T =
    getOption(label).getOrElse(default)

  /**
   * Gets column value in current row if value is not null.
   *
   * @tparam T type of value to return
   *
   * @param index column index
   */
  def getOption[T](index: Int)(using getValue: GetValueByIndex[T]): Option[T] =
    val value = getValue(resultSet, index)

    resultSet.wasNull match
      case true  => None
      case false => Option(value)

  /**
   * Gets column value in current row if value is not null.
   *
   * @tparam T type of value to return
   *
   * @param label column label
   */
  def getOption[T](label: String)(using getValue: GetValueByLabel[T]): Option[T] =
    val value = getValue(resultSet, label)

    resultSet.wasNull match
      case true  => None
      case false => Option(value)

  /** Gets column value as LocalDate. */
  def getLocalDate(index: Int): LocalDate =
    GetLocalDate(resultSet, index)

  /** Gets column value as LocalTime. */
  def getLocalTime(index: Int): LocalTime =
    GetLocalTime(resultSet, index)

  /** Gets column value as LocalDateTime. */
  def getLocalDateTime(index: Int): LocalDateTime =
    GetLocalDateTime(resultSet, index)

  /** Gets column value as Instant. */
  def getInstant(index: Int): Instant =
    GetInstant(resultSet, index)

  /** Gets column value as LocalDate. */
  def getLocalDate(label: String): LocalDate =
    GetLocalDate(resultSet, label)

  /** Gets column value as LocalTime. */
  def getLocalTime(label: String): LocalTime =
    GetLocalTime(resultSet, label)

  /** Gets column value as LocalDateTime. */
  def getLocalDateTime(label: String): LocalDateTime =
    GetLocalDateTime(resultSet, label)

  /** Gets column value as Instant. */
  def getInstant(label: String): Instant =
    GetInstant(resultSet, label)

  /**
   * Invokes supplied function for each remaining row of ResultSet.
   *
   * @param f function
   */
  def foreach(f: ResultSet => Unit): Unit =
    while resultSet.next() do
      f(resultSet)

  /**
   * Maps next row of ResultSet using supplied function.
   *
   * If the result set has another row, and if the supplied function's return
   * value is not null, then `Some` value is returned; otherwise, `None` is
   * returned.
   *
   * @param f map function
   */
  def next[T](f: ResultSet => T): Option[T] =
    resultSet.next() match
      case true  => Option(f(resultSet))
      case false => None

  /**
   * Maps remaining rows of ResultSet using supplied function.
   *
   * @param f map function
   */
  def map[T](f: ResultSet => T): Seq[T] =
    fold(new ListBuffer[T]) { _ += f(_) }.toSeq

  /**
   * Maps remaining rows of ResultSet building a collection using elements
   * returned from map function.
   *
   * @param f map function
   */
  def flatMap[T](f: ResultSet => Iterable[T]): Seq[T] =
    fold(new ListBuffer[T]) { (buf, rs) =>
      f(rs).foreach(buf.+=)
      buf
    }.toSeq

  /**
   * Folds remaining rows of ResultSet to single value using given initial
   * value and binary operator.
   *
   * @param init initial value
   * @param op binary operator
   */
  def fold[T](init: T)(op: (T, ResultSet) => T): T =
    var res = init
    while resultSet.next() do
      res = op(res, resultSet)
    res
