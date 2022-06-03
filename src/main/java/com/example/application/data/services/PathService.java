package com.example.application.data.services;

import com.example.application.data.structureModel.StrucPath;
import com.example.application.data.structureModel.StrucSchema;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class PathService {

    public static List<StrucPath> getPrimaryViewPaths(Map<String, Map<HttpMethod, StrucPath>> pathsForTag, String tagName) {
        List<StrucPath> primaryPaths = new ArrayList<>();

        pathsForTag.forEach((key, value) -> {
            //wenn kein {} drinne kanns n primary path sein
            if (!key.contains("{") && value.containsKey(HttpMethod.GET)) {
                if (value.get(HttpMethod.GET).getExternalResponseBodySchemaName() != null) {
                    primaryPaths.add(value.get(HttpMethod.GET));
                    log.info("Detected primary path: " + value.get(HttpMethod.GET).getPath() + " for tag: " + tagName);
                }
            }
        });
        //Master Detail View (BSP Artifacts)
        return primaryPaths;
    }

    public static StrucPath operationToStrucPath(String path, HttpMethod httpMethod, Operation operation) {
        StrucPath strucPath = new StrucPath();
        strucPath.setPath(path);
        strucPath.setHttpMethod(httpMethod);
        log.info("Converting Operation to Path for Path {} and HttpMethod {}", path, httpMethod.toString());
        if (HttpMethod.POST.equals(httpMethod) || HttpMethod.PUT.equals(httpMethod)) { //TODO was in get oder delete ?, was tun wenn kein ref schema
            if (operation.getRequestBody() != null) { //example: /api/apps/{id}/actions and HttpMethod PUT-
                if (operation.getRequestBody().getContent().containsKey("application/json") && operation.getRequestBody().getContent().get("application/json").getSchema() != null) { //Has actual Schema (Example /artifacts/id/data)
                    //TODO application/octet-stream (data PUT)
                    String externalSchemaPath = operation.getRequestBody().getContent().get("application/json").getSchema().get$ref(); //TODO was wenn kein href & für andere arten von content
                    if (externalSchemaPath != null) {
                        strucPath.setExternalRequestBodySchemaName(SchemaService.stripSchemaRefPath(externalSchemaPath));
                        strucPath.setExternalRequestSchema(true);
                    } else {
                        StrucSchema strucSchema = SchemaService.mapSchemaToStrucSchema("noName", operation.getRequestBody().getContent().get("application/json").getSchema());
                        strucPath.setRequestStrucSchema(strucSchema);
                    }

                } else {
                    StrucSchema strucSchema = new StrucSchema();
                    strucSchema.setFreeSchema(true);
                }
            }
        }
        if (HttpMethod.GET.equals(httpMethod)) { //TODO was wenn mehrere rückgaben möglich
            if (operation.getResponses().containsKey("200") && operation.getResponses().get("200").getContent() != null) {
                if (operation.getResponses().get("200").getContent().containsKey("*/*"))
                    setResponseSchema(strucPath, operation, "200", "*/*");
                else if (operation.getResponses().get("200").getContent().containsKey("application/json"))
                    setResponseSchema(strucPath, operation, "200", "application/json");
            }
        } else {
            log.info("The current path can only respond with the following http codes: {}", operation.getResponses().keySet());
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
    public static void setResponseSchema(StrucPath strucPath, Operation operation, String returnCode, String returnType) {
        if (operation.getResponses().get(returnCode).getContent().containsKey(returnType)) {
            if (operation.getResponses().get(returnCode).getContent().get(returnType).getSchema().get$ref() != null) {
                String externalSchemaPath = operation.getResponses().get(returnCode).getContent().get(returnType).getSchema().get$ref();
                strucPath.setExternalResponseBodySchemaName(SchemaService.stripSchemaRefPath(externalSchemaPath));
                strucPath.setExternalResponseSchema(true);
            } else {
                StrucSchema strucSchema = SchemaService.mapSchemaToStrucSchema("noName", operation.getResponses().get(returnCode).getContent().get(returnType).getSchema());
                strucPath.setResponseStrucSchema(strucSchema);
            }
        }
    }

    public static Map<String, Map<HttpMethod, StrucPath>> getPathsForTag(String tagName, Paths paths) {
        Map<String, Map<HttpMethod, StrucPath>> pathOperationMap = new HashMap<>(); //Path -> List
        paths.keySet().forEach(key -> { //TODO all paths without recognised tags into one sonstiges tag
            Map<HttpMethod, StrucPath> methodOperationMap = new HashMap<>();
            if (paths.get(key).getGet() != null
                    && paths.get(key).getGet().getTags() != null
                    && paths.get(key).getGet().getTags().contains(tagName)) {
                methodOperationMap.put(HttpMethod.GET,
                        operationToStrucPath(key, HttpMethod.GET, paths.get(key).getGet()));
            }
            if (paths.get(key).getPost() != null
                    && paths.get(key).getPost().getTags() != null
                    && paths.get(key).getPost().getTags().contains(tagName)) {
                methodOperationMap.put(HttpMethod.POST,
                        operationToStrucPath(key, HttpMethod.POST, paths.get(key).getPost()));
            }
            if (paths.get(key).getPut() != null
                    && paths.get(key).getPut().getTags() != null
                    && paths.get(key).getPut().getTags().contains(tagName)) {
                methodOperationMap.put(HttpMethod.PUT,
                        operationToStrucPath(key, HttpMethod.PUT, paths.get(key).getPut()));
            }
            if (paths.get(key).getDelete() != null
                    && paths.get(key).getDelete().getTags() != null
                    && paths.get(key).getDelete().getTags().contains(tagName)) {
                methodOperationMap.put(HttpMethod.DELETE,
                        operationToStrucPath(key, HttpMethod.DELETE, paths.get(key).getDelete()));
            }
            if (methodOperationMap.size() > 0)
                pathOperationMap.put(key, methodOperationMap);
        });
        return pathOperationMap;
    }
}
