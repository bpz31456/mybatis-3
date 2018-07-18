/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.session.Configuration;

/**
 * 类型处理基类，模版模式（所有abstract方法都是钩子方法），方便子类调用
 *
 * @author Clinton Begin
 * @author Simone Tripodi
 */
public abstract class BaseTypeHandler<T> extends TypeReference<T> implements TypeHandler<T> {
    /**
     * 配置文件 mybatis.xml副本
     */
  protected Configuration configuration;

  public void setConfiguration(Configuration c) {
    this.configuration = c;
  }

    /**
     * 对sql声明（PreparedStatement ps）设置参数
     * @param ps
     * @param i PreparedStatement ps中“?”下标
     * @param parameter
     * @param jdbcType
     * @throws SQLException
     */
  @Override
  public void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
    //参数为空
      if (parameter == null) {
      if (jdbcType == null) {
        throw new TypeException("JDBC requires that the JdbcType must be specified for all nullable parameters.");
      }
      try {
          //直接调用了ps.setNull
        ps.setNull(i, jdbcType.TYPE_CODE);
      } catch (SQLException e) {
        throw new TypeException("Error setting null for parameter #" + i + " with JdbcType " + jdbcType + " . " +
                "Try setting a different JdbcType for this parameter or a different jdbcTypeForNull configuration property. " +
                "Cause: " + e, e);
      }
      //参数不为空
    } else {
      try {
        setNonNullParameter(ps, i, parameter, jdbcType);
      } catch (Exception e) {
        throw new TypeException("Error setting non null for parameter #" + i + " with JdbcType " + jdbcType + " . " +
                "Try setting a different JdbcType for this parameter or a different configuration property. " +
                "Cause: " + e, e);
      }
    }
  }

    /**
     * 获取的结果集的某一个单元格的数据，并非为一个collection
     * @param rs
     * @param columnName
     * @return
     * @throws SQLException
     */
  @Override
  public T getResult(ResultSet rs, String columnName) throws SQLException {
    T result;
    try {
      result = getNullableResult(rs, columnName);
    } catch (Exception e) {
      throw new ResultMapException("Error attempting to get column '" + columnName + "' from result set.  Cause: " + e, e);
    }
    if (rs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }

    /**
     * 获取的结果集的某一个单元格的数据，并非为一个collection
     * @param rs
     * @param columnIndex
     * @return
     * @throws SQLException
     */
  @Override
  public T getResult(ResultSet rs, int columnIndex) throws SQLException {
    T result;
    try {
      result = getNullableResult(rs, columnIndex);
    } catch (Exception e) {
      throw new ResultMapException("Error attempting to get column #" + columnIndex+ " from result set.  Cause: " + e, e);
    }
    if (rs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }

    /**
     * 调用过程时，返回的结果(CallableStatement extent PreparedStatement)
     * @param cs
     * @param columnIndex
     * @return
     * @throws SQLException
     */
  @Override
  public T getResult(CallableStatement cs, int columnIndex) throws SQLException {
    T result;
    try {
      result = getNullableResult(cs, columnIndex);
    } catch (Exception e) {
      throw new ResultMapException("Error attempting to get column #" + columnIndex+ " from callable statement.  Cause: " + e, e);
    }
    if (cs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }

  public abstract void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  public abstract T getNullableResult(ResultSet rs, String columnName) throws SQLException;

  public abstract T getNullableResult(ResultSet rs, int columnIndex) throws SQLException;

  public abstract T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException;

}
