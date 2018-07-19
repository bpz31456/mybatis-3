/**
 * Copyright 2009-2017 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.logging.jdbc;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * PreparedStatement proxy to add logging
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * 预处理日志
 */
public final class PreparedStatementLogger extends BaseJdbcLogger implements InvocationHandler {

    private final PreparedStatement statement;

    private PreparedStatementLogger(PreparedStatement stmt, Log statementLog, int queryStack) {
        super(statementLog, queryStack);
        this.statement = stmt;
    }

    /**
     * 具体执行的方法
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
            //EXE执行方法集合中的方法
            if (EXECUTE_METHODS.contains(method.getName())) {
                if (isDebugEnabled()) {
                    debug("Parameters: " + getParameterValueString(), true);
                }
                clearColumnInfo();
                //查询语句
                if ("executeQuery".equals(method.getName())) {
                    //结果集实体
                    ResultSet rs = (ResultSet) method.invoke(statement, params);
                    //通过结果集实体->ResultSetLogger代理
                    return rs == null ? null : ResultSetLogger.newInstance(rs, statementLog, queryStack);
                } else {
                    // 其他执行类，就直接打印没有结果集合
                    return method.invoke(statement, params);
                }
                //设置方法里面的
            } else if (SET_METHODS.contains(method.getName())) {
                if ("setNull".equals(method.getName())) {
                    setColumn(params[0], null);
                } else {
                    setColumn(params[0], params[1]);
                }
                return method.invoke(statement, params);
                //结果集
            } else if ("getResultSet".equals(method.getName())) {
                ResultSet rs = (ResultSet) method.invoke(statement, params);
                return rs == null ? null : ResultSetLogger.newInstance(rs, statementLog, queryStack);
                //更新
            } else if ("getUpdateCount".equals(method.getName())) {
                int updateCount = (Integer) method.invoke(statement, params);
                if (updateCount != -1) {
                    debug("   Updates: " + updateCount, false);
                }
                return updateCount;
                //其他
            } else {
                return method.invoke(statement, params);
            }
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
    }

    /**
     *
     * @param stmt
     * @param statementLog
     * @param queryStack
     * @return
     */
    public static PreparedStatement newInstance(PreparedStatement stmt, Log statementLog, int queryStack) {
        InvocationHandler handler = new PreparedStatementLogger(stmt, statementLog, queryStack);
        ClassLoader cl = PreparedStatement.class.getClassLoader();
        return (PreparedStatement) Proxy.newProxyInstance(cl, new Class[]{PreparedStatement.class, CallableStatement.class}, handler);
    }

    /**
     *
     * @return
     */
    public PreparedStatement getPreparedStatement() {
        return statement;
    }

}
