package org.geelato.core.orm;

import com.alibaba.fastjson2.JSONObject;
import org.apache.logging.log4j.util.Strings;
import org.geelato.core.enums.EnableStatusEnum;
import org.geelato.core.enums.TableTypeEnum;
import org.geelato.core.meta.MetaManager;
import org.geelato.core.meta.model.connect.ConnectMeta;
import org.geelato.core.meta.model.entity.EntityMeta;
import org.geelato.core.meta.model.entity.TableForeign;
import org.geelato.core.meta.model.entity.TableMeta;
import org.geelato.core.meta.model.field.ColumnMeta;
import org.geelato.core.meta.model.field.FieldMeta;
import org.geelato.core.meta.schema.SchemaIndex;
import org.geelato.core.meta.schema.SchemaTable;
import org.geelato.core.util.ConnectUtils;
import org.geelato.utils.SqlParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * 注意：使用前需先注入dao，见{@link #setDao(Dao)}。
 *
 * @author geemeta
 */
@Component
public class DbGenerateDao {

    private static Logger logger = LoggerFactory.getLogger(DbGenerateDao.class);
    private static HashMap<String, Integer> defaultColumnLengthMap;
    private static HashMap<String, Boolean> defaultColumnNullMap;
    private static HashMap<String, String> defaultColumnDataTypeMap;
    private static HashMap<String, String> defaultColumnTitleMap;
    private Dao dao;

    private MetaManager metaManager = MetaManager.singleInstance();

    public DbGenerateDao() {
        InitDefaultColumn();
    }

    private void InitDefaultColumn() {
        //todo chengx: init default column datatype,null,title

        //init default column length
        defaultColumnLengthMap = new HashMap<>();
        defaultColumnLengthMap.put("id", 32);
        defaultColumnLengthMap.put("dept_id", 32);
        defaultColumnLengthMap.put("bu_id", 32);
        defaultColumnLengthMap.put("tenant_code", 64);
        defaultColumnLengthMap.put("del_status", 10);
        defaultColumnLengthMap.put("update_at", 0);
        defaultColumnLengthMap.put("updater", 32);
        defaultColumnLengthMap.put("create_at", 0);
        defaultColumnLengthMap.put("creator", 32);
    }

    /**
     * @param dao
     */
    public void setDao(Dao dao) {
        this.dao = dao;
    }

    public Dao getDao() {
        return dao;
    }

    public void createAllTables(boolean dropBeforeCreate) {
        this.createAllTables(dropBeforeCreate, null);
    }

    /**
     * 基于元数据管理，需元数据据管理器已加载、扫描元数据
     * <p>内部调用了sqlId:dropOneTable来删除表，
     * 内部调用了sqlId:createOneTable来创建表</p>
     * 创建完表之后，将元数据信息保存到数据库中
     *
     * @param dropBeforeCreate     存在表时，是否删除
     * @param ignoreEntityNameList
     */
    public void createAllTables(boolean dropBeforeCreate, List<String> ignoreEntityNameList) {
        Collection<EntityMeta> entityMetas = metaManager.getAll();
        if (entityMetas == null) {
            logger.warn("实体元数据为空，可能还未解析元数据，请解析之后，再执行该方法(createAllTables)");
            return;
        }
        for (EntityMeta em : entityMetas) {
            boolean isIgnore = false;
            if (ignoreEntityNameList != null) {
                for (String ignoreEntityName : ignoreEntityNameList) {
                    if (em.getEntityName().indexOf(ignoreEntityName) != -1) {
                        isIgnore = true;
                        break;
                    }
                }
            }
            if (!isIgnore) {
                createOrUpdateOneTable(em, dropBeforeCreate);
            } else {
                logger.info("ignore createTable for entity: {}.", em.getEntityName());
            }
        }
        // 先清空元数据
//        this.dao.getJdbcTemplate().execute("TRUNCATE TABLE platform_dev_column");
//        this.dao.getJdbcTemplate().execute("TRUNCATE TABLE platform_dev_table");
//        this.dao.getJdbcTemplate().execute("TRUNCATE TABLE platform_dev_db_connect");

        // 创建数据库连接
        // TODO 改成数据文件中获取
        ConnectMeta connectMeta = new ConnectMeta();
        connectMeta.setDbName("geelato");
        connectMeta.setDbConnectName("geelato-local");
        connectMeta.setDbHostnameIp("127.0.0.1");
        connectMeta.setDbUserName("sa");
        connectMeta.setDbPort(3306);
        connectMeta.setDbSchema("geelato");
        connectMeta.setDbType("Mysql");
        connectMeta.setEnableStatus(1);
        connectMeta.setDbPassword("123456");
        Map connectMetaMap = this.dao.save(connectMeta);

        // 保存所有的数据表元数据
        this.saveJavaMetaToDb(connectMetaMap.get("id").toString(), entityMetas);
    }

    /**
     * 将元数据信息保存到服务端，一般用于开发环境初始化，创建完表之后执行
     *
     * @param entityMetas
     */
    private void saveJavaMetaToDb(String id, Collection<EntityMeta> entityMetas) {
        for (EntityMeta em : entityMetas) {
            TableMeta tm = em.getTableMeta();
            tm.setConnectId(id);
            tm.setLinked(1);
            tm.setEnableStatus(1);
            Map table = dao.save(tm);
            for (FieldMeta fm : em.getFieldMetas()) {
                ColumnMeta cm = fm.getColumn();
                cm.setTableId(table.get("id").toString());
                cm.setLinked(1);
                // 已有name不需再设置
                // cm.setTableId(em.getTableMeta().getTableName());
                dao.save(cm);
            }
            // 保存外键关系
            for (TableForeign ft : em.getTableForeigns()) {
                ft.setEnableStatus(1);
                dao.save(ft);
            }
        }
    }


    /**
     * 从数据库中删除实体对应的表
     *
     * @param entityName       实体名称
     * @param dropBeforeCreate 存在表时，是否删除
     */
    public void createOrUpdateOneTable(String entityName, boolean dropBeforeCreate) {
        //createOrUpdateOneTable(metaManager.getByEntityName(entityName,false), dropBeforeCreate);
        EntityMeta entityMeta = metaManager.getByEntityName(entityName, false);
        if (TableTypeEnum.TABLE.getCode().equals(entityMeta.getTableMeta().getTableType())) {
            createOrUpdateOneTable(entityMeta);
        } else if (TableTypeEnum.VIEW.getCode().equals(entityMeta.getTableMeta().getTableType())) {
            createOrUpdateView(entityName, entityMeta.getTableMeta().getViewSql());
        }
    }

    private void createOrUpdateOneTable(EntityMeta em) {
        boolean isExistsTable = true;
        Map existscolumnMap = new HashMap();
        String tableName = Strings.isEmpty(em.getTableName()) ? em.getEntityName() : em.getTableName();
        List<Map<String, Object>> columns = dao.queryForMapList("queryColumnsByTableName", SqlParams.map("tableName", tableName));
        if (columns == null || columns.size() == 0) {
            isExistsTable = false;
        } else {
            for (Map<String, Object> columnMap : columns) {
                existscolumnMap.put(columnMap.get("COLUMN_NAME"), columnMap);
            }
        }
        // 通过create table创建的字段
        ArrayList<JSONObject> createList = new ArrayList<>();
        // 通过alert table创建的字段
        ArrayList<JSONObject> addList = new ArrayList<>();
        // 通过alert table修改的字段
        ArrayList<JSONObject> modifyList = new ArrayList<>();
        // 通过alert table删除的字段
        ArrayList<JSONObject> deleteList = new ArrayList<>();
        // 数据表中 非主键的唯一约束索引
        ArrayList<JSONObject> indexList = new ArrayList<>();
        ArrayList<String> primaryList = new ArrayList<>();
        ArrayList<JSONObject> uniqueList = new ArrayList<>();
        // 数据表中 外键
        ArrayList<JSONObject> foreignList = new ArrayList<>();
        ArrayList<JSONObject> delForeignList = new ArrayList<>();
        // 排序
        for (FieldMeta fm : em.getFieldMetas()) {
            if (fm.getColumn().getEnableStatus() == EnableStatusEnum.DISABLED.getCode()) {
                continue;
            }
            try {
                JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(fm.getColumn()));
                if (existscolumnMap.containsKey(fm.getColumnName())) {
                    modifyList.add(jsonColumn);
                } else {
                    addList.add(jsonColumn);
                }
                createList.add(jsonColumn);
                // primary key
                if (fm.getColumn().isKey() && Strings.isNotEmpty(fm.getColumn().getName())) {
                    primaryList.add(fm.getColumn().getName());
                }
                // unique index
                if (fm.getColumn().isUniqued()) {
                    uniqueList.add(jsonColumn);
                }
            } catch (Exception e) {
                if (e.getMessage().indexOf("Duplicate column name") != -1) {
                    logger.info("column " + fm.getColumnName() + " is exists，ignore.");
                } else {
                    throw e;
                }
            }
        }
        /*for (TableForeign tf : em.getTableForeigns()) {
            if (tf.getEnableStatus() == EnableStatusEnum.DISABLED.getCode()) {
                continue;
            }
            JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(tf));
            foreignList.add(jsonColumn);
        }*/
        if (!isExistsTable) {
            createTable(em.getTableMeta(), createList, foreignList);
        } else {
            // 唯一约束索引
            List<SchemaIndex> schemaIndexList = metaManager.queryIndexes(em.getTableName());
            for (SchemaIndex meta : schemaIndexList) {
                JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(meta));
                indexList.add(jsonColumn);
            }
            // 外键
            /*List<TableForeignKey> foreignKeyList = metaManager.queryForeignKeys(em.getTableName());
            for (TableForeignKey meta : foreignKeyList) {
                JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(meta));
                delForeignList.add(jsonColumn);
            }*/

            upgradeTable(em.getTableMeta(), addList, modifyList, indexList, uniqueList, String.join(",", primaryList));
        }
    }

    private void createOrUpdateOneTable(EntityMeta em, boolean dropBeforeCreate) {

        if (dropBeforeCreate) {
            logger.info("  drop entity " + em.getTableName());
            dao.execute("dropOneTable", SqlParams.map("tableName", em.getTableName()));
        }
        logger.info("  create or update an entity " + em.getTableName());

        // 检查表是否存在，或取已存在的列元数据
        boolean isExistsTable = true;
        Map existscolumnMap = new HashMap();
        List<Map<String, Object>> columns = dao.queryForMapList("queryColumnsByTableName", SqlParams.map("tableName", em.getTableName()));
        if (columns == null || columns.size() == 0) {
            isExistsTable = false;
        } else {
            for (Map<String, Object> columnMap : columns) {
                existscolumnMap.put(columnMap.get("COLUMN_NAME"), columnMap);
            }
        }
        // 通过create table创建的字段
        ArrayList<JSONObject> createList = new ArrayList<>();
        // 通过alert table创建的字段
        ArrayList<JSONObject> addList = new ArrayList<>();
        // 通过alert table修改的字段
        ArrayList<JSONObject> modifyList = new ArrayList<>();
        // 通过alert table删除的字段
        ArrayList<JSONObject> deleteList = new ArrayList<>();
        ArrayList<JSONObject> uniqueList = new ArrayList<>();

        for (FieldMeta fm : em.getFieldMetas()) {
            try {
                if (defaultColumnLengthMap.containsKey(fm.getColumnName())) {
                    int len = defaultColumnLengthMap.get(fm.getColumnName()).intValue();

                    fm.getColumn().setCharMaxLength(len);
                    fm.getColumn().setNumericPrecision(len);
                    fm.getColumn().afterSet();
                }

                JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(fm.getColumn()));

                if (existscolumnMap.containsKey(fm.getColumnName())) {
                    modifyList.add(jsonColumn);
                } else {
                    addList.add(jsonColumn);
                }
                createList.add(jsonColumn);
                if (fm.getColumn().isUniqued()) {
                    uniqueList.add(jsonColumn);
                }
            } catch (Exception e) {
                if (e.getMessage().indexOf("Duplicate column name") != -1) {
                    logger.info("column " + fm.getColumnName() + " is exists，ignore.");
                } else {
                    throw e;
                }
            }
        }
        Map<String, Object> map = new HashMap<>();
        map.put("tableName", em.getTableName());
        map.put("createList", createList);
        map.put("addList", addList);
        map.put("modifyList", modifyList);
        map.put("deleteList", deleteList);
        map.put("uniqueList", uniqueList);
        map.put("foreignList", em.getTableForeigns());
        map.put("existsTable", isExistsTable);

        dao.execute("createOrUpdateOneTable", map);
    }

    /**
     * 创建数据库表
     *
     * @param tableMeta
     * @param createColumnList
     */
    private void createTable(TableMeta tableMeta, List<JSONObject> createColumnList, List<JSONObject> foreignList) {
        Map<String, Object> map = new HashMap<>();
        // ArrayList<JSONObject> defaultColumnList = getDefaultColumn();
        //  createColumnList.addAll(defaultColumnList);  //add org.geelato.core.meta.model.entity.BaseEntity
        ArrayList<JSONObject> uniqueColumnList = getUniqueColumn(createColumnList);
        String primaryKey = getPrimaryColumn(createColumnList);
        // 表单信息
        map.put("tableName", tableMeta.getEntityName());
        map.put("tableTitle", tableMeta.getTitle());
        // 表字段 - 添加
        map.put("addList", createColumnList);
        createColumnList.sort(new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject o1, JSONObject o2) {
                return o1.getIntValue("ordinalPosition") - o2.getIntValue("ordinalPosition");
            }
        });
        // 表索引 - 唯一约束 - 添加
        map.put("uniqueList", uniqueColumnList);
        // 表索引 - 主键 - 添加
        map.put("primaryKey", primaryKey);
        // 表外键 - 添加
        map.put("foreignList", foreignList);
        dao.execute("createOneTable", map);
    }

    /**
     * 更新数据库表
     *
     * @param tableMeta
     * @param addList
     * @param modifyList
     * @param indexList
     * @param uniqueList
     * @param primaryKey
     */
    private void upgradeTable(TableMeta tableMeta, List<JSONObject> addList, List<JSONObject> modifyList, List<JSONObject> indexList, List<JSONObject> uniqueList, String primaryKey) {
        Map<String, Object> map = new HashMap<>();
        // 表单信息
        map.put("tableName", tableMeta.getTableName());
        // 表注释 - 更新
        map.put("tableTitle", tableMeta.getTitle());
        // 表字段 - 新增
        map.put("addList", addList);
        addList.sort(new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject o1, JSONObject o2) {
                return o1.getIntValue("ordinalPosition") - o2.getIntValue("ordinalPosition");
            }
        });
        // 表字段 - 更新
        map.put("modifyList", modifyList);
        // map.put("deleteList", deleteList);
        // 表索引 - 唯一约束 - 删除
        map.put("indexList", indexList);
        // 表索引 - 主键 - 删除、添加
        map.put("primaryKey", primaryKey);
        // 表索引 - 唯一约束 - 添加
        map.put("uniqueList", uniqueList);
        dao.execute("upgradeOneTable", map);
    }

    private ArrayList<JSONObject> getDefaultColumn() {
        ArrayList<JSONObject> defaultColumnList = new ArrayList<>();
        List<ColumnMeta> defaultColumnMetaList = MetaManager.singleInstance().getDefaultColumn();
        for (ColumnMeta columnMeta : defaultColumnMetaList) {
            columnMeta.setComment(String.format("'%s'", Strings.isNotBlank(columnMeta.getComment()) ? columnMeta.getComment() : columnMeta.getTitle()));
            JSONObject jsonColumn = JSONObject.parseObject(JSONObject.toJSONString(columnMeta));
            defaultColumnList.add(jsonColumn);
        }

        return defaultColumnList;
    }

    private ArrayList<JSONObject> getUniqueColumn(List<JSONObject> jsonObjectList) {
        ArrayList<JSONObject> defaultColumnList = new ArrayList<>();
        for (JSONObject jsonObject : jsonObjectList) {
            if (jsonObject.getBoolean("uniqued")) {
                defaultColumnList.add(jsonObject);
            }
        }
        return defaultColumnList;
    }

    private String getPrimaryColumn(List<JSONObject> jsonObjectList) {
        Set<String> columnNames = new HashSet<>();
        for (JSONObject jsonObject : jsonObjectList) {
            if (jsonObject.getBoolean("key") && Strings.isNotEmpty(jsonObject.getString("name"))) {
                columnNames.add(jsonObject.getString("name"));
            }
        }
        return String.join(",", columnNames);
    }


    public void createOrUpdateView(String view, String sql) {
        if (Strings.isBlank(view) || Strings.isBlank(sql)) {
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("viewName", view);
        map.put("viewSql", sql);   //TODO 对sql进行检查
        dao.execute("createOneView", map);
    }

    public boolean validateViewSql(String connectId, String sql) throws SQLException {
        if (Strings.isBlank(connectId) || Strings.isBlank(sql)) {
            return false;
        }
        // 查询数据库连接信息
        ConnectMeta connectMeta = dao.queryForObject(ConnectMeta.class, connectId);
        // 创建一个数据库连接对象，但不要打开连接。
        Connection conn = ConnectUtils.getConnection(connectMeta);
        // 创建一个 Statement 对象，但不要执行它。
        Statement stmt = conn.createStatement();
        // 使用 Statement 对象的 setQueryTimeout() 方法设置查询超时时间，以确保 SQL 语句在一定时间内执行完毕。
        // 设置查询超时时间为 1 秒
        stmt.setQueryTimeout(1);
        // 使用 Statement 对象的 execute() 方法执行 SQL 语句。如果 SQL 语句正确，execute() 方法将返回 true，否则返回 false。
        boolean isValid = stmt.execute(sql);
        stmt.close();
        conn.close();

        return isValid;
    }
}
