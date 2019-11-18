package org.apache.ibatis.builder.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.ibatis.io.Resources;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author
 */
public class XMLMapperEntityResolver implements EntityResolver {

    private static final Map<String, String> doctypeMap = new HashMap<String, String>();

    private static final String IBATIS_CONFIG_DOCTYPE = "-//ibatis.apache.org//DTD Config 3.0//EN".toUpperCase(Locale.ENGLISH);
    private static final String IBATIS_CONFIG_URL = "http://ibatis.apache.org/dtd/ibatis-3-config.dtd".toUpperCase(Locale.ENGLISH);

    private static final String IBATIS_MAPPER_DOCTYPE = "-//ibatis.apache.org//DTD Mapper 3.0//EN".toUpperCase(Locale.ENGLISH);
    private static final String IBATIS_MAPPER_URL = "http://ibatis.apache.org/dtd/ibatis-3-mapper.dtd".toUpperCase(Locale.ENGLISH);

    private static final String MYBATIS_CONFIG_DOCTYPE = "-//mybatis.org//DTD Config 3.0//EN".toUpperCase(Locale.ENGLISH);
    private static final String MYBATIS_CONFIG_URL = "http://mybatis.org/dtd/mybatis-3-config.dtd".toUpperCase(Locale.ENGLISH);

    private static final String MYBATIS_MAPPER_DOCTYPE = "-//mybatis.org//DTD Mapper 3.0//EN".toUpperCase(Locale.ENGLISH);
    private static final String MYBATIS_MAPPER_URL = "http://mybatis.org/dtd/mybatis-3-mapper.dtd".toUpperCase(Locale.ENGLISH);

    private static final String IBATIS_CONFIG_DTD = "org/apache/ibatis/builder/xml/mybatis-3-config.dtd";
    private static final String IBATIS_MAPPER_DTD = "org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd";

    static {
        doctypeMap.put(IBATIS_CONFIG_URL, IBATIS_CONFIG_DTD);
        doctypeMap.put(IBATIS_CONFIG_DOCTYPE, IBATIS_CONFIG_DTD);

        doctypeMap.put(IBATIS_MAPPER_URL, IBATIS_MAPPER_DTD);
        doctypeMap.put(IBATIS_MAPPER_DOCTYPE, IBATIS_MAPPER_DTD);

        doctypeMap.put(MYBATIS_CONFIG_URL, IBATIS_CONFIG_DTD);
        doctypeMap.put(MYBATIS_CONFIG_DOCTYPE, IBATIS_CONFIG_DTD);

        doctypeMap.put(MYBATIS_MAPPER_URL, IBATIS_MAPPER_DTD);
        doctypeMap.put(MYBATIS_MAPPER_DOCTYPE, IBATIS_MAPPER_DTD);
    }

    /**
     * 将公共的DTD转换为本地模式，mybatis在解析的时候，引用了本地的DTD文件，
     * 和本类在同一个package下，其中的 ibatis-3-config.dtd 和 ibatis-3-mapper.dtd 应该主要是为了兼容低版本。
     *
     * @param publicId 公共标识符 从 mybatis XML文件中的 DOCTYPE 中获取。
     * @param systemId
     * @return
     * @throws SAXException
     */
    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException {

        if (publicId != null) {
            publicId = publicId.toUpperCase(Locale.ENGLISH);
        }
        if (systemId != null) {
            systemId = systemId.toUpperCase(Locale.ENGLISH);
        }

        InputSource source = null;
        try {
            String path = doctypeMap.get(publicId);
            source = getInputSource(path, source);
            if (source == null) {
                path = doctypeMap.get(systemId);
                source = getInputSource(path, source);
            }
        } catch (Exception e) {
            throw new SAXException(e.toString());
        }
        return source;
    }

    private InputSource getInputSource(String path, InputSource source) {
        if (path != null) {
            InputStream in;
            try {
                in = Resources.getResourceAsStream(path);
                source = new InputSource(in);
            } catch (IOException e) {
                // ignore, null is ok
            }
        }
        return source;
    }

}