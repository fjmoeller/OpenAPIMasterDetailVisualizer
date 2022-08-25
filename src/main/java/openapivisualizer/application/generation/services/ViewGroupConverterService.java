package openapivisualizer.application.generation.services;

import lombok.extern.slf4j.Slf4j;
import openapivisualizer.application.generation.structuremodel.*;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ViewGroupConverterService {

    public static ViewGroupLV createViewGroupLV(ViewGroup viewGroup) {
        log.debug("List LV primary paths for {}: {}", viewGroup.getTagName(), viewGroup.getPrimaryPaths());

        //creates internal MDVs
        Map<String, ViewGroupMDV> internalMDVStrucViewGroups = new HashMap<>();
        viewGroup.getPrimaryPaths().forEach(primaryPath -> {
            ViewGroup viewGroupInternalMD = new ViewGroup(viewGroup.getTagName(), List.of(primaryPath)
                    , viewGroup.getSecondaryPaths().entrySet().stream().filter(e -> e.getKey().equals(primaryPath)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    , new LinkedMultiValueMap<>()
                    , viewGroup.getStrucSchemaMap()
                    , viewGroup.getStrucPathMap().entrySet().stream().filter(entry -> entry.getKey().startsWith(primaryPath)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            internalMDVStrucViewGroups.put(primaryPath, createStrucViewGroupMDV(viewGroupInternalMD));
        });

        return new ViewGroupLV(viewGroup.getTagName(), viewGroup.getStrucSchemaMap(), //TODO remove secondary paths
                viewGroup.getStrucPathMap().entrySet().stream()
                        .filter(entry -> !viewGroup.getSecondaryPaths().containsValue(entry.getKey()) //TODO mal strucviewgroup angucken ob alles richtig zugeordnet ist
                                && !viewGroup.getPrimaryPaths().contains(entry.getKey())
                                && viewGroup.getInternalPrimaryPaths().values().stream().noneMatch(values -> values.contains(entry.getKey())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                internalMDVStrucViewGroups);
    }

    public static ViewGroupMDV createStrucViewGroupMDV(ViewGroup viewGroup) {
        //if (!isMDVStructure(strucViewGroup)) return null; //TODO glaube kann raus, würde sonst listview dinger

        ViewGroupMDV primaryStrucViewGroup = createSingleStrucViewGroupMDV(viewGroup.getTagName(),
                viewGroup.getPrimaryPaths().get(0),
                viewGroup.getSecondaryPaths().get(viewGroup.getPrimaryPaths().get(0)),
                viewGroup.getStrucSchemaMap(),
                viewGroup.getStrucPathMap());

        //there should only be 1 entry (the primary path)
        if (viewGroup.getInternalPrimaryPaths().containsKey(viewGroup.getPrimaryPaths().get(0)))
            viewGroup.getInternalPrimaryPaths().get(viewGroup.getPrimaryPaths().get(0)).forEach(internalPrimaryPath -> {
                ViewGroupMDV internalViewGroupMDV = createSingleStrucViewGroupMDV(viewGroup.getTagName(),
                        internalPrimaryPath, null, viewGroup.getStrucSchemaMap(),
                        viewGroup.getStrucPathMap());
                primaryStrucViewGroup.getInternalMDVs().put(internalPrimaryPath, internalViewGroupMDV);
            });

        return primaryStrucViewGroup;
    }

    public static ViewGroupMDV createSingleStrucViewGroupMDV(String tagName, String primaryPath,
                                                             String secondaryPath,
                                                             Map<String, StrucSchema> groupStrucSchemaMap,
                                                             Map<String, Map<HttpMethod, StrucPath>> groupStrucPathMap) {

        Map<HttpMethod, StrucPath> strucPathMap = new HashMap<>();
        Map<HttpMethod, StrucSchema> strucSchemaMap = new HashMap<>();
        //There should only be one primary path
        StrucPath primaryGetPath = groupStrucPathMap.get(primaryPath).get(HttpMethod.GET);

        //Add the paged Schema (if it exists)
        StrucSchema pagedStrucSchema = null;
        if (primaryGetPath == null)
            log.info("debug me");
        if (SchemaService.isPagedSchema(primaryGetPath.getResponseStrucSchema()))
            pagedStrucSchema = groupStrucSchemaMap.get(SchemaService.getPagedSchemaName(primaryGetPath.getResponseStrucSchema()));

        //GET (needs to exist)
        strucPathMap.put(HttpMethod.GET, primaryGetPath);
        strucSchemaMap.put(HttpMethod.GET, primaryGetPath.getResponseStrucSchema());

        //POST (only looks as input, not response)
        if (groupStrucPathMap.get(primaryGetPath.getPath()).containsKey(HttpMethod.POST)) { //If a POST exists for this path based on the Rest vorgaben
            StrucPath postPath = groupStrucPathMap.get(primaryGetPath.getPath()).get(HttpMethod.POST);

            strucPathMap.put(HttpMethod.POST, postPath);
            strucSchemaMap.put(HttpMethod.POST, postPath.getRequestStrucSchema());
        }

        //PUT (only looks as input, not response) IS FOR SECONDARYPATH //TODO can also be primary path -> prob has reuqired query param
        if (groupStrucPathMap.get(secondaryPath) != null &&
                groupStrucPathMap.get(secondaryPath).containsKey(HttpMethod.PUT)) { //If a Put exists for this path based on the Rest vorgaben
            StrucPath putPath = groupStrucPathMap.get(secondaryPath).get(HttpMethod.PUT);

            strucPathMap.put(HttpMethod.PUT, putPath);
            strucSchemaMap.put(HttpMethod.PUT, putPath.getRequestStrucSchema());
        } else if (groupStrucPathMap.containsKey(primaryGetPath) &&
                groupStrucPathMap.get(primaryGetPath).containsKey(HttpMethod.PUT)) { //If a Put exists for this path based on the Rest vorgaben
            StrucPath putPath = groupStrucPathMap.get(primaryGetPath).get(HttpMethod.PUT);

            strucPathMap.put(HttpMethod.PUT, putPath);
            strucSchemaMap.put(HttpMethod.PUT, putPath.getRequestStrucSchema());
        }

        //DELETE (only looks as input, not response) IS FOR SECONDARYPATH  //TODO can also be primary path -> prob has reuqired query param
        if (groupStrucPathMap.get(secondaryPath) != null &&
                groupStrucPathMap.get(secondaryPath).containsKey(HttpMethod.DELETE)) { //If a DELETE exists for this path based on the Rest vorgaben
            StrucPath deletePath = groupStrucPathMap.get(secondaryPath).get(HttpMethod.DELETE);
            strucPathMap.put(HttpMethod.DELETE, deletePath);
        } else if (groupStrucPathMap.containsKey(primaryGetPath) &&
                groupStrucPathMap.get(primaryGetPath).containsKey(HttpMethod.DELETE)) { //If a DELETE exists for this path based on the Rest vorgaben
            StrucPath deletePath = groupStrucPathMap.get(primaryGetPath).get(HttpMethod.DELETE);
            strucPathMap.put(HttpMethod.DELETE, deletePath);
        }

        return new ViewGroupMDV(tagName, pagedStrucSchema, strucPathMap, secondaryPath, strucSchemaMap);
    }

    public static boolean isMDVStructure(ViewGroup viewGroup) {
        return viewGroup.getPrimaryPaths().size() == 1
                && viewGroup.getStrucPathMap().keySet().stream()
                .allMatch(path -> path.startsWith(viewGroup.getPrimaryPaths().get(0)))
                && viewGroup.getStrucPathMap().get(viewGroup.getPrimaryPaths().get(0)).containsKey(HttpMethod.GET)
                && viewGroup.getStrucPathMap().get(viewGroup.getPrimaryPaths().get(0)).get(HttpMethod.GET)
                .getResponseStrucSchema() != null
                && viewGroup.getStrucPathMap().keySet().stream() //All paths
                .allMatch(path -> viewGroup.getPrimaryPaths().get(0).equals(path)
                        || viewGroup.getSecondaryPaths().containsValue(path)
                        || (viewGroup.getInternalPrimaryPaths().containsKey(viewGroup.getPrimaryPaths().get(0)) &&
                        viewGroup.getInternalPrimaryPaths().get(viewGroup.getPrimaryPaths().get(0)).contains(path)));
    }
}