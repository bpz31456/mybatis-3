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
package org.apache.ibatis.reflection;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.CollectionWrapper;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

/**
 * 对象元数据
 * 静态工厂方法forObject创建MetaObject,做解耦
 * MetaObject ,setValue,getValue,add,addAll等元数据操作
 * @author Clinton Begin
 */
public class MetaObject {

  private final Object originalObject;
    /**
     * originalObject 的包装类型
     */
  private final ObjectWrapper objectWrapper;
    /**
     * 代码中没有直接调用factory来构造originalObject
     */
  private final ObjectFactory objectFactory;
    /**
     * 这里 DefaultObjectWrapperFactory不可用，所以方法做了异常抛出
     * 在构造函数中，通过一个钩子方法，扩展程序在mybatis.xml中配置的
     * ObjectWrapperFactory
     */
  private final ObjectWrapperFactory objectWrapperFactory;
  private final ReflectorFactory reflectorFactory;

  private MetaObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    this.originalObject = object;
    this.objectFactory = objectFactory;
    this.objectWrapperFactory = objectWrapperFactory;
    this.reflectorFactory = reflectorFactory;
    //本身自己是包装类型，直接返回自己
    if (object instanceof ObjectWrapper) {
      this.objectWrapper = (ObjectWrapper) object;
    } else if (objectWrapperFactory.hasWrapperFor(object)) {
      this.objectWrapper = objectWrapperFactory.getWrapperFor(this, object);
      //提供三种类型的包装类，Map,collection,bean
    } else if (object instanceof Map) {
      this.objectWrapper = new MapWrapper(this, (Map) object);
    } else if (object instanceof Collection) {
      this.objectWrapper = new CollectionWrapper(this, (Collection) object);
    } else {
      this.objectWrapper = new BeanWrapper(this, object);
    }
  }

    /**
     * 创建方法
     * @param object
     * @param objectFactory
     * @param objectWrapperFactory
     * @param reflectorFactory
     * @return
     */
  public static MetaObject forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
      //出入的需要包装的对象为空，直接返回null_meta_object
      if (object == null) {
      return SystemMetaObject.NULL_META_OBJECT;
      //不为空时，直接初始化private构造方法
    } else {
      return new MetaObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
    }
  }
  //几个基础的返回方法
  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  public ReflectorFactory getReflectorFactory() {
	return reflectorFactory;
  }

  public Object getOriginalObject() {
    return originalObject;
  }

    /**
     * 在MetaObject中查找属性（属性表达式）
     * @param propName
     * @param useCamelCaseMapping 是否使用驼峰表示，如果是，则，默认消除"_"
     * @return
     */
  public String findProperty(String propName, boolean useCamelCaseMapping) {
    return objectWrapper.findProperty(propName, useCamelCaseMapping);
  }

    /**
     * 得到setter方法
     * @return
     */
  public String[] getGetterNames() {
    return objectWrapper.getGetterNames();
  }

    /**
     * 得到getter方法
     * @return
     */
  public String[] getSetterNames() {
    return objectWrapper.getSetterNames();
  }

    /**
     * 得到setter的属性
     * @param name
     * @return
     */
  public Class<?> getSetterType(String name) {
    return objectWrapper.getSetterType(name);
  }

    /**
     * 得到getterType
     * @param name
     * @return
     */
  public Class<?> getGetterType(String name) {
    return objectWrapper.getGetterType(name);
  }

    /**
     * 是否有setter方法
     * @param name
     * @return
     */
  public boolean hasSetter(String name) {
    return objectWrapper.hasSetter(name);
  }

    /**
     * 是否有getter方法
     * @param name
     * @return
     */
  public boolean hasGetter(String name) {
    return objectWrapper.hasGetter(name);
  }

    /**
     * 根据属性表达式，得到值
     * @param name
     * @return
     */
  public Object getValue(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
        //得到对应分词器的name[index]
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return null;
      } else {
        return metaValue.getValue(prop.getChildren());
      }
    } else {
        //循环到属性表达式最后一个name,返回包装类中属性对应的值
      return objectWrapper.get(prop);
    }
  }

    /**
     * 更具属性表达式，设置对象的值
     * @param name
     * @param value
     */
  public void setValue(String name, Object value) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
        //得到对应分词器的name[index]
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          //当 name[index] = null时，直接返回，不能设置值
        if (value == null && prop.getChildren() != null) {
          // don't instantiate child path if value is null
          return;
        } else {
          metaValue = objectWrapper.instantiatePropertyValue(name, prop, objectFactory);
        }
      }
      metaValue.setValue(prop.getChildren(), value);
    } else {
        //没有child，直接设置当前name
      objectWrapper.set(prop, value);
    }
  }

    /**
     * name[i].child => 直接得到name[i]对象
     * 根据属性表达式，得到当前对象对应的MetaObject
     * @param name
     * @return
     */
  public MetaObject metaObjectForProperty(String name) {
      //得到name[i]对象
    Object value = getValue(name);
    return MetaObject.forObject(value, objectFactory, objectWrapperFactory, reflectorFactory);
  }


  public ObjectWrapper getObjectWrapper() {
    return objectWrapper;
  }

    /**
     * 是否为容器类型
     * @return
     */
  public boolean isCollection() {
    return objectWrapper.isCollection();
  }

    /**
     *  添加一个元入到容器中
     * @param element
     */
  public void add(Object element) {
    objectWrapper.add(element);
  }

    /**
     * 添加所有元素到容器中
     * @param list
     * @param <E>
     */
  public <E> void addAll(List<E> list) {
    objectWrapper.addAll(list);
  }

}
