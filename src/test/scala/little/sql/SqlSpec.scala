/*
 * Copyright 2018 Carlos Conyers
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

import org.scalatest.FlatSpec
import java.sql.Connection

import Implicits._

class SqlSpec extends FlatSpec {
  private val connector = Connector(s"jdbc:h2:${sys.props("java.io.tmpdir")}/test", "sa", "", "org.h2.Driver")

  "SQL statement" should "drop table if it exists" in connector.withConnection { conn =>
    conn.update("drop table prog_lang if exists")
  }

  it should "create table" in connector.withConnection { conn =>
    conn.update("create table prog_lang (id int, name text, comments text)")
  }

  it should "insert records into table" in connector.withConnection { conn =>
    val count = conn.update("insert into prog_lang(id, name) values (1, 'basic'), (2, 'pascal'), (3, 'c')")
    assert(count == 3)
  }

  it should "select records from table" in connector.withConnection { conn =>
    conn.forEachRow("select * from prog_lang") { rs =>
      val id = rs.get[Int]("id")
      val name = rs.get[String]("name")
      val comments = rs.get[String]("comments")
    }
  }

  it should "select record having one column with null value" in connector.withConnection { conn =>
    conn.forEachRow("select * from prog_lang") { rs =>
      val id = rs.get[Option[Int]]("id")
      val name = rs.get[Option[String]]("name")
      val comments = rs.get[Option[String]]("comments")

      assert(id.isDefined)
      assert(name.isDefined)
      assert(!comments.isDefined)
    }
  }

  it should "insert records into table with null value" in connector.withConnection { conn =>
    conn.update("insert into prog_lang (id, name) values (?, ?)", Seq(None, "cobol"))
    val count: Option[Int] = conn.mapFirstRow("select count(*) from prog_lang where id is null") { rs =>
      rs.get[Int](1)
    }
    assert(count.getOrElse(0) == 1)
  }
}