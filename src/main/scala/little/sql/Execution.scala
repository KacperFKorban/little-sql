package little.sql

import java.sql.ResultSet

/**
 * Represents result of either update or query. If update, result can be
 * obtained via {@code count}; otherwise, if query, result can be obtained via
 * {@code resultSet}.
 *
 * @see [[Implicits.ConnectionType.execute]]
 */
sealed abstract class Execution {
  /**
   * Returns {@code true} if this execution represents result of update;
   * otherwise, returns {@code false}.
   */
  def isUpdate: Boolean

  /**
   * Returns {@code true} if this execution represents result of query;
   * otherwise, returns {@code false}.
   */
  def isQuery: Boolean

  /**
   * Gets update count.
   *
   * @throws NoSuchElementException if this execution is not result of update
   */
  def count: Int

  /**
   * Gets result set.
   *
   * @throws NoSuchElementException if this execution is not result of query
   */
  def resultSet: ResultSet
}

/** Execution factory */
object Execution {
  /** Creates Update(count). */
  def apply(count: Int) = Update(count)

  /** Creates Query(resultSet). */
  def apply(resultSet: ResultSet) = Query(resultSet)
}

/**
 * Represents result of update.
 *
 * @see [[Query]], [[Implicits.ConnectionType.execute]]
 *
 * @constructor
 * @param count update count
 */
final case class Update(count: Int) extends Execution {
  val isUpdate = true
  val isQuery = false

  def resultSet: ResultSet = throw new NoSuchElementException("resultSet")
}

/**
 * Represents result of query.
 *
 * @see [[Update]], [[Implicits.ConnectionType.execute]]
 *
 * @constructor
 * @param resultSet result set
 */
final case class Query(resultSet: ResultSet) extends Execution {
  val isUpdate = false
  val isQuery = true

  def count: Int = throw new NoSuchElementException("count")
}
