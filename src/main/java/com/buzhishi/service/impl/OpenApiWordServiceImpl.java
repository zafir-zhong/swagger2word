package com.buzhishi.service.impl;

import com.buzhishi.model.ModelAttr;
import com.buzhishi.model.Request;
import com.buzhishi.model.Response;
import com.buzhishi.model.Table;
import com.buzhishi.service.OpenApiWordService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.buzhishi.utils.JsonUtils;
import com.buzhishi.utils.RequestUtils;
import com.buzhishi.utils.ResponseUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * @Author XiuYin.Cui
 * @Date 2018/1/12
 **/
@SuppressWarnings({"unchecked", "rawtypes"})
@Slf4j
@Service
public class OpenApiWordServiceImpl implements OpenApiWordService {

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public Map<String, Object> tableList(String swaggerUrl) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String jsonStr = restTemplate.getForObject(swaggerUrl, String.class);
            resultMap = tableListFromString(jsonStr);
            // log.debug(JsonUtils.writeJsonStr(resultMap));
        } catch (Exception e) {
            log.error("parse error", e);
        }
        return resultMap;
    }

    @Override
    public Map<String, Object> tableListFromString(String jsonStr) throws IOException {
        Map<String, Object> resultMap = new HashMap<>();
        List<Table> result = new ArrayList<>();
        try {
            Map<String, Object> map = getResultFromString(result, jsonStr);
            Map<String, List<Table>> tableMap = result.stream().parallel().collect(Collectors.groupingBy(Table::getTitle));
            resultMap.put("tableMap", new TreeMap<>(tableMap));
            resultMap.put("info", map.get("info"));

            // log.debug(JsonUtils.writeJsonStr(resultMap));
        } catch (Exception e) {
            log.error("parse error", e);
        	throw e;
        }
        return resultMap;
    }

	@Override
    public Map<String, Object> tableList(MultipartFile jsonFile) throws IOException {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String jsonStr = new String(jsonFile.getBytes());
            resultMap = tableListFromString(jsonStr);
            // log.debug(JsonUtils.writeJsonStr(resultMap));
        } catch (Exception e) {
            log.error("parse error", e);
        	throw e;
        }
        return resultMap;
    }

    private Map<String, Object> getResultFromString(List<Table> result, String jsonStr) throws IOException {
        // convert JSON string to Map
        Map<String, Object> map = JsonUtils.readValue(jsonStr, HashMap.class);

        //??????model
        Map<String, ModelAttr> definitinMap = parseComponents(map);

        //??????paths
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) map.get("paths");

        //????????????????????????????????????????????????????????????
        List<String> defaultConsumes = (List) map.get("consumes");

        //????????????????????????????????????????????????????????????
        List<String> defaultProduces = (List) map.get("produces");

        if (paths != null) {

            Iterator<Entry<String, Map<String, Object>>> it = paths.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Map<String, Object>> path = it.next();

                // 0. ???????????????????????????????????????????????????
                Map<String, Object> methods = (Map<String, Object>) path.getValue();
                List<LinkedHashMap> commonParameters = (ArrayList) methods.get("parameters");

                Iterator<Entry<String, Object>> it2 = path.getValue().entrySet().iterator();
                // 1.????????????
                String url = path.getKey();
                String requestType = null;
                while (it2.hasNext()) {
                	try {
                		Entry<String, Object> request = it2.next();

                		// 2.???????????????????????? get,post,delete,put ??????
                		requestType = request.getKey();

                		if ("parameters".equals(requestType)) {
                			continue;
                		}

                		Map<String, Object> content = (Map<String, Object>) request.getValue();

                		// 4. ????????????????????????
                		String title = String.valueOf(((List) content.get("tags")).get(0));

                		// 5.????????? ??????????????????
                		String tag = String.valueOf(content.get("operationId"));

                		// 6.????????????
                		Object descObj = content.get("description");
                		String description = descObj == null ? "" : descObj.toString();

                		// 7. ?????????
                		List<LinkedHashMap> parameters = (ArrayList) content.get("parameters");

                		if (!CollectionUtils.isEmpty(parameters)) {
                			if (commonParameters != null) {
                				parameters.addAll(commonParameters);
                			}
                		} else {
                			if (commonParameters != null) {
                				parameters = commonParameters;
                			}
                		}

                		// 8.?????????
                		Map<String, Object> responses = (LinkedHashMap) content.get("responses");

                		// 9.?????????????????????????????? multipart/form-data
                		List<String> requestParamsFormates = getRequestParamsFormate(content);
                		String requestForm = StringUtils.join(requestParamsFormates, ",");

                		// ??????????????????200???????????????
                		Map<String, Object> obj = (Map<String, Object>) responses.get("200");
                		Map<String, Object> requestBody = (Map<String, Object>) content.get("requestBody");

                		// 10.?????????????????????????????? application/json
                		List<String> responseParamsFormates = getResponseParamsFormate(obj);
                		String responseForm = StringUtils.join(responseParamsFormates, ",");

                		//??????Table
                		Table table = new Table();

                		table.setTitle(title);
                		table.setUrl(url);
                		table.setTag(tag);
                		table.setDescription(description);
                		table.setRequestForm(requestForm);
                		table.setResponseForm(responseForm);
                		table.setRequestType(requestType);
                		table.setRequestList(processRequestList(parameters, requestBody, definitinMap));

                		table.setResponseList(processResponseCodeList(responses, definitinMap));
                		if (obj != null && obj.get("content") != null) {
                			table.setModelAttr(processResponseModelAttrs(obj, definitinMap));
                		}

                		//??????
                		table.setRequestParam(processRequestParam(table.getRequestList()));
                		table.setResponseParam(processResponseParam1(obj, definitinMap));

                		result.add(table);
                	} catch (Exception e) {
                		StringWriter sw = new StringWriter();
                		PrintWriter pw = new PrintWriter(sw);
                		e.printStackTrace(pw);
                		throw new JsonProcessingException(url + "?????????????????????: " + requestType + "?????? " + e.getMessage()) {};
                	}
                }
            }
        }
        return map;
    }

    /**
     * ????????????????????? ????????? multipart/form-data
     */
    private List<String> getRequestParamsFormate(Map<String, Object> obj) {
        Map<String, Object> requestBody = (LinkedHashMap) obj.get("requestBody");
        List<String> requestTypes = new ArrayList();
        if (requestBody != null) {
            Map<String, Map> content = (LinkedHashMap) requestBody.get("content");
            Set keys = content.keySet();
            return new ArrayList<String>(keys);
        }
        return requestTypes;
    }

    /**
     * ?????????????????????????????? application/json
     * @throws Exception
     */
    private List<String> getResponseParamsFormate(Map<String, Object> responseObj) {
        Map<String, Map> content = (LinkedHashMap) responseObj.get("content");
        List<String> responseTypes = new ArrayList();
        if (content != null) {
            Set keys = content.keySet();
            return new ArrayList<String>(keys);
        }
        return responseTypes;
    }

    /**
     * ????????????????????????
     *
     * @param parameters
     * @param definitinMap
     * @return
     * @throws JsonProcessingException
     */
    private List<Request> processRequestList(List<LinkedHashMap> parameters, Map<String, Object> requestBody, Map<String, ModelAttr> definitinMap) throws JsonProcessingException {
        List<Request> requestList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(parameters)) {
            for (Map<String, Object> param : parameters) {
                Object in = param.get("in");
                Request request = new Request();
                request.setName(String.valueOf(param.get("name")));

                Map<String, String> schema1 = (Map) param.get("schema");

                request.setType(schema1 == null ? " " : schema1.get("type").toString());
                // request.setType(param.get("type") == null ? "object" : param.get("type").toString());
                if (param.get("format") != null) {
                    request.setType(request.getType() + "(" + param.get("format") + ")");
                }
                request.setParamType(String.valueOf(in));
                // ????????????????????????
                if (in != null && "body".equals(in)) {
                    Map<String, Object> schema = (Map) param.get("schema");
                    Object ref = schema.get("$ref");
                    // ????????????????????????
                    if (schema.get("type") != null && "array".equals(schema.get("type"))) {
                        ref = ((Map) schema.get("items")).get("$ref");
                        request.setType("array");
                    }
                    if (ref != null) {
                        request.setType(request.getType() + ":" + ref.toString().replaceAll("#/definitions/", ""));
                        request.setModelAttr(definitinMap.get(ref));
                    }
                }
                // ????????????
                request.setRequire(false);
                if (param.get("required") != null) {
                    request.setRequire((Boolean) param.get("required"));
                }
                // ????????????
                request.setRemark(String.valueOf(param.get("description")));
                requestList.add(request);
            }
        }

        if (requestBody != null) {
            Map<String, Map> content = (LinkedHashMap) requestBody.get("content");

            try {
            	RequestUtils.validateRequestKey(content);
            } catch(Exception e) {
            	throw new JsonProcessingException("requestybody ?????? " + e.getMessage()) {};
            }

            Iterator<Map.Entry<String, Map>> applications = content.entrySet().iterator();
            while (applications.hasNext()) {
                Map.Entry<String, Map> application = applications.next();

                if (application.getValue() != null) {
                    Request request = new Request();

                    Map<String, Object> schema = (Map<String, Object>) application.getValue().get("schema");
                    request.setName(" ");
                    request.setType(schema == null ? " " : schema.get("type").toString());
                    request.setParamType("body");

                    Object ref = schema.get("$ref");

                    if (schema.get("type") != null && "array".equals(schema.get("type"))) {
                        ref = ((Map) schema.get("items")).get("$ref");
                        request.setType("array");
                    }
                    if (ref != null) {
                        // request.setType(request.getType() + ":" + ref.toString().replaceAll("#/definitions/", ""));
                        request.setType("object");
                        request.setModelAttr(definitinMap.get(ref));
                    }
                    if (schema.get("properties") != null) {
                        ArrayList<String> requiredArr = new ArrayList<String>();
                        if (schema.get("required") != null) {
                            requiredArr = (ArrayList<String>) schema.get("required");
                        }
                        request.setModelAttr(getRequestSchemaModelAttr(schema, requiredArr));
                    }

                    // ????????????
                    request.setRequire(true);

                    // ????????????
                    requestList.add(request);
                }
            }
        }
        return requestList;
    }


    /**
     * ?????????????????????
     *
     * @param responses ???????????????????????????
     * @return
     */
    private List<Response> processResponseCodeList(Map<String, Object> responses, Map<String, ModelAttr> definitinMap ) throws JsonProcessingException  {
        List<Response> responseList = new ArrayList<>();
        Iterator<Map.Entry<String, Object>> resIt = responses.entrySet().iterator();
        while (resIt.hasNext()) {
            Map.Entry<String, Object> entry = resIt.next();
            Response response = new Response();
            // ????????? 200 201 401 403 404 ??????
            response.setName(entry.getKey());
            LinkedHashMap<String, Object> statusCodeInfo = (LinkedHashMap) entry.getValue();
            response.setDescription(String.valueOf(statusCodeInfo.get("description")));

            Map<String, Map> content = (Map) statusCodeInfo.get("content");

            if (content != null) {
            	try {
            		ResponseUtils.validateResponseKey(content);
            	} catch(Exception e) {
            		throw new JsonProcessingException("response?????? " + entry.getKey() + "?????? " + e.getMessage()) {};
            	}
                // responses??????application??????????????????
                Iterator<Map.Entry<String, Map>> applications = content.entrySet().iterator();

                while (applications.hasNext()) {
                    Map.Entry<String, Map> application = applications.next();

                    if (application.getValue() != null) {
                        Object schema = application.getValue().get("schema");
                        if (schema != null) {
                            Object originalRef = ((LinkedHashMap) schema).get("originalRef");
                            response.setRemark(originalRef == null ? "" : originalRef.toString());
                        }
                        responseList.add(response);
                    }
                }
            } else {
                String ref = String.valueOf(statusCodeInfo.get("$ref"));

                if (ref != "") {
                    ModelAttr modelAttr = definitinMap.get(ref);
                    response.setDescription(modelAttr.getDescription());
                }

                responseList.add(response);
            }
        }
        return responseList;
    }

    /**
     * ????????????????????????
     *
     * @param responseObj
     * @param definitinMap
     * @return
     */
    private ModelAttr processResponseModelAttrs(Map<String, Object> responseObj, Map<String, ModelAttr> definitinMap) {
        Map<String, Map> content = (Map) responseObj.get("content");
        //????????????
        ModelAttr modelAttr = new ModelAttr();

        Iterator<Map.Entry<String, Map>> applications = content.entrySet().iterator();

        while (applications.hasNext()) {
            Map.Entry<String, Map> application = applications.next();

            if (application.getValue() != null) {

                Map<String, Object> schema = (Map<String, Object>) application.getValue().get("schema");
                String type = (String) schema.get("type");
                String ref = null;
                //??????
                if ("array".equals(type)) {
                    Map<String, Object> items = (Map<String, Object>) schema.get("items");
                    if (items != null && items.get("$ref") != null) {
                        ref = (String) items.get("$ref");
                    }
                }
                //??????
                if (schema.get("$ref") != null) {
                    ref = (String) schema.get("$ref");
                }

                //????????????
                modelAttr.setType(StringUtils.defaultIfBlank(type, StringUtils.EMPTY));

                if (StringUtils.isNotBlank(ref) && definitinMap.get(ref) != null) {
                    modelAttr = definitinMap.get(ref);
                }

                // ?????????ref?????? ??????properties??????
                if (schema.get("properties") != null) {
                    modelAttr = getSchemaModelAttr(schema);
                }
            }
        }
        return modelAttr;
    }

    /**
     * ??????components
     *
     * @param map
     * @return
     */
    private Map<String, ModelAttr> parseComponents(Map<String, Object> map) {
        Map<String, Object> definitions = (Map<String, Object>) map.get("components");
        Map<String, ModelAttr> definitinMap = new HashMap<>(256);
        if (definitions != null) {
            Iterator<String> modelNameIt = definitions.keySet().iterator();
            /**
             "components": {
             "requestBodies": {},
             "schemas": {}
             }
             */
            while (modelNameIt.hasNext()) {
                String modeName = modelNameIt.next();
                /**
                 "schemas": {
                 "cat":{},
                 "dog":{},
                 }
                 */
                Map<String, Map<String, Object>> modeContent = (Map<String, Map<String, Object>>) definitions.get(modeName);

                if (modeContent != null) {
                    Iterator<String> modeContentIt = modeContent.keySet().iterator();

                    while (modeContentIt.hasNext()) {
                        String componentsGrandChildName = modeContentIt.next();

                        getAndPutModelAttr(modeContent, definitinMap, modeName, componentsGrandChildName);
                    }
                }
            }
        }
        return definitinMap;
    }

    /**
     * ????????????ModelAttr
     * ???$ref????????????????????????
     */
    private ModelAttr getAndPutModelAttr(Map<String, Map<String, Object>> swaggerMap, Map<String, ModelAttr> resMap, String parentName, String modeName) {
        ModelAttr modeAttr;
        if ((modeAttr = resMap.get("#/components/" + parentName + "/" + modeName)) == null) {
            modeAttr = new ModelAttr();
            resMap.put("#/components/" + parentName + "/" + modeName, modeAttr);
        } else if (resMap.get("#/components/" + parentName + "/" + modeName) != null) {
            return resMap.get("#/components/" + parentName + "/" + modeName);
        }
        Map<String, Object> modeProperties = (Map<String, Object>) swaggerMap.get(modeName).get("properties");
        List<ModelAttr> attrList = new ArrayList<>();
        // ??????required???????????????properties????????????????????????
        ArrayList modeRequired = (ArrayList) swaggerMap.get(modeName).get("required");

        if (modeProperties != null) {
            Iterator<Entry<String, Object>> mIt = modeProperties.entrySet().iterator();

            //????????????
            while (mIt.hasNext()) {
                Entry<String, Object> mEntry = mIt.next();
                Map<String, Object> attrInfoMap = (Map<String, Object>) mEntry.getValue();
                ModelAttr child = new ModelAttr();
                child.setName(mEntry.getKey());
                child.setType((String) attrInfoMap.get("type"));
                if (attrInfoMap.get("format") != null) {
                    child.setType(child.getType() + "(" + attrInfoMap.get("format") + ")");
                }
                child.setType(StringUtils.defaultIfBlank(child.getType(), "object"));

                Object ref = attrInfoMap.get("$ref");
                Object items = attrInfoMap.get("items");
                if (ref != null || (items != null && (ref = ((Map) items).get("$ref")) != null)) {
                    String refName = ref.toString();
                    //?????? #/components/ ?????????
                    String clsName = refName.substring(21);
                    ModelAttr refModel = getAndPutModelAttr(swaggerMap, resMap, parentName, clsName);
                    if (refModel != null) {
                        child.setProperties(refModel.getProperties());
                    }
                    child.setType(child.getType() + ":" + clsName);
                }
                child.setDescription((String) attrInfoMap.get("description"));

                child.setRequire(false);
                if (modeRequired != null && modeRequired.contains(mEntry.getKey())) {
                    child.setRequire(true);
                }

                attrList.add(child);
            }
        }

        Object title = swaggerMap.get(modeName).get("title");
        Object description = swaggerMap.get(modeName).get("description");
        modeAttr.setClassName(title == null ? "" : title.toString());
        modeAttr.setDescription(description == null ? "" : description.toString());
        modeAttr.setProperties(attrList);
        return modeAttr;
    }

    /**
     * ????????????ModelAttr
     * ??????schema??????
     * ??????requestBody???????????????????????????
     */
    private ModelAttr getRequestSchemaModelAttr(Map<String, Object> schemaMap, ArrayList requiredArr) {
        ModelAttr modeAttr = new ModelAttr();
        Map<String, Object> modeProperties = (Map<String, Object>) schemaMap.get("properties");

        if ("array".equals(schemaMap.get("type"))) {
            Map items = (Map<String, Object>) schemaMap.get("items");

            if (items != null) {
                modeProperties = (Map<String, Object>) items.get("properties");
            }
        }

        if (modeProperties == null) {
            return null;
        }
        Iterator<Entry<String, Object>> mIt = modeProperties.entrySet().iterator();

        List<ModelAttr> attrList = new ArrayList<>();
        //????????????
        while (mIt.hasNext()) {
            Entry<String, Object> mEntry = mIt.next();
            Map<String, Object> attrInfoMap = (Map<String, Object>) mEntry.getValue();
            ModelAttr child = new ModelAttr();
            child.setName(mEntry.getKey());
            child.setType((String) attrInfoMap.get("type"));
            if (attrInfoMap.get("format") != null) {
                child.setType(child.getType() + "(" + attrInfoMap.get("format") + ")");
            }
            child.setType(StringUtils.defaultIfBlank(child.getType(), "object"));

            Object properties = attrInfoMap.get("properties");
            Object ref = attrInfoMap.get("$ref");
            Object items = attrInfoMap.get("items");
            if (properties != null || (items != null)) {
                ArrayList<String> childRequiredArr = new ArrayList<String>();
                if (attrInfoMap.get("required") != null) {
                    childRequiredArr = (ArrayList<String>) attrInfoMap.get("required");
                }
                ModelAttr refModel = getRequestSchemaModelAttr(attrInfoMap, childRequiredArr);
                if (refModel != null) {
                    child.setProperties(refModel.getProperties());
                }
                child.setType((String) attrInfoMap.get("type"));
            }
            child.setRequire(true);
            if (!requiredArr.contains(mEntry.getKey())) {
                child.setRequire(false);
            }
            child.setDescription((String) attrInfoMap.get("description"));
            attrList.add(child);
        }
        modeAttr.setClassName("");
        modeAttr.setDescription("");
        modeAttr.setProperties(attrList);
        return modeAttr;
    }

    /**
     * ????????????ModelAttr
     * ??????schema??????
     * ??????responseData???????????????????????????
     */
    private ModelAttr getSchemaModelAttr(Map<String, Object> schemaMap) {
        ModelAttr modeAttr = new ModelAttr();
        Map<String, Object> modeProperties = (Map<String, Object>) schemaMap.get("properties");

        if ("array".equals(schemaMap.get("type"))) {
            Map items = (Map<String, Object>) schemaMap.get("items");

            if (items != null) {
                modeProperties = (Map<String, Object>) items.get("properties");
            }
        }

        if (modeProperties == null) {
            return null;
        }
        Iterator<Entry<String, Object>> mIt = modeProperties.entrySet().iterator();

        List<ModelAttr> attrList = new ArrayList<>();
        //????????????
        while (mIt.hasNext()) {
            Entry<String, Object> mEntry = mIt.next();
            Map<String, Object> attrInfoMap = (Map<String, Object>) mEntry.getValue();
            ModelAttr child = new ModelAttr();
            child.setName(mEntry.getKey());

            child.setType((String) attrInfoMap.get("type"));
            if (attrInfoMap.get("format") != null) {
                child.setType(child.getType() + "(" + attrInfoMap.get("format") + ")");
            }
            child.setType(StringUtils.defaultIfBlank(child.getType(), "object"));

            Object properties = attrInfoMap.get("properties");
            Object ref = attrInfoMap.get("$ref");
            Object items = attrInfoMap.get("items");
            if (properties != null || (items != null)) {
                ModelAttr refModel = getSchemaModelAttr(attrInfoMap);
                if (refModel != null) {
                    child.setProperties(refModel.getProperties());
                }
                child.setType((String) attrInfoMap.get("type"));
            }
            child.setDescription((String) attrInfoMap.get("description"));
            attrList.add(child);
        }
        modeAttr.setClassName("");
        modeAttr.setDescription("");
        modeAttr.setProperties(attrList);
        return modeAttr;
    }

    /**
     * ???????????????
     *
     * @param responseObj
     * @return
     */
    private String processResponseParam(Map<String, Object> responseObj, Map<String, ModelAttr> definitinMap) throws JsonProcessingException {
        Map<String, Map> content = (Map) responseObj.get("content");
        if (content != null) {
            Iterator<Map.Entry<String, Map>> applications = content.entrySet().iterator();
            while (applications.hasNext()) {
                Map.Entry<String, Map> application = applications.next();

                if (application.getValue() != null) {
                    Map<String, Object> applicationContent = (Map<String, Object>) application.getValue();
                    if (applicationContent != null) {
                        Map<String, Object> schema = (Map<String, Object>) applicationContent.get("schema");
                        String type = (String) schema.get("type");
                        String ref = null;
                        // ??????
                        if ("array".equals(type)) {
                            Map<String, Object> items = (Map<String, Object>) schema.get("items");
                            if (items != null && items.get("$ref") != null) {
                                ref = (String) items.get("$ref");
                            }
                        }
                        // ??????ref
                        if (schema.get("$ref") != null) {
                            ref = (String) schema.get("$ref");
                        }
                        if (StringUtils.isNotEmpty(ref)) {
                            ModelAttr modelAttr = definitinMap.get(ref);
                            if (modelAttr != null && !CollectionUtils.isEmpty(modelAttr.getProperties())) {
                                Map<String, Object> responseMap = new HashMap<>(8);
                                for (ModelAttr subModelAttr : modelAttr.getProperties()) {
                                    responseMap.put(subModelAttr.getName(), getValue(subModelAttr.getType(), subModelAttr));
                                }
                                return JsonUtils.writeJsonStr(responseMap);
                            }
                        }
                        if (schema.get("properties") != null) {
                            ModelAttr modelAttr = getSchemaModelAttr(schema);
                            if (modelAttr != null) {
                                Map<String, Object> responseMap = new HashMap<>(8);
                                for (ModelAttr subModelAttr : modelAttr.getProperties()) {
                                    responseMap.put(subModelAttr.getName(), getValue(subModelAttr.getType(), subModelAttr));
                                }
                                return JsonUtils.writeJsonStr(responseMap);
                            }
                        }
                    }
                }
            }
        }
        return StringUtils.EMPTY;
    }

    private String processResponseParam1(Map<String, Object> responseObj, Map<String, ModelAttr> definitinMap) throws JsonProcessingException {
        Map<String, Map> content = (Map) responseObj.get("content");
        // if (responseObj != null && content.get("application/json") != null) {
        if (content != null) {
            Iterator<Map.Entry<String, Map>> applications = content.entrySet().iterator();
            while (applications.hasNext()) {
                Map.Entry<String, Map> application = applications.next();

                if (application.getValue() != null) {
                    Map<String, Map<String, Map>> applicationContent = (Map<String, Map<String, Map>>) application.getValue();
                    if (applicationContent != null) {
                        Map<String, Map> examples = (Map<String, Map>) applicationContent.get("examples");

                        if (examples != null) {
                            Map<String, Object> responseData = examples.get("response");

                            if (responseData != null) {
                                Object value = responseData.get("value");
                                return JsonUtils.writeJsonStr(value);
                            }
                        } else {
                            return "";
                        }

                    }
                }
            }
        }
        return StringUtils.EMPTY;
    }

    /**
     * ???????????????
     *
     * @param list
     * @return
     */
    private String processRequestParam(List<Request> list) throws IOException {
        Map<String, Object> headerMap = new LinkedHashMap<>();
        Map<String, Object> queryMap = new LinkedHashMap<>();
        Map<String, Object> jsonMap = new LinkedHashMap<>();
        if (list != null && list.size() > 0) {
            for (Request request : list) {
                String name = request.getName();
                String paramType = request.getParamType();
                Object value = getValue(request.getType(), request.getModelAttr());
                switch (paramType) {
                    case "header":
                        headerMap.put(name, value);
                        break;
                    case "query":
                        queryMap.put(name, value);
                        break;
                    case "path":
                        queryMap.put(name, value);
                        break;
                    case "body":
                        //TODO ??????content-type??????????????????????????????????????????json
                        jsonMap.put(name, value);
                        break;
                    default:
                        break;

                }
            }
        }
        String res = "";
        if (!queryMap.isEmpty()) {
            res += getUrlParamsByMap(queryMap);
        }
        if (!headerMap.isEmpty()) {
            res += " " + getHeaderByMap(headerMap);
        }
        if (!jsonMap.isEmpty()) {
            if (jsonMap.size() == 1) {
                for (Entry<String, Object> entry : jsonMap.entrySet()) {
                    res += " '" + JsonUtils.writeJsonStr(entry.getValue()) + "'";
                }
            } else {
                res += " '" + JsonUtils.writeJsonStr(jsonMap) + "'";
            }
        }
        return res;
    }

    /**
     * ??????????????????????????????
     *
     * @param type      ??????
     * @param modelAttr ???????????????
     * @return
     */
    private Object getValue(String type, ModelAttr modelAttr) {
        int pos;
        if ((pos = type.indexOf(":")) != -1) {
            type = type.substring(0, pos);
        }
        switch (type) {
            case "string":
                return "string";
            case "string(date-time)":
                return "2020/01/01 00:00:00";
            case "integer":
            case "integer(int64)":
            case "integer(int32)":
                return 0;
            case "number":
                return 0.0;
            case "boolean":
                return true;
            case "file":
                return "(binary)";
            case "array":
                List list = new ArrayList();
                Map<String, Object> map = new LinkedHashMap<>();
                if (modelAttr != null && !CollectionUtils.isEmpty(modelAttr.getProperties())) {
                    for (ModelAttr subModelAttr : modelAttr.getProperties()) {
                        map.put(subModelAttr.getName(), getValue(subModelAttr.getType(), subModelAttr));
                    }
                }
                list.add(map);
                return list;
            case "object":
                map = new LinkedHashMap<>();
                if (modelAttr != null && !CollectionUtils.isEmpty(modelAttr.getProperties())) {
                    for (ModelAttr subModelAttr : modelAttr.getProperties()) {
                        map.put(subModelAttr.getName(), getValue(subModelAttr.getType(), subModelAttr));
                    }
                }
                return map;
            default:
                return null;
        }
    }

    /**
     * ???map?????????url
     */
    public static String getUrlParamsByMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append(entry.getKey() + "=" + entry.getValue());
            sb.append("&");
        }
        String s = sb.toString();
        if (s.endsWith("&")) {
            s = StringUtils.substringBeforeLast(s, "&");
        }
        return s;
    }

    /**
     * ???map?????????header
     */
    public static String getHeaderByMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append("--header '");
            sb.append(entry.getKey() + ":" + entry.getValue());
            sb.append("'");
        }
        return sb.toString();
    }
}