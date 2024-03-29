package openapivisualizer.application.ui.components;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.grid.dnd.GridDropLocation;
import com.vaadin.flow.component.grid.dnd.GridDropMode;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.Query;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import openapivisualizer.application.generation.structuremodel.DataPropertyType;
import openapivisualizer.application.generation.structuremodel.StrucPath;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.*;
import java.util.stream.Collectors;

public class SettingsDialog extends Dialog {

    public interface SettingsActionListener {
        void setQueryParams(MultiValueMap<String, String> queryParams);

        void setGridColumnSettings(List<ColumnGridElement> columns);

        void setWrappedSchemaPath(String pathToSchema);

        void refreshFromSettings();
    }

    private final SettingsActionListener actionListener;

    private final List<InputValueComponent> queryFieldComponents = new ArrayList<>();
    private final Grid<ColumnGridElement> grid = new Grid<>(ColumnGridElement.class);
    private final TextField wrappedSchemaTextfield = new TextField("Path of wrapped data");

    public SettingsDialog(SettingsActionListener actionListener) {
        this.actionListener = actionListener;
        setWidth(50, Unit.PERCENTAGE);
    }

    public void open(StrucPath strucPath, List<ColumnGridElement> columnSorting, String currentPath) {
        if (!strucPath.getQueryParams().isEmpty())
            createQueryParamFields(strucPath);

        createWrappedSchemaSettings(currentPath);

        createGridSettings(columnSorting);

        Button closePostViewButton = new Button("Close");
        closePostViewButton.addClickListener(e -> this.close());

        Button postButton = new Button("Save");
        postButton.addClickListener(e -> this.save(currentPath));

        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.add(postButton, closePostViewButton);
        add(horizontalLayout);

        this.open();
    }

    private void createWrappedSchemaSettings(String currentPath) {
        Label currentPathLabel = new Label("Current path: " + currentPath);

        wrappedSchemaTextfield.setPlaceholder("path/path");
        if (currentPath != null)
            wrappedSchemaTextfield.setValue(currentPath);

        VerticalLayout content = new VerticalLayout();
        content.add(currentPathLabel, wrappedSchemaTextfield);
        Details details = new Details("Wrapped schema location", content);
        add(details);
    }

    private MultiValueMap<String, String> collectQueryParams() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        queryFieldComponents.forEach(component -> {
            if (!component.getComponent().isEmpty()) {
                params.add(component.getTitle(), component.getComponent().getValue().toString());
            }
        });
        return params;
    }

    private void save(String currentPath) {
        MultiValueMap<String, String> queryParams = collectQueryParams();

        if (areRequiredFieldsFilled())
            actionListener.setQueryParams(queryParams);

        actionListener.setGridColumnSettings(grid.getDataProvider().fetch(new Query<>())
                .collect(Collectors.toList()));

        if (!wrappedSchemaTextfield.getValue().equals(currentPath))
            actionListener.setWrappedSchemaPath(wrappedSchemaTextfield.getValue());

        actionListener.refreshFromSettings();
        this.close();
    }

    private boolean areRequiredFieldsFilled() {
        return queryFieldComponents.stream().allMatch(component -> !component.getComponent().isRequiredIndicatorVisible()
                || (component.getComponent().isRequiredIndicatorVisible() && !component.getComponent().isEmpty()));
    }

    private AbstractField createEditorComponent(DataPropertyType type, String title, boolean required) {
        AbstractField inputComponent = switch (type) {
            case INTEGER -> new IntegerField(title);
            case DOUBLE -> new NumberField(title);
            case BOOLEAN -> new Checkbox(title);
            case STRING -> new TextField(title);
            case OBJECT ->
                    new TextField(title);
            default -> new TextField(title);
        };

        inputComponent.setRequiredIndicatorVisible(required);

        queryFieldComponents.add(new InputValueComponent(title, inputComponent, type));
        return inputComponent;
    }

    private void createGridSettings(List<ColumnGridElement> columnSortation) {
        GridListDataView<ColumnGridElement> dataView = grid.setItems(columnSortation);
        grid.removeAllColumns();
        grid.addComponentColumn(columnGridElement -> {
            final var checkBox = new Checkbox();
            checkBox.setValue(columnGridElement.isVisible());
            checkBox.addClickListener(event -> columnGridElement.setVisible(checkBox.getValue()));
            return checkBox;
        }).setHeader("Visible");
        grid.addColumn(ColumnGridElement::getColumnName).setHeader("Column name");
        grid.setRowsDraggable(true);
        grid.setDropMode(GridDropMode.BETWEEN);

        final ColumnGridElement[] draggedItem = new ColumnGridElement[1];

        grid.addDragStartListener(
                e -> draggedItem[0] = e.getDraggedItems().get(0));

        grid.addDropListener(e -> {
            ColumnGridElement target = e.getDropTargetItem().orElse(null);
            GridDropLocation dropLocation = e.getDropLocation();

            boolean targetWasDroppedOntoItself = draggedItem[0]
                    .equals(target);

            if (target == null || targetWasDroppedOntoItself)
                return;

            dataView.removeItem(draggedItem[0]);

            if (dropLocation == GridDropLocation.BELOW) {
                dataView.addItemAfter(draggedItem[0], target);
            } else {
                dataView.addItemBefore(draggedItem[0], target);
            }
        });

        grid.addDragEndListener(e -> draggedItem[0] = null);

        Details details = new Details("Grid column sorting", grid);
        add(details);
    }

    private void createQueryParamFields(StrucPath strucPath) {
        VerticalLayout content = new VerticalLayout();

        strucPath.getQueryParams().forEach(queryParam -> {
                    AbstractField abstractField = createEditorComponent(queryParam.getType(), queryParam.getName(), queryParam.isRequired());
                    content.add(abstractField);
                }
        );
        Details details = new Details("Query Parameter", content);
        add(details);
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class ColumnGridElement {
        private String columnName;
        private boolean visible;
    }
}
