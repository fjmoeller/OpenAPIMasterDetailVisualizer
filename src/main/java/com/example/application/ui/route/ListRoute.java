package com.example.application.ui.route;

import com.example.application.ui.MainLayout;
import com.example.application.ui.presenter.TagPresenter;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.*;

@PageTitle("List")
@Route(value="list/:tag", layout = MainLayout.class)
@PreserveOnRefresh
public class ListRoute extends Div implements BeforeEnterObserver,BeforeLeaveObserver{

    private final TagPresenter presenter;

    private Component activeView;

    public ListRoute(TagPresenter presenter) {
        this.presenter = presenter;
        setSizeFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent){
        String tag = beforeEnterEvent.getRouteParameters().get("tag").get().replace("%20"," "); //replace spaces
        System.out.println("Route tag:"+ tag);

        activeView = presenter.getListPresenter(tag).getView();
        add(activeView);
        //TODO choose correct presenter to get View from
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent beforeLeaveEvent){
        remove(activeView);
        activeView = null; //TODO immer neu erstellen oder nur einmal und dann abspeichern und abrufen über ne Map ?
    }
}