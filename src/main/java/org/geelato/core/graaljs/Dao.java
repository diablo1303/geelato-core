package org.geelato.core.graaljs;

import org.geelato.core.Ctx;
import org.geelato.core.api.ApiPagedResult;
import org.geelato.core.ds.DataSourceManager;
import org.geelato.core.gql.GqlManager;
import org.geelato.core.gql.execute.BoundPageSql;
import org.geelato.core.gql.parser.QueryCommand;
import org.geelato.core.sql.SqlManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

public class Dao {
    private final GqlManager gqlManager = GqlManager.singleInstance();
    private final SqlManager sqlManager = SqlManager.singleInstance();
    private org.geelato.core.orm.Dao ormDao;

    public Dao(){
        DataSource ds= DataSourceManager.singleInstance().getDataSource("primary");
        JdbcTemplate jdbcTemplate=new JdbcTemplate();
        jdbcTemplate.setDataSource(ds);
        this.ormDao=new org.geelato.core.orm.Dao(jdbcTemplate);
    }

    public ApiPagedResult list(String gql){
        QueryCommand command = gqlManager.generateQuerySql(gql, getSessionCtx());
        BoundPageSql boundPageSql = sqlManager.generatePageQuerySql(command);
        return ormDao.queryForMapList(boundPageSql, false);
    }



    public String save(String gql){
        return null;
    }

    public String delete(String gql){
        return null;
    }
    private Ctx getSessionCtx() {
        return new Ctx();
    }
}
