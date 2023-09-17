package org.geelato.core.gql.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.geelato.core.meta.MetaManager;
import org.geelato.core.meta.model.entity.EntityMeta;
import org.geelato.core.meta.model.field.FieldMeta;
import org.geelato.core.mvc.Ctx;
import org.geelato.utils.UIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.w3c.dom.stylesheets.LinkStyle;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author geelato
 * 解析json字符串，并返回参数map
 */
public class JsonTextSaveParser {

    private static Logger logger = LoggerFactory.getLogger(JsonTextSaveParser.class);
    private MetaManager metaManager = MetaManager.singleInstance();

    private final static String SUB_ENTITY_FLAG = "#";
    private final static String KW_BIZ = "@biz";

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    /**
     * @param jsonText
     * @param ctx
     * @return
     */
    public SaveCommand parse(String jsonText, Ctx ctx) {
        JSONObject jo = JSON.parseObject(jsonText);
        CommandValidator validator = new CommandValidator();
        if (jo.containsKey(KW_BIZ)) {
            jo.remove(KW_BIZ);
        }
        String entityName = jo.keySet().iterator().next();
        return parse(ctx, entityName, jo.getJSONObject(entityName), validator);
    }


    public List<SaveCommand> parseBatch(String jsonText, Ctx ctx) {
        JSONObject jo = JSON.parseObject(jsonText);
        CommandValidator validator = new CommandValidator();
        if (jo.containsKey(KW_BIZ)) {
            jo.remove(KW_BIZ);
        }
        String entityName = jo.keySet().iterator().next();
        return parseBatch(ctx, entityName, jo.getJSONArray(entityName), validator);
    }
    public List<SaveCommand> parseMulti(String jsonText, Ctx ctx) {
        JSONObject jo = JSON.parseObject(jsonText);
        CommandValidator validator = new CommandValidator();
        if (jo.containsKey(KW_BIZ)) {
            jo.remove(KW_BIZ);
        }
        return parseMulti(ctx, jo, validator);
    }
    private List<SaveCommand> parseMulti(Ctx ctx, JSONObject jo, CommandValidator validator){
        List<SaveCommand> saveCommandList=new ArrayList<>();
        Iterator iterator = jo.keySet().iterator();
        while(iterator.hasNext()){
            String entityName = (String) iterator.next();
            String value = jo.getString(entityName);
            JSONObject o=JSONObject.parseObject(value);
            SaveCommand saveCommand=parse(ctx,entityName, o,validator);
            saveCommandList.add(saveCommand);
        }
        return saveCommandList;
    }
    private List<SaveCommand> parseBatch(Ctx ctx, String commandName, JSONArray jsonArray, CommandValidator validator){
        List<SaveCommand> saveCommandList=new ArrayList<>();
        for (Object o:jsonArray) {
            SaveCommand saveCommand=parse(ctx,commandName, (JSONObject) o,validator);
            saveCommandList.add(saveCommand);
        }
        return saveCommandList;
    }
    /**
     * 递归解析保存操作命令，里面变更在执行期再解析，不在此解析
     *
     * @param ctx
     * @param commandName
     * @param jo
     * @param validator
     * @return
     */
    private SaveCommand parse(Ctx ctx, String commandName, JSONObject jo, CommandValidator validator) {
        Assert.isTrue(validator.validateEntity(commandName), validator.getMessage());
        SaveCommand command = new SaveCommand();
        command.setEntityName(commandName);
        EntityMeta entityMeta = metaManager.getByEntityName(commandName);
        Map<String, Object> params = new HashMap();

        jo.keySet().forEach(key -> {
            if (key.startsWith(SUB_ENTITY_FLAG)) {
                // 解析子实体
                // 子实体是数组还是实体
                Object sub = jo.get(key);
                if (sub instanceof JSONObject) {
                    SaveCommand subCommand = parse(ctx, key.substring(1), (JSONObject) sub, validator);
                    subCommand.setParentCommand(command);
                    command.getCommands().add(subCommand);
                } else if (sub instanceof JSONArray) {
                    ((JSONArray) sub).forEach(subJo -> {
                        SaveCommand subCommand = parse(ctx, key.substring(1), (JSONObject) subJo, validator);
                        subCommand.setParentCommand(command);
                        command.getCommands().add(subCommand);
                    });
                } else {
                    validator.appendMessage(key + "的值应为object或array");
                }
            } else {
                // 字段
                validator.validateField(key, "字段");
                // 对于boolean类型的值，转为数值，以值存到数据库
                FieldMeta fieldMeta = entityMeta.getFieldMeta(key);
                if (fieldMeta != null && (boolean.class.equals(fieldMeta.getFieldType()) || Boolean.class.equals(fieldMeta.getFieldType()))) {
                    String v = jo.getString(key).toLowerCase();
                    params.put(key, "true".equals(v) ? 1 : ("false".equals(v) ? 0 : v));
                } else {
                    params.put(key, jo.getString(key));
                }
            }
        });
        Assert.isTrue(validator.isSuccess(), validator.getMessage());

        String[] fields = new String[params.keySet().size()];
        params.keySet().toArray(fields);
        String PK = validator.getPK();

        String newDataString = simpleDateFormat.format(new Date());
        if (validator.hasPK(fields) && StringUtils.hasText(jo.getString(PK))) {
            //update
            FilterGroup fg = new FilterGroup();
            fg.addFilter(PK, jo.getString(PK));
            command.setWhere(fg);
            command.setCommandType(CommandType.Update);
            Object pkValue = params.remove(PK);
            if (validator.hasKeyField("updateAt")) {
                params.put("updateAt", newDataString);
            }
            if (validator.hasKeyField("updater")) {
                params.put("updater", ctx.get("userId"));
            }
            String[] updateFields = new String[params.keySet().size()];
            params.keySet().toArray(updateFields);
            command.setFields(updateFields);
            command.setValueMap(params);
            command.setPK(jo.getString(PK));
        } else {
            //insert
            command.setCommandType(CommandType.Insert);
            Map<String, Object> entity = metaManager.newDefaultEntity(commandName);
            entity.putAll(params);
            entity.put(PK, UIDGenerator.generate(1));
            if (entity.containsKey("createAt")) {
                entity.put("createAt", newDataString);
            }
            if (entity.containsKey("creator")) {
                entity.put("creator", ctx.get("userId"));
            }
            if (entity.containsKey("updateAt")) {
                entity.put("updateAt", newDataString);
            }
            if (entity.containsKey("updater")) {
                entity.put("updater", ctx.get("userId"));
            }
            if (entity.containsKey("tenantCode")) {
                entity.put("tenantCode", ctx.get("tenantCode"));
            }
            if (entity.containsKey("buId")) {
                entity.put("buId",ctx.getCurrentUser().getBuId());
            }
            if (entity.containsKey("deptId")) {
                entity.put("deptId", ctx.getCurrentUser().getDeptId());
            }
            String[] insertFields = new String[entity.size()];
            entity.keySet().toArray(insertFields);
            command.setFields(insertFields);
            command.setValueMap(entity);
            command.setPK(entity.get(PK).toString());
        }
        return command;
    }
}
