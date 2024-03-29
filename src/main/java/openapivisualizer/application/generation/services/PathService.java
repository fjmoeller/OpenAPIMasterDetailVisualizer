package openapivisualizer.application.generation.services;

import com.vaadin.flow.spring.annotation.UIScope;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import lombok.extern.slf4j.Slf4j;
import openapivisualizer.application.generation.structuremodel.DataPropertyType;
import openapivisualizer.application.generation.structuremodel.StrucPath;
import openapivisualizer.application.generation.structuremodel.StrucSchema;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@UIScope
public class PathService {

    private final SchemaService schemaService;

    public PathService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    public List<String> getApiPaths(Map<String, Map<HttpMethod, StrucPath>> pathsForTag) {
        List<String> apiPaths = new ArrayList<>();

        pathsForTag.forEach((key, value) -> {
            //wenn kein {} drinne kanns n api path sein
            if (!key.contains("{") && value.containsKey(HttpMethod.GET)) {
                if (value.get(HttpMethod.GET).getResponseStrucSchema() != null) {
                    apiPaths.add(value.get(HttpMethod.GET).getPath());
                    log.debug("Detected apiPath: " + value.get(HttpMethod.GET).getPath());
                }
            }
        });
        //Master Detail View (BSP Artifacts)
        return apiPaths;
    }

    public Map<String, String> getUriPaths(Map<String, Map<HttpMethod, StrucPath>> pathsForTag, List<String> apiPaths) {
        //apiPath: /artifact/ -> uriPath: /artifact/{id}/

        //uriPath -> apiPath
        Map<String, String> uriPaths = new HashMap<>();

        apiPaths.forEach(apiPath -> {
            String uriRegex = apiPath;
            if (!apiPath.endsWith("/")) {
                uriRegex += "/";
            }
            uriRegex += "{";
            String finalUriRegex = uriRegex;
            pathsForTag.keySet().forEach(path -> {
                if (path.startsWith(finalUriRegex) && (path.split("}").length == 1 || path.split("}/").length == 1)
                        && (path.endsWith("}") || path.endsWith("}/"))) {
                    uriPaths.put(apiPath, path);
                    log.debug("URIPath found {} for APIPath {}", path, apiPath);
                }
            });
        });
        return uriPaths;
    }

    public MultiValueMap<String, String> getRelationPaths(Map<String, Map<HttpMethod, StrucPath>> pathsForTag, List<String> apiPaths) {
        MultiValueMap<String, String> relationPath = new LinkedMultiValueMap<>();

        apiPaths.forEach(apiPath -> pathsForTag.keySet().forEach(path -> {
            if (path.matches(apiPath + "/\\{(\\w)+}/(\\w)+/?") && pathsForTag.get(path).containsKey(HttpMethod.GET)) {
                relationPath.add(apiPath, path);
                //log.info("Relation Path found {} for APIPath {}", path, apiPath);
            }
        }));
        return relationPath;
    }

    public StrucPath operationToStrucPath(String path, HttpMethod httpMethod, Operation operation, Map<String, StrucSchema> strucSchemaMap) {
        StrucPath strucPath = new StrucPath();
        strucPath.setPath(path);
        strucPath.setHttpMethod(httpMethod);
        log.debug("Converting Operation to Path for Path {} and HttpMethod {}", path, httpMethod.toString());

        //Query & Path params
        if (operation.getParameters() != null && !operation.getParameters().isEmpty()) {
            operation.getParameters().forEach(parameter -> {
                if (parameter.getIn().equals("query"))
                    strucPath.getQueryParams().add(new StrucPath.StrucParameter(parameter.getName(),
                            DataPropertyType.fromString(parameter.getSchema().getType()), parameter.getSchema().getFormat(), parameter.getRequired()));
                else if (parameter.getIn().equals("path"))
                    strucPath.getPathParams().add(new StrucPath.StrucParameter(parameter.getName(),
                            DataPropertyType.fromString(parameter.getSchema().getType()), parameter.getSchema().getFormat(), parameter.getRequired()));
            });
        }

        if (HttpMethod.POST.equals(httpMethod) || HttpMethod.PUT.equals(httpMethod)) { //TODO was in get oder delete ?, was tun wenn kein ref schema
            if (operation.getRequestBody() != null) { //example: /api/apps/{id}/actions and HttpMethod PUT-
                if (operation.getRequestBody().getContent().containsKey("application/json") && operation.getRequestBody().getContent().get("application/json").getSchema() != null) { //Has actual Schema (Example /artifacts/id/data)
                    //TODO application/octet-stream (data PUT)
                    String externalSchemaPath = operation.getRequestBody().getContent().get("application/json").getSchema().get$ref(); //TODO was wenn kein href & für andere arten von content
                    if (externalSchemaPath != null) {
                        StrucSchema strucSchema = strucSchemaMap.get(schemaService.stripSchemaRefPath(externalSchemaPath));
                        strucPath.setRequestStrucSchema(strucSchema);
                    } else {
                        StrucSchema strucSchema = schemaService.mapSchemaToStrucSchema("noName", operation.getRequestBody().getContent().get("application/json").getSchema());
                        strucPath.setRequestStrucSchema(strucSchema);
                    }
                }else if (operation.getRequestBody().getContent().containsKey("application/octet-stream") && operation.getRequestBody().getContent().get("application/octet-stream").getSchema() != null) { //Has actual Schema (Example /artifacts/id/data)
                    //TODO application/octet-stream (data PUT)
                    String externalSchemaPath = operation.getRequestBody().getContent().get("application/octet-stream").getSchema().get$ref(); //TODO was wenn kein href & für andere arten von content
                    if (externalSchemaPath != null) {
                        StrucSchema strucSchema = strucSchemaMap.get(schemaService.stripSchemaRefPath(externalSchemaPath));
                        strucPath.setRequestStrucSchema(strucSchema);
                    } else {
                        StrucSchema strucSchema = schemaService.mapSchemaToStrucSchema("noName", operation.getRequestBody().getContent().get("application/octet-stream").getSchema());
                        strucPath.setRequestStrucSchema(strucSchema);
                    }
                }
            }
        } else if (HttpMethod.GET.equals(httpMethod)) {
            if (operation.getResponses().containsKey("200") && operation.getResponses().get("200").getContent() != null) {
                if (operation.getResponses().get("200").getContent().containsKey("*/*"))
                    setResponseSchema(strucPath, operation, "200", "*/*", strucSchemaMap);
                else if (operation.getResponses().get("200").getContent().containsKey("application/json"))
                    setResponseSchema(strucPath, operation, "200", "application/json", strucSchemaMap);
                else if (operation.getResponses().get("200").getContent().containsKey("application/ld+json"))
                    setResponseSchema(strucPath, operation, "200", "application/ld+json", strucSchemaMap);
                else if (operation.getResponses().get("200").getContent().containsKey("application/hal+json"))
                    setResponseSchema(strucPath, operation, "200", "application/hal+json", strucSchemaMap);
            }
        }
        else {
            log.debug("The current path can only respond with the following http codes: {}", operation.getResponses().keySet());
        }
        return strucPath;
    }

    /**
     * This function finds the schema of the content in the response and puts it into the StrucPath object
     *
     * @param strucPath  the Strucpath to set the ResponseBodySchema in
     * @param operation  the Operation that holds the inital Schema
     * @param returnCode the http returnCode to be searched
     * @param returnType the returnType to be searched
     */
    public void setResponseSchema(StrucPath strucPath, Operation operation, String returnCode, String returnType, Map<String, StrucSchema> strucSchemaMap) {
        Content content = operation.getResponses().get(returnCode).getContent();

        if (content.containsKey(returnType)) {
            if (content.get(returnType).getSchema().get$ref() != null) {
                String externalSchemaPath = content.get(returnType).getSchema().get$ref();
                strucPath.setResponseStrucSchema(strucSchemaMap.get(schemaService.stripSchemaRefPath(externalSchemaPath)));
            } else if (content.get(returnType).getSchema().getType() != null
                    && content.get(returnType).getSchema().getType().equals("array")
                    && content.get(returnType).getSchema().getItems().get$ref() != null) {
                String externalSchemaPath = content.get(returnType).getSchema().getItems().get$ref();
                strucPath.setResponseStrucSchema(strucSchemaMap.get(schemaService.stripSchemaRefPath(externalSchemaPath)));
            }else if (content.get(returnType).getSchema().getType() != null
                    && content.get(returnType).getSchema().getType().equals("array")
                    && content.get(returnType).getSchema().getItems().get$ref() == null) {
                StrucSchema strucSchema = schemaService.mapSchemaToStrucSchema("Array", operation.getResponses().get(returnCode).getContent().get(returnType).getSchema().getItems());
                strucPath.setResponseStrucSchema(strucSchema);
            }  else {
                StrucSchema strucSchema = schemaService.mapSchemaToStrucSchema("Array", operation.getResponses().get(returnCode).getContent().get(returnType).getSchema());
                strucPath.setResponseStrucSchema(strucSchema);
            }
        }
    }

    public Map<String, Map<HttpMethod, StrucPath>> getPathsNoTag(Paths paths, Map<String, StrucSchema> strucSchemaMap) {
        Map<String, Map<HttpMethod, StrucPath>> pathOperationMap = new HashMap<>(); //Path -> List
        paths.keySet().forEach(path -> { //TODO all paths without recognised tags into one sonstiges tag
            Map<HttpMethod, StrucPath> methodOperationMap = new HashMap<>();
            if (paths.get(path).getGet() != null
                    && (paths.get(path).getGet().getTags() == null
                    || paths.get(path).getGet().getTags().isEmpty())) {
                methodOperationMap.put(HttpMethod.GET,
                        operationToStrucPath(path, HttpMethod.GET, paths.get(path).getGet(), strucSchemaMap));
            }
            if (paths.get(path).getPost() != null
                    && (paths.get(path).getPost().getTags() == null
                    || paths.get(path).getPost().getTags().isEmpty())) {
                methodOperationMap.put(HttpMethod.POST,
                        operationToStrucPath(path, HttpMethod.POST, paths.get(path).getPost(), strucSchemaMap));
            }
            if (paths.get(path).getPut() != null
                    && (paths.get(path).getPut().getTags() == null
                    || paths.get(path).getPut().getTags().isEmpty())) {
                methodOperationMap.put(HttpMethod.PUT,
                        operationToStrucPath(path, HttpMethod.PUT, paths.get(path).getPut(), strucSchemaMap));
            }
            if (paths.get(path).getDelete() != null
                    && (paths.get(path).getDelete().getTags() == null
                    || paths.get(path).getDelete().getTags().isEmpty())) {
                methodOperationMap.put(HttpMethod.DELETE,
                        operationToStrucPath(path, HttpMethod.DELETE, paths.get(path).getDelete(), strucSchemaMap));
            }
            if (methodOperationMap.size() > 0)
                pathOperationMap.put(path, methodOperationMap);
        });
        return pathOperationMap;
    }

    public Map<String, Map<HttpMethod, StrucPath>> getPathsForTag(String tagName, Paths paths, Map<String, StrucSchema> strucSchemaMap) {
        Map<String, Map<HttpMethod, StrucPath>> pathOperationMap = new HashMap<>(); //Path -> List
        paths.keySet().forEach(path -> { //TODO all paths without recognised tags into one sonstiges tag
            Map<HttpMethod, StrucPath> methodOperationMap = new HashMap<>();
            if (paths.get(path).getGet() != null
                    && paths.get(path).getGet().getTags() != null
                    && paths.get(path).getGet().getTags().contains(tagName)) {
                methodOperationMap.put(HttpMethod.GET,
                        operationToStrucPath(path, HttpMethod.GET, paths.get(path).getGet(), strucSchemaMap));
            }
            if (paths.get(path).getPost() != null
                    && paths.get(path).getPost().getTags() != null
                    && paths.get(path).getPost().getTags().contains(tagName)) {
                methodOperationMap.put(HttpMethod.POST,
                        operationToStrucPath(path, HttpMethod.POST, paths.get(path).getPost(), strucSchemaMap));
            }
            if (paths.get(path).getPut() != null
                    && paths.get(path).getPut().getTags() != null
                    && paths.get(path).getPut().getTags().contains(tagName)) {
                methodOperationMap.put(HttpMethod.PUT,
                        operationToStrucPath(path, HttpMethod.PUT, paths.get(path).getPut(), strucSchemaMap));
            }
            if (paths.get(path).getDelete() != null
                    && paths.get(path).getDelete().getTags() != null
                    && paths.get(path).getDelete().getTags().contains(tagName)) {
                methodOperationMap.put(HttpMethod.DELETE,
                        operationToStrucPath(path, HttpMethod.DELETE, paths.get(path).getDelete(), strucSchemaMap));
            }
            if (methodOperationMap.size() > 0)
                pathOperationMap.put(path, methodOperationMap);
        });
        return pathOperationMap;
    }
}
