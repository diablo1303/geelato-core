package org.geelato.core.gql.parser;

import org.apache.commons.collections.map.HashedMap;

import java.util.Map;

/**
 * @author geemeta
 */
public class QueryCommand extends BaseCommand<QueryCommand> {
    /**
     * TODO 客户端生成的唯一标识，用于缓存
     */
    private String key;
    private boolean queryForList = false;
    /**
     * @param pageNum，第几页，从1开始。
     */
    private int pageNum = -1;
    /**
     * @param pageSize 每页最大展示记录数，pageSize(客户端请求参数中)=limit(in mysql)
     */
    private int pageSize = -1;

    /**
     * 查询字段重命名
     */
    private Map alias = new HashedMap(10);

    /**
     * 查询语句
     */
    private String selectSql;

    private String groupBy;
    private String orderBy;
    private FilterGroup having;

    public QueryCommand() {
        setCommandType(CommandType.Query);
    }

    public boolean isPagingQuery() {
        return pageNum > 0 && pageSize > 0;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 是查询单条记录还是多条记录，默认值为false，即查询单条记录
     */
    public boolean isQueryForList() {
        return queryForList;
    }

    public void setQueryForList(boolean queryForList) {
        this.queryForList = queryForList;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }


    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        //orderBy中的列，应该出现在group by子句中
        this.orderBy = orderBy;
    }

    public String getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }

    public FilterGroup getHaving() {
        return having;
    }

    public void setHaving(FilterGroup having) {
        this.having = having;
    }

    public Map getAlias() {
        return alias;
    }

    public void setAlias(Map alias) {
        this.alias = alias;
    }

    public String getSelectSql() {
        return selectSql;
    }

    public void setSelectSql(String selectSql) {
        this.selectSql = selectSql;
    }
}
