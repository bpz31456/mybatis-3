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
package org.apache.ibatis.scripting.xmltags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * xml节点片段(select|delete|update|insert)构建器
 * @author Clinton Begin
 */
public class XMLScriptBuilder extends BaseBuilder {

    /**
     * 当前节点()
     */
  private final XNode context;
  private boolean isDynamic;
    /**
     * 传入的参数类型
     */
  private final Class<?> parameterType;
  private final Map<String, NodeHandler> nodeHandlerMap = new HashMap<String, NodeHandler>();

  public XMLScriptBuilder(Configuration configuration, XNode context) {
    this(configuration, context, null);
  }

    /**
     * 创建一个构建器
     * @param configuration
     * @param context
     * @param parameterType
     */
  public XMLScriptBuilder(Configuration configuration, XNode context, Class<?> parameterType) {
    super(configuration);
    this.context = context;
    this.parameterType = parameterType;
    initNodeHandlerMap();
  }


    /**
     * 初始化节点Handlers Map
     * 动态SQL语句节点
     * if
     * choose (when, otherwise)
     * trim (where, set)
     * foreach
     * bind
     */
  private void initNodeHandlerMap() {
      /**
       * 修剪
       */
    nodeHandlerMap.put("trim", new TrimHandler());
    nodeHandlerMap.put("where", new WhereHandler());
    nodeHandlerMap.put("set", new SetHandler());
    nodeHandlerMap.put("foreach", new ForEachHandler());
    nodeHandlerMap.put("if", new IfHandler());
    nodeHandlerMap.put("choose", new ChooseHandler());
    nodeHandlerMap.put("when", new IfHandler());
    nodeHandlerMap.put("otherwise", new OtherwiseHandler());
    nodeHandlerMap.put("bind", new BindHandler());
  }

  /**
   * 具体解析脚本,解析具体的节点
   * @return
   */
  public SqlSource parseScriptNode() {
    /**
     * 具体解析动态的标签
     */
    MixedSqlNode rootSqlNode = parseDynamicTags(context);
    SqlSource sqlSource = null;
    if (isDynamic) {
        //动态sqlSource
      sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
    } else {
        //原始SqlSource
      sqlSource = new RawSqlSource(configuration, rootSqlNode, parameterType);
    }
    return sqlSource;
  }

    /**
     * 解析动态tags，
     * @param node (select|delete|update|insert)节点
     * @return
     */
  protected MixedSqlNode parseDynamicTags(XNode node) {
    List<SqlNode> contents = new ArrayList<SqlNode>();
      /**
       * 所有childNode
       */
    NodeList children = node.getNode().getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
        /**
         * 每个子节点
         */
      XNode child = node.newXNode(children.item(i));
      //静态文本模块
      if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE || child.getNode().getNodeType() == Node.TEXT_NODE) {
          //TODO 如何得到StringBody
        String data = child.getStringBody("");
        //构造为TextSqlNode
        TextSqlNode textSqlNode = new TextSqlNode(data);
          //StringBody是否存在符号标记
          if (textSqlNode.isDynamic()) {
              //动态Node需要替换
          contents.add(textSqlNode);
          isDynamic = true;
        } else {
              //静态代码块
          contents.add(new StaticTextSqlNode(data));
        }
        //元素节点
      } else if (child.getNode().getNodeType() == Node.ELEMENT_NODE) { // issue #628
          //if
          //choose (when, otherwise)
          //trim (where, set)
          //foreach
          //bind
        String nodeName = child.getNode().getNodeName();
        //根据注册的nodeHandler,通过nodeName得到 <String,Handler>
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        //没有对应的处理模块，在xml中瞎写的element
        if (handler == null) {
              throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
          }
          //根据不同的节点类型，自己处理自己的内容
        handler.handleNode(child, contents);
        isDynamic = true;
      }
    }
    //混合SqlNode
    return new MixedSqlNode(contents);
  }

  private interface NodeHandler {
    void handleNode(XNode nodeToHandle, List<SqlNode> targetContents);
  }

  private class BindHandler implements NodeHandler {
    public BindHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      final String name = nodeToHandle.getStringAttribute("name");
      final String expression = nodeToHandle.getStringAttribute("value");
      final VarDeclSqlNode node = new VarDeclSqlNode(name, expression);
      targetContents.add(node);
    }
  }

  private class TrimHandler implements NodeHandler {
    public TrimHandler() {
        //防止合成访问
      // Prevent Synthetic Access
    }

      /**
       * 修剪节点实现
       * @param nodeToHandle
       * @param targetContents
       */
    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      String prefix = nodeToHandle.getStringAttribute("prefix");
      String prefixOverrides = nodeToHandle.getStringAttribute("prefixOverrides");
      String suffix = nodeToHandle.getStringAttribute("suffix");
      String suffixOverrides = nodeToHandle.getStringAttribute("suffixOverrides");
      TrimSqlNode trim = new TrimSqlNode(configuration, mixedSqlNode, prefix, prefixOverrides, suffix, suffixOverrides);
      targetContents.add(trim);
    }
  }

  private class WhereHandler implements NodeHandler {
    public WhereHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      WhereSqlNode where = new WhereSqlNode(configuration, mixedSqlNode);
      targetContents.add(where);
    }
  }

  private class SetHandler implements NodeHandler {
    public SetHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      SetSqlNode set = new SetSqlNode(configuration, mixedSqlNode);
      targetContents.add(set);
    }
  }

  private class ForEachHandler implements NodeHandler {
    public ForEachHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      String collection = nodeToHandle.getStringAttribute("collection");
      String item = nodeToHandle.getStringAttribute("item");
      String index = nodeToHandle.getStringAttribute("index");
      String open = nodeToHandle.getStringAttribute("open");
      String close = nodeToHandle.getStringAttribute("close");
      String separator = nodeToHandle.getStringAttribute("separator");

      ForEachSqlNode forEachSqlNode = new ForEachSqlNode(configuration, mixedSqlNode, collection, index, item, open, close, separator);
      targetContents.add(forEachSqlNode);
    }
  }

  private class IfHandler implements NodeHandler {
    public IfHandler() {
      // Prevent Synthetic Access
    }

      /**
       * if节点
       * @param nodeToHandle
       * @param targetContents
       */
    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
        //递归处理，处理if标签下标签
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);

      String test = nodeToHandle.getStringAttribute("test");
      //比较test
      IfSqlNode ifSqlNode = new IfSqlNode(mixedSqlNode, test);
      //只是用来保存ifSqlNode
      targetContents.add(ifSqlNode);
    }
  }

  private class OtherwiseHandler implements NodeHandler {
    public OtherwiseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      targetContents.add(mixedSqlNode);
    }
  }

  private class ChooseHandler implements NodeHandler {
    public ChooseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> whenSqlNodes = new ArrayList<SqlNode>();
      List<SqlNode> otherwiseSqlNodes = new ArrayList<SqlNode>();
      handleWhenOtherwiseNodes(nodeToHandle, whenSqlNodes, otherwiseSqlNodes);
      SqlNode defaultSqlNode = getDefaultSqlNode(otherwiseSqlNodes);
      ChooseSqlNode chooseSqlNode = new ChooseSqlNode(whenSqlNodes, defaultSqlNode);
      targetContents.add(chooseSqlNode);
    }

    private void handleWhenOtherwiseNodes(XNode chooseSqlNode, List<SqlNode> ifSqlNodes, List<SqlNode> defaultSqlNodes) {
      List<XNode> children = chooseSqlNode.getChildren();
      for (XNode child : children) {
        String nodeName = child.getNode().getNodeName();
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        if (handler instanceof IfHandler) {
          handler.handleNode(child, ifSqlNodes);
        } else if (handler instanceof OtherwiseHandler) {
          handler.handleNode(child, defaultSqlNodes);
        }
      }
    }

    private SqlNode getDefaultSqlNode(List<SqlNode> defaultSqlNodes) {
      SqlNode defaultSqlNode = null;
      if (defaultSqlNodes.size() == 1) {
        defaultSqlNode = defaultSqlNodes.get(0);
      } else if (defaultSqlNodes.size() > 1) {
        throw new BuilderException("Too many default (otherwise) elements in choose statement.");
      }
      return defaultSqlNode;
    }
  }

}
