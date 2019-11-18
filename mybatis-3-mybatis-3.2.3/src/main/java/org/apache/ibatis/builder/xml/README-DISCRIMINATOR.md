# 鉴别器


## 定义：

鉴别器类似于java中的switch语句。 定义鉴别器也是通过column和javaType属性来唯一标识，column是用来确定某个字段是否为鉴别器，JavaType是需要被用来保证等价测试的合适类型

## 例子：

```xml
<discriminator javaType="int" column="vehicle_type">
   <case value="1" resultMap="carResult"/>
   <case value="2" resultMap="truckResult"/>
   <case value="3" resultMap="vanResult"/>
   <case value="4" resultMap="suvResult"/>
</discriminator>
```

对上上述的鉴别器，如果 vehicle_type 的值为 "1"，则使用 carResult 的结果映射。

```xml
<resultMap id="carResult" type="Car">
    <result property="doorCount" column="door_count" />
</resultMap>
```

主要处理见：processDiscriminatorElement()
