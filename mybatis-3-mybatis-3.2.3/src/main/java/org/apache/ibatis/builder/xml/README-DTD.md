# DTD文件

## 简介

DTD（文档类型定义）的作用是定义XML文档的合法构建模板。

DTD 可被成行地声明与XML文档中，也可作为一个外部引用。

## 内部的DOCTYPE声明

加入DTD被包含在XML源码文件中，它应该通过下面的语法包装在一个DOCTYPE声明中：

```dtd
<!DOCTYPE root-element [element-declarations]>
```

带有DTD的xml文档实例：

```dtd
<?xml version="1.0"?>
<!DOCTYPE note [
<!ELEMENT note (to,from,heading,body)>
<!ELEMENT to (#PCDATA)>
<!ELEMENT from (#PCDATA)>
<!ELEMENT heading (#PCDATA)>
<!ELEMENT body (#PCDATA)>
]>
<note>
<to>Tove</to>
<from>Jani</from>
<heading>Reminder</heading>
<body>Don't forget me this weekend</body>
</note>
```
以上DTD解释如下：

* !DOCTYPE note (第二行)，定义此文档是note类型的文档。
* !ELEMENT note (第三行)，定义note元素有四个元素："to、from、heading、body"
* !ELEMENT note (第四行)，定义to元素为 "#PCDATA"类型。
* !ELEMENT note (第五行)，定义from元素为"#PCDATA"类型。
* !ELEMENT note (第六行)，定义heading元素为"#PCDATA"类型。
* !ELEMENT note (第七行)，定义body元素为"#PCDATA"类型。

## 外部文档声明

接入DTD位于XML源文件的外部，那么它应该通过下面的语法被封装在一个DOCTYPE定义中。

```dtd
<!DOCTYPE root-element SYSTEM "filename">
```

例如：

```xml
<?xml version="1.0"?>
<!DOCTYPE note SYSTEM "note.dtd">
<note>
  <to>Tove</to>
  <from>Jani</from>
  <heading>Reminder</heading>
  <body>Don't forget me this weekend!</body>
</note>
```

```dtd
<!ELEMENT note (to,from,heading,body)>
<!ELEMENT to (#PCDATA)>
<!ELEMENT from (#PCDATA)>
<!ELEMENT heading (#PCDATA)>
<!ELEMENT body (#PCDATA)>
```

## 为什么使用DTD？

* 通过DTD，可以使每个XML文件均可携带一个有关其自身格式的描述。
* 通过DTD，独立的团体可一致地使用某个标准的DTD来交换数据。
* 可以通过DTD来验证自身的数据。

## DTD构建模块

所有的XML文档均由以下简单的构建模块构成：

* 元素
* 属性
* 实体
* PCDATA
* CDATA

### 元素

元素是XML以及HTML文档的主要构建模块。
