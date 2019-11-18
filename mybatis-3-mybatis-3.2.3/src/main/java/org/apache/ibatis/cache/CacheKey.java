package org.apache.ibatis.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author
 */
public class CacheKey implements Cloneable, Serializable {

    private static final long serialVersionUID = 1146682552656046210L;

    public static final CacheKey NULL_CACHE_KEY = new NullCacheKey();

    private static final int DEFAULT_MULTIPLYER = 37;
    private static final int DEFAULT_HASHCODE = 17;

    private int multiplier;
    private int hashcode;
    private long checksum;
    private int count;
    private List<Object> updateList;

    public CacheKey() {
        this.hashcode = DEFAULT_HASHCODE;
        this.multiplier = DEFAULT_MULTIPLYER;
        this.count = 0;
        this.updateList = new ArrayList<Object>();
    }

    public CacheKey(Object[] objects) {
        this();
        updateAll(objects);
    }

    public int getUpdateCount() {
        return updateList.size();
    }

    public void update(Object object) {

        // 直接获取对象的 hashCode，在3.5.3 中定义了 ArrayUtil 对象，用于计算对象的hashCode,toString,equals等。
        int baseHashCode = object == null ? 1 : object.hashCode();

        count++;
        checksum += baseHashCode;
        baseHashCode *= count;

        hashcode = multiplier * hashcode + baseHashCode;

        updateList.add(object);
    }

    public void updateAll(Object[] objects) {
        for (Object o : objects) {
            update(o);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CacheKey)) {
            return false;
        }

        final CacheKey cacheKey = (CacheKey) object;

        if (hashcode != cacheKey.hashcode) {
            return false;
        }
        if (checksum != cacheKey.checksum) {
            return false;
        }
        if (count != cacheKey.count) {
            return false;
        }

        for (int i = 0; i < updateList.size(); i++) {
            Object thisObject = updateList.get(i);
            Object thatObject = cacheKey.updateList.get(i);
            if (thisObject == null) {
                if (thatObject != null) {
                    return false;
                }
            } else {
                if (!thisObject.equals(thatObject)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    @Override
    public String toString() {
        StringBuilder returnValue = new StringBuilder().append(hashcode).append(':').append(checksum);
        for (int i = 0; i < updateList.size(); i++) {
            returnValue.append(':').append(updateList.get(i));
        }

        return returnValue.toString();
    }

    @Override
    public CacheKey clone() throws CloneNotSupportedException {
        CacheKey clonedCacheKey = (CacheKey) super.clone();
        clonedCacheKey.updateList = new ArrayList<Object>(updateList);
        return clonedCacheKey;
    }

}
