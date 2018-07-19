/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.logging.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * ResultSet proxy to add logging
 * 
 * @author Clinton Begin
 * @author Eduardo Macarron
 * 结果集日志
 */
public final class ResultSetLogger extends BaseJdbcLogger implements InvocationHandler {
    /**
     * blob类型特殊处理
     */
  private static Set<Integer> BLOB_TYPES = new HashSet<Integer>();
  private boolean first = true;
  //这里会不会越界
  private int rows;
  private final ResultSet rs;
  private final Set<Integer> blobColumns = new HashSet<Integer>();

  static {
    BLOB_TYPES.add(Types.BINARY);
    BLOB_TYPES.add(Types.BLOB);
    BLOB_TYPES.add(Types.CLOB);
    BLOB_TYPES.add(Types.LONGNVARCHAR);
    BLOB_TYPES.add(Types.LONGVARBINARY);
    BLOB_TYPES.add(Types.LONGVARCHAR);
    BLOB_TYPES.add(Types.NCLOB);
    BLOB_TYPES.add(Types.VARBINARY);
  }
  
  private ResultSetLogger(ResultSet rs, Log statementLog, int queryStack) {
    super(statementLog, queryStack);
    this.rs = rs;
  }

  /**
   * rs结果日志打印
   * @param proxy
   * @param method
   * @param params
   * @return
   * @throws Throwable
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
    try {
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, params);
      }
      //执行rs的方法
      Object o = method.invoke(rs, params);
      //执行完next方法后，然后
      if ("next".equals(method.getName())) {
        if (((Boolean) o)) {
          rows++;
          //堆栈跟踪才会答应结果集
          if (isTraceEnabled()) {
            ResultSetMetaData rsmd = rs.getMetaData();
            final int columnCount = rsmd.getColumnCount();
            //第一行就答应头
            if (first) {
              first = false;
              printColumnHeaders(rsmd, columnCount);
            }
            //其他就打印值
            printColumnValues(columnCount);
          }
        } else {
          debug("     Total: " + rows, false);
        }
      }
      clearColumnInfo();
      return o;
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

    /**
     * 表头处理
     * @param rsmd
     * @param columnCount
     * @throws SQLException
     */
  private void printColumnHeaders(ResultSetMetaData rsmd, int columnCount) throws SQLException {
    StringBuilder row = new StringBuilder();
    row.append("   Columns: ");
    //遍历表头，blob单独记录
    for (int i = 1; i <= columnCount; i++) {
      if (BLOB_TYPES.contains(rsmd.getColumnType(i))) {
        blobColumns.add(i);
      }
      String colname = rsmd.getColumnLabel(i);
      row.append(colname);
      if (i != columnCount) {
        row.append(", ");
      }
    }
    trace(row.toString(), false);
  }

    /**
     * 打印值
     * @param columnCount
     */
  private void printColumnValues(int columnCount) {
    StringBuilder row = new StringBuilder();
    row.append("       Row: ");
    for (int i = 1; i <= columnCount; i++) {
      String colname;
      try {
          //通过下标来辨别blob
        if (blobColumns.contains(i)) {
          colname = "<<BLOB>>";
        } else {
          colname = rs.getString(i);
        }
      } catch (SQLException e) {
        // generally can't call getString() on a BLOB column
        colname = "<<Cannot Display>>";
      }
      row.append(colname);
      if (i != columnCount) {
        row.append(", ");
      }
    }
    trace(row.toString(), false);
  }

  /**
   * Creates a logging version of a ResultSet
   *
   * @param rs - the ResultSet to proxy
   * @return - the ResultSet with logging
   */
  public static ResultSet newInstance(ResultSet rs, Log statementLog, int queryStack) {
    InvocationHandler handler = new ResultSetLogger(rs, statementLog, queryStack);
    ClassLoader cl = ResultSet.class.getClassLoader();
    return (ResultSet) Proxy.newProxyInstance(cl, new Class[]{ResultSet.class}, handler);
  }

  /**
   * Get the wrapped result set
   *
   * @return the resultSet
   */
  public ResultSet getRs() {
    return rs;
  }

}
