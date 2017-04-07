package org.apache.spark.carbondata.restructure

import java.io.File

import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.common.util.QueryTest
import org.apache.spark.sql.test.TestQueryExecutor
import org.scalatest.BeforeAndAfterAll


class AlterTableRevertTestCase extends QueryTest with BeforeAndAfterAll {

  override def beforeAll() {
    sql("drop table if exists reverttest")
    sql(
      "CREATE TABLE reverttest(intField int,stringField string,timestampField timestamp," +
      "decimalField decimal(6,2)) STORED BY 'carbondata'")
    sql(s"LOAD DATA LOCAL INPATH '$resourcesPath/restructure/data4.csv' INTO TABLE reverttest " +
        s"options('FILEHEADER'='intField,stringField,timestampField,decimalField')")
  }

  test("test to revert new added columns on failure") {
    intercept[RuntimeException] {
      hiveClient.runSqlHive("set hive.security.authorization.enabled=true")
      sql(
        "Alter table reverttest add columns(newField string) TBLPROPERTIES" +
        "('DICTIONARY_EXCLUDE'='newField','DEFAULT.VALUE.charfield'='def')")
      hiveClient.runSqlHive("set hive.security.authorization.enabled=false")
      intercept[AnalysisException] {
        sql("select newField from reverttest")
      }
    }
  }

  test("test to revert table name on failure") {
    intercept[RuntimeException] {
      new File(TestQueryExecutor.warehouse + "/reverttest_fail").mkdir()
      sql("alter table reverttest rename to reverttest_fail")
      new File(TestQueryExecutor.warehouse + "/reverttest_fail").delete()
    }
    val result = sql("select * from reverttest").count()
    assert(result.equals(1L))
  }

  test("test to revert drop columns on failure") {
    intercept[Exception] {
      hiveClient.runSqlHive("set hive.security.authorization.enabled=true")
      sql("Alter table reverttest drop columns(decimalField)")
      hiveClient.runSqlHive("set hive.security.authorization.enabled=false")
    }
    assert(sql("select decimalField from reverttest").count().equals(1L))
  }

  test("test to revert changed datatype on failure") {
    intercept[Exception] {
      hiveClient.runSqlHive("set hive.security.authorization.enabled=true")
      sql("Alter table reverttest change intField intfield bigint")
      hiveClient.runSqlHive("set hive.security.authorization.enabled=false")
    }
    assert(
      sql("select intfield from reverttest").schema.fields.apply(0).dataType.simpleString == "int")
  }

  override def afterAll() {
    hiveClient.runSqlHive("set hive.security.authorization.enabled=false")
    sql("drop table if exists reverttest")
  }

}