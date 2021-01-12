package net.cabworks.jdbc

import java.security.PrivilegedAction
import java.sql.{Connection, Driver, DriverPropertyInfo}
import java.util.Properties
import java.util.logging.Logger

import com.microsoft.sqlserver.jdbc.SQLServerDriver
import org.apache.hadoop.conf.Configuration
//import org.apache.hadoop.hdfs.server.common.JspHelper.Url
import org.apache.hadoop.security.UserGroupInformation
import org.apache.hadoop.security.UserGroupInformation.AuthenticationMethod

class Krb5SqlServer extends Driver {

  private val sqlServerDriver = new SQLServerDriver()

  override def acceptsURL(url: String): Boolean = sqlServerDriver.acceptsURL(url)

  override def jdbcCompliant(): Boolean = sqlServerDriver.jdbcCompliant()

  override def getPropertyInfo(url: String, info: Properties): Array[DriverPropertyInfo] = sqlServerDriver.getPropertyInfo(url, info)

  override def getMinorVersion: Int = sqlServerDriver.getMinorVersion

  override def getParentLogger: Logger = sqlServerDriver.getParentLogger

  override def connect(url: String, info: Properties): Connection = {

    println("*********** Calling the DRIVER (connect method call ) ********************")
    println(s"input param = ${url}")

    val connectionProps = Krb5SqlServer.connectionProperties(url)
    val keytabFile = connectionProps(Krb5SqlServer.keytabFile)
    val principal = connectionProps(Krb5SqlServer.principalKey)

    println("extracted variables : ")
    println(s"keytabFile param = ${keytabFile}")
    println(s"principal param = ${principal}")

    val config = new Configuration()
    config.addResource("/etc/hadoop/conf/hdfs-site.xml")
    config.addResource("/etc/hadoop/conf/core-site.xml")
    config.addResource("/etc/hadoop/conf/mapred-site.xml")

    println("config is set ")

    UserGroupInformation.setConfiguration(config)

    UserGroupInformation
      .getCurrentUser
      .setAuthenticationMethod(AuthenticationMethod.KERBEROS)

    UserGroupInformation.loginUserFromKeytabAndReturnUGI(principal, keytabFile)
      .doAs(new PrivilegedAction[Connection] {
        override def run(): Connection = {
          val newURL= Krb5SqlServer.toSqlServerUrl(url)
          println(s"url = ${url}")
          println(s"newURL = ${newURL}")
          println(s"info = ${info}")
          sqlServerDriver.connect(newURL, info)
         // sqlServerDriver.connect(url, info)

        }
    })
  }

  override def getMajorVersion: Int = sqlServerDriver.getMajorVersion
}

object Krb5SqlServer {
  def toSqlServerUrl(url: String): String = {
   val str = s"${head(url).replace(krbPrefix, sqlServerPrefix)};${connectionProperties(url).filter({ case (k, v) => k != principalKey && k != keytabFile }).map({ case (k, v) => s"$k=$v" }).mkString(";")};"
    println(s"URL String = ${str}")
    str
  }

  val sqlServerPrefix = "sqlserver"
  val krbPrefix = "krb5ss"
  val principalKey = "krb5Principal"
  val keytabFile = "krb5Keytab"

  def connectionProperties(url: String): Map[String, String] = url.split(';')
                                                                .toList.tail.map(p => p.split('=')).map(s => (s(0), s(1)))
                                                                .foldLeft(Map.empty[String, String]){ case (m, (k, v)) => m + (k -> v) }

  def head(url: String): String = url.split(';').head

}
