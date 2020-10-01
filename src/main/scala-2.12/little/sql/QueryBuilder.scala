/*
 * Copyright 2019 Carlos Conyers
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

import java.sql.{ Connection, PreparedStatement, ResultSet, Types }

import scala.collection.GenTraversableOnce
import scala.collection.mutable.ListBuffer
import scala.util.Try

/**
 * Provides interface to incrementally build and execute SQL statements.
 *
 * `QueryBuilder` is an immutable structure. A new builder is returned with each
 * requested modification, and a new statement and result set are created on
 * each requested execution.
 *
 * {{{
 * import java.sql.Connection
 *
 * import little.sql.Implicits._
 * import little.sql.QueryBuilder
 *
 * implicit val conn: Connection = ???
 *
 * QueryBuilder("select * from users where group = ? and enabled = ?")
 *  .params("staff", true) // Set input parameter values
 *  .maxRows(10) // Limit result set to 10 rows
 *  .foreach { rs => printf(s"uid=%d%n", rs.getInt("id")) } // Use implicit connection
 *
 * }}}
 */
trait QueryBuilder {
  /** Gets SQL. */
  def sql: String

  /** Gets parameters. */
  def params: Seq[InParam]

  /** Sets parameters. */
  def params(values: InParam*): QueryBuilder

  /** Gets query timeout. */
  def queryTimeout: Int

  /** Sets query timeout. */
  def queryTimeout(value: Int): QueryBuilder

  /** Gets max rows. */
  def maxRows: Int

  /** Sets max rows. */
  def maxRows(value: Int): QueryBuilder

  /** Gets fetch size. */
  def fetchSize: Int

  /** Sets fetch size. */
  def fetchSize(value: Int): QueryBuilder

  /**
   * Executes statement and passes result to supplied handler.
   *
   * @param handler execution handler
   * @param conn connection to execute statement
   */
  def execute[T](handler: Execution => T)(implicit conn: Connection): T

  /**
   * Executes query and passes result set to supplied handler.
   *
   * @param handler result set handler
   * @param conn connection to execute query
   */
  def withResultSet[T](handler: ResultSet => T)(implicit conn: Connection): T

  /**
   * Executes update and returns update count.
   *
   * @param conn connection to execute update
   */
  def getUpdateCount(implicit conn: Connection): Long

  /**
   * Executes query and invokes supplied function for each row of result set.
   *
   * @param f function
   * @param conn connection to execute query
   */
  def foreach(f: ResultSet => Unit)(implicit conn: Connection): Unit

  /**
   * Executes query and maps first row of result set using supplied function.
   *
   * If the result set is not empty, and if the supplied function's return
   * value is not null, then `Some` value is returned; otherwise, `None` is
   * returned.
   *
   * @param f function
   * @param conn connection to execute query
   */
  def first[T](f: ResultSet => T)(implicit conn: Connection): Option[T]

  /**
   * Executes query and maps each row of result set using supplied function.
   *
   * @param f map function
   * @param conn connection to execute query
   */
  def map[T](f: ResultSet => T)(implicit conn: Connection): Seq[T]

  /**
   * Executes query and builds collection using elements mapped from each row of
   * result set.
   *
   * @param f map function
   * @param conn connection to execute query
   */
  def flatMap[T](f: ResultSet => GenTraversableOnce[T])(implicit conn: Connection): Seq[T]

  /**
   * Executes query and folds result set to single value using given initial
   * value and binary operator.
   *
   * @param init initial value
   * @param op binary operator
   * @param conn connection to execute query
   */
  def fold[T](init: T)(op: (T, ResultSet) => T)(implicit conn: Connection): T
}

/** Provides factory method for QueryBuilder. */
object QueryBuilder {
  /**
   * Creates QueryBuilder initialized with supplied SQL.
   *
   * @param sql SQL with which to initialize query builder
   */
  def apply(sql: String): QueryBuilder = QueryBuilderImpl(sql)
}

private case class QueryBuilderImpl(sql: String, params: Seq[InParam] = Nil, queryTimeout: Int = 0, maxRows: Int = 0, fetchSize: Int = 0) extends QueryBuilder {
  require(sql != null)

  def params(values: InParam*): QueryBuilder =
    copy(params = values)

  def queryTimeout(value: Int): QueryBuilder =
    copy(queryTimeout = value)

  def maxRows(value: Int): QueryBuilder =
    copy(maxRows = value)

  def fetchSize(value: Int): QueryBuilder =
    copy(fetchSize = value)

  def execute[T](f: Execution => T)(implicit conn: Connection): T =
    withStatement { stmt =>
      stmt.execute() match {
        case true =>
          try f(Query(stmt.getResultSet))
          finally Try(stmt.getResultSet.close())
        case false =>
          f(Update(stmt.getUpdateCount))
      }
    }


  def withResultSet[T](f: ResultSet => T)(implicit conn: Connection): T =
    withStatement { stmt =>
      val rs = stmt.executeQuery()
      try f(rs)
      finally Try(rs.close())
    }

  def getUpdateCount(implicit conn: Connection): Long =
    withStatement { stmt => stmt.executeUpdate() }

  def foreach(f: ResultSet => Unit)(implicit conn: Connection): Unit =
    withResultSet { rs =>
      while (rs.next())
        f(rs)
    }

  def first[T](f: ResultSet => T)(implicit conn: Connection): Option[T] =
    maxRows(1).withResultSet { rs =>
      if (rs.next())
        Option(f(rs))
      else None
    }

  def map[T](f: ResultSet => T)(implicit conn: Connection): Seq[T] =
    withResultSet { rs =>
      val values = new ListBuffer[T]
      while (rs.next())
        values += f(rs)
      values
    }

  def flatMap[T](f: ResultSet => GenTraversableOnce[T])(implicit conn: Connection): Seq[T] =
    withResultSet { rs =>
      val values = new ListBuffer[T]
      while (rs.next())
        f(rs).foreach(values.+=)
      values
    }

  def fold[T](init: T)(op: (T, ResultSet) => T)(implicit conn: Connection): T =
    withResultSet { rs =>
      var value = init
      while (rs.next())
        value = op(value, rs)
      value
    }

  private def withStatement[T](f: PreparedStatement => T)(implicit conn: Connection): T = {
    val stmt = conn.prepareStatement(sql)

    try {
      params.zipWithIndex.foreach {
        case (null, index) =>
          stmt.setNull(index + 1, Types.NULL)
        case (param, index) =>
          param.isNull match {
            case true  => stmt.setNull(index + 1, param.sqlType)
            case false => stmt.setObject(index + 1, param.value, param.sqlType)
          }
      }

      if (queryTimeout > 0) Try(stmt.setQueryTimeout(queryTimeout))
      if (maxRows > 0) stmt.setMaxRows(maxRows)
      if (fetchSize > 0) stmt.setFetchSize(maxRows)

      f(stmt)
    } finally Try(stmt.close())
  }
}

