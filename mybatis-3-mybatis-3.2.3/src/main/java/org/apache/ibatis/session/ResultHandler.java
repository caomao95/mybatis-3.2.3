package org.apache.ibatis.session;

/**
 * 结果处理程序
 *
 * @author
 */
public interface ResultHandler {

    void handleResult(ResultContext context);

}
