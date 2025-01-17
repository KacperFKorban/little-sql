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

import java.sql.{ ResultSet, Statement, Date, Time, Timestamp, Types }
import java.time.{ Instant, LocalDate, LocalDateTime, LocalTime }

import scala.language.{ higherKinds, implicitConversions }

import TimeConverters.*

/** Converts String to InParam. */
given stringToInParam: Conversion[String, InParam] with
  def apply(value: String) = InParam(value)

/** Converts Boolean to InParam. */
given booleanToInParam: Conversion[Boolean, InParam] with
  def apply(value: Boolean) = InParam(value)

/** Converts Byte to InParam. */
given byteToInParam: Conversion[Byte, InParam] with
  def apply(value: Byte) = InParam(value)

/** Converts Short to InParam. */
given shortToInParam: Conversion[Short, InParam] with
  def apply(value: Short) = InParam(value)

/** Converts Int to InParam. */
given intToInParam: Conversion[Int, InParam] with
  def apply(value: Int) = InParam(value)

/** Converts Long to InParam. */
given longToInParam: Conversion[Long, InParam] with
  def apply(value: Long) = InParam(value)

/** Converts Float to InParam. */
given floatToInParam: Conversion[Float, InParam] with
  def apply(value: Float) = InParam(value)

/** Converts Double to InParam. */
given doubleToInParam: Conversion[Double, InParam] with
  def apply(value: Double) = InParam(value)

/** Converts BigDecimal to InParam. */
given bigDecimalToInParam: Conversion[BigDecimal, InParam] with
  def apply(value: BigDecimal) = InParam(value)

/** Converts Date to InParam. */
given dateToInParam: Conversion[Date, InParam] with
  def apply(value: Date) = InParam(value)

/** Converts Time to InParam. */
given timeToInParam: Conversion[Time, InParam] with
  def apply(value: Time) = InParam(value)

/** Converts Timestamp to InParam. */
given timestampToInParam: Conversion[Timestamp, InParam] with
  def apply(value: Timestamp) = InParam(value)

/** Converts LocalDate to InParam. */
given localDateToInParam: Conversion[LocalDate, InParam] with
  def apply(value: LocalDate) = InParam(value)

/** Converts LocalTime to InParam. */
given localTimeToInParam: Conversion[LocalTime, InParam] with
  def apply(value: LocalTime) = InParam(value)

/** Converts LocalDateTime to InParam. */
given localDateTimeToInParam: Conversion[LocalDateTime, InParam] with
  def apply(value: LocalDateTime) = InParam(value)

/** Converts Instant to InParam. */
given instantToInParam: Conversion[Instant, InParam] with
  def apply(value: Instant) = InParam(value)

/** Converts Option[T] to InParam. */
given optionToInParam[T](using convert: Conversion[T, InParam]): Conversion[Option[T], InParam] with
  def apply(value: Option[T]) = value.map(convert).getOrElse(InParam.Null)

/** Converts Any to InParam. */
given anyToInParam: Conversion[Any, InParam] with
  def apply(value: Any): InParam =
    value match
      case null             => InParam.Null
      case x: String        => InParam(x)
      case x: Boolean       => InParam(x)
      case x: Byte          => InParam(x)
      case x: Short         => InParam(x)
      case x: Int           => InParam(x)
      case x: Long          => InParam(x)
      case x: Float         => InParam(x)
      case x: Double        => InParam(x)
      case x: BigDecimal    => InParam(x)
      case x: Date          => InParam(x)
      case x: Time          => InParam(x)
      case x: Timestamp     => InParam(x)
      case x: LocalDate     => InParam(x)
      case x: LocalTime     => InParam(x)
      case x: LocalDateTime => InParam(x)
      case x: Instant       => InParam(x)
      case x: Some[_]       => anyToInParam(x.get)
      case None             => InParam.Null
      case x: InParam       => x
      case x: Any           => throw IllegalArgumentException(s"Cannot convert instance of ${x.getClass.getName} to little.sql.InParam")

/** Converts Seq[T] to Seq[InParam]. */
given seqToSeqInParam[T](using convert: Conversion[T, InParam]): Conversion[Seq[T], Seq[InParam]] with
  def apply(values: Seq[T]) = values.map(convert)

/** Converts Map[T] to Seq[InParam]. */
given mapToMapInParam[T](using convert: Conversion[T, InParam]): Conversion[Map[String, T], Map[String, InParam]] with
  def apply(values: Map[String, T]) = values.map { (name, value) => name -> convert(value) }

/** Converts Map[T] to Seq[InParam]. */
given tupleToTupleInParam[T](using convert: Conversion[T, InParam]): Conversion[(String, T), (String, InParam)] with
  def apply(value: (String, T)) = value._1 -> convert(value._2)

/** Gets String from ResultSet. */
given GetString: GetValue[String] with
  def apply(rs: ResultSet, index: Int): String = rs.getString(index)
  def apply(rs: ResultSet, label: String): String = rs.getString(label)

/** Gets Boolean from ResultSet. */
given GetBoolean: GetValue[Boolean] with
  def apply(rs: ResultSet, index: Int): Boolean = rs.getBoolean(index)
  def apply(rs: ResultSet, label: String): Boolean = rs.getBoolean(label)

/** Gets Byte from ResultSet. */
given GetByte: GetValue[Byte] with
  def apply(rs: ResultSet, index: Int): Byte = rs.getByte(index)
  def apply(rs: ResultSet, label: String): Byte = rs.getByte(label)

/** Gets Int from ResultSet. */
given GetInt: GetValue[Int] with
  def apply(rs: ResultSet, index: Int): Int = rs.getInt(index)
  def apply(rs: ResultSet, label: String): Int = rs.getInt(label)

/** Gets Short from ResultSet. */
given GetShort: GetValue[Short] with
  def apply(rs: ResultSet, index: Int): Short = rs.getShort(index)
  def apply(rs: ResultSet, label: String): Short = rs.getShort(label)

/** Gets Long from ResultSet. */
given GetLong: GetValue[Long] with
  def apply(rs: ResultSet, index: Int): Long = rs.getLong(index)
  def apply(rs: ResultSet, label: String): Long = rs.getLong(label)

/** Gets Float from ResultSet. */
given GetFloat: GetValue[Float] with
  def apply(rs: ResultSet, index: Int): Float = rs.getFloat(index)
  def apply(rs: ResultSet, label: String): Float = rs.getFloat(label)

/** Gets Double from ResultSet. */
given GetDouble: GetValue[Double] with
  def apply(rs: ResultSet, index: Int): Double = rs.getDouble(index)
  def apply(rs: ResultSet, label: String): Double = rs.getDouble(label)

/** Gets BigDecimal from ResultSet. */
given GetBigDecimal: GetValue[BigDecimal] with
  def apply(rs: ResultSet, index: Int): BigDecimal = rs.getBigDecimal(index)
  def apply(rs: ResultSet, label: String): BigDecimal = rs.getBigDecimal(label)

/** Gets Date from ResultSet. */
given GetDate: GetValue[Date] with
  def apply(rs: ResultSet, index: Int): Date = rs.getDate(index)
  def apply(rs: ResultSet, label: String): Date = rs.getDate(label)

/** Gets Time from ResultSet. */
given GetTime: GetValue[Time] with
  def apply(rs: ResultSet, index: Int): Time = rs.getTime(index)
  def apply(rs: ResultSet, label: String): Time = rs.getTime(label)

/** Gets Timestamp from ResultSet. */
given GetTimestamp: GetValue[Timestamp] with
  def apply(rs: ResultSet, index: Int): Timestamp = rs.getTimestamp(index)
  def apply(rs: ResultSet, label: String): Timestamp = rs.getTimestamp(label)

/** Gets LocalDate from ResultSet. */
given GetLocalDate: GetValue[LocalDate] with
  def apply(rs: ResultSet, index: Int): LocalDate = dateToLocalDate(rs.getDate(index))
  def apply(rs: ResultSet, label: String): LocalDate = dateToLocalDate(rs.getDate(label))

/** Gets LocalTime from ResultSet. */
given GetLocalTime: GetValue[LocalTime] with
  def apply(rs: ResultSet, index: Int): LocalTime = timeToLocalTime(rs.getTime(index))
  def apply(rs: ResultSet, label: String): LocalTime = timeToLocalTime(rs.getTime(label))

/** Gets LocalDateTime from ResultSet. */
given GetLocalDateTime: GetValue[LocalDateTime] with
  def apply(rs: ResultSet, index: Int): LocalDateTime = timestampToLocalDateTime(rs.getTimestamp(index))
  def apply(rs: ResultSet, label: String): LocalDateTime = timestampToLocalDateTime(rs.getTimestamp(label))

/** Gets Instant from ResultSet. */
given GetInstant: GetValue[Instant] with
  def apply(rs: ResultSet, index: Int): Instant = timestampToInstant(rs.getTimestamp(index))
  def apply(rs: ResultSet, label: String): Instant = timestampToInstant(rs.getTimestamp(label))
