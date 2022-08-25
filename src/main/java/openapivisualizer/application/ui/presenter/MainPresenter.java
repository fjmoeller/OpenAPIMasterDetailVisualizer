package openapivisualizer.application.ui.presenter;
//
//import com.example.application.data.services.Deprecated.StructureProvider;
//import com.example.application.data.structureModel.OpenApi;

import openapivisualizer.application.generation.services.StructureProviderService;
import openapivisualizer.application.rest.client.ClientDataService;
import openapivisualizer.application.ui.view.MainView;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;

@Controller
@UIScope
@Slf4j
public class MainPresenter implements MainView.ActionListener {

    private final ClientDataService clientDataService;
    private final TagPresenter tagPresenter;

    private final List<String> serverList = new ArrayList<>();

    private String currentServerURL = "/";
    private final MainView view  = new MainView(this, StructureProviderService.PARSE_OBJECT,currentServerURL,serverList);


    public MainPresenter(ClientDataService clientDataService, TagPresenter tagPresenter) {
        this.clientDataService = clientDataService;
        this.tagPresenter = tagPresenter;
    }

    public MainView getView() {
        view.setSelectedServer(currentServerURL);
        return view;
    }

    @Override
    public void openApiAction(String source) {
        tagPresenter.prepareStructure(source);
        serverList.clear();
        serverList.addAll(tagPresenter.getServers());
        view.setServers(serverList);
    }

    @Override
    public void serverSelected(String selectedServerURL) {
        clientDataService.setServerUrl(selectedServerURL);
        currentServerURL = selectedServerURL;
        log.info("New Server selected: {}",selectedServerURL);
    }

    @Override
    public void addServerToSelection(String server) {
        serverList.add(server);
        view.setServers(serverList);
        view.setSelectedServer(server);
    }


}