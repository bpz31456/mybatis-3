/**
 *    Copyright 2009-2018 the original author or authors.
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
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 * 类的元信息，静态工厂模式，类与类元数据解耦
 */
public class MetaClass {

  private final ReflectorFactory reflectorFactory;
  private final Reflector reflector;

    /**
     * 私有化构造方法
     * @param type
     * @param reflectorFactory
     */
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

    /**
     * 静态初始化方法
     * @param type
     * @param reflectorFactory
     * @return
     */
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

    /**
     *根据属性名得到元数据
     * @param name
     * @return
     */
  public MetaClass metaClassForProperty(String name) {
      //根据属性名的getter方法，返回属性的type
    Class<?> propType = reflector.getGetterType(name);
    return MetaClass.forClass(propType, reflectorFactory);
  }

    /**
     * 根据“属性表达式”查找属性
     * @param name
     * @return
     */
  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

    /***
     * 返回getter方法名
     * @return
     */
  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

    /**
     * setter方法名
     * @return
     */
  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

    /**
     * 属性表达式user.name => name的类型
     * @param name
     * @return
     */
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

    /**
     * 属性表达式中user.name => name的类型
     * @param name
     * @return
     */
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

    /**
     * 更具拆词器来来获取metaClass
     * @param prop
     * @return
     */
  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

    /**
     * 根据拆词器获取属性的Getter方法返回的类型
     * @param prop
     * @return
     */
  private Class<?> getGetterType(PropertyTokenizer prop) {
      //name[index].child
      //分词器中第一个name对应属性
    Class<?> type = reflector.getGetterType(prop.getName());
    //存在name[index]形式，表示为集合类型的属性
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      Type returnType = getGenericGetterType(prop.getName());
      //如果返回类型为基础泛型如List<String> ,Set<User>,Map<String,User>等
      if (returnType instanceof ParameterizedType) {
          //实际类型列表Map<String,User> = > {java.lang.String,xxx.entity.User}
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        //基础泛型，非Map
          //TODO 如果为Map如何处理
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
              //List<String> => java.util.List
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

    /**
     * 返回通用的getter方法返回值类型
     * @param propertyName
     * @return
     */
  private Type getGenericGetterType(String propertyName) {
    try {
        //直接返回属性对应的Invoker
      Invoker invoker = reflector.getGetInvoker(propertyName);
      //通过getter的方法提直接添加到reflector中的方法
      if (invoker instanceof MethodInvoker) {
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
        //通过属性名生成的方法执行体GetFieldInvoker添加到reflector中
      } else if (invoker instanceof GetFieldInvoker) {
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
      return null;
  }

    /**
     * 根据传入的属性表达式，得到属性，并查看是否有对应的setter方法
     * 当所有的属性都具有setter方法才会返回true，但凡有一个属性不具有
     * setter就会返回false
     * 这里的属性如果没有显示指定setter，如果reflector有足够的访问权限，也会生成一个setter反射方法SetFieldInvoker实例
     * @param name
     * @return
     */
  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

    /**
     * 判断属性表达式中的属性是否有getter方法
     * 这里属性表达式中属性如果没有getter方法，也会更具reflector是否对属性有足够访问权限，直接生成GetFieldInvoker实例
     * @param name
     * @return
     */
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  private StringBuilder buildProperty(String name, StringBuilder builder) {
      //属性表达式拆词器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
        //得到拆词器的中的name
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        //处理的是类信息，所以没有管prop中的indexedName,index也没有管
        MetaClass metaProp = metaClassForProperty(propertyName);
        metaProp.buildProperty(prop.getChildren(), builder);
      }
      //没有“.”，单属性
    } else {
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
