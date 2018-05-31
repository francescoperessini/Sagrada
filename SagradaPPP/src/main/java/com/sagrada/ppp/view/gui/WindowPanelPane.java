package com.sagrada.ppp.view.gui;

import com.sagrada.ppp.model.Cell;
import com.sagrada.ppp.model.WindowPanel;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;



import static com.sagrada.ppp.utils.StaticValues.*;

public class WindowPanelPane extends GridPane implements EventHandler<MouseEvent> {
    private WindowPanel panel;
    private Label name,tokens;
    private WindowPanelEventBus eventBus;
    private double width,height;


    public WindowPanelPane(WindowPanel panel, double height, double width) {
        this.panel = panel;

        name = new Label();
        tokens = new Label();
        this.width = width;
        this.height = height;

        this.setMinSize(width/2,height/2);
        this.setPadding(new Insets(10));
        this.setHgap(5);
        this.setVgap(5);
        this.setAlignment(Pos.CENTER);
        this.setBackground(new Background(
                new BackgroundFill(Color.BLACK,new CornerRadii(10), new Insets(0))
        ));
        this.autosize();

        draw();


        //Adding labels on the bottom of the grid view
        name.setStyle("-fx-text-fill: white;");
        tokens.setStyle("-fx-text-fill: white;");
        GridPane.setHalignment(tokens,HPos.CENTER);
        GridPane.setHalignment(name,HPos.CENTER);
        this.add(name,1,4,3,1);
        this.add(tokens,4,4,1,1);


    }

    void draw(){
        double cellHeight = height/4;
        double cellWidth = width/4;
        int col = 0;
        int row = 0;
        name.setText(panel.getPanelName());
        tokens.setText("Tokens:" + panel.getFavorTokens());
        for (Cell c:panel.getCells()) {
            CellPane cell = new CellPane(row,col);
            cell.setPrefSize(cellWidth,cellHeight);

            if(c.hasColorRestriction()){
                cell.setBackground(
                        new Background(
                                new BackgroundImage(
                                        new Image(getAssetUri(c.getColor()),cellWidth,cellHeight,true,true),
                                        BackgroundRepeat.NO_REPEAT,
                                        BackgroundRepeat.NO_REPEAT,
                                        BackgroundPosition.CENTER,
                                        BackgroundSize.DEFAULT)));
            }else if(c.hasValueRestriction()){
                cell.setBackground(
                        new Background(
                                new BackgroundImage(
                                        new Image(getAssetUri(c.getValue()),cellWidth,cellHeight,true,true),
                                        BackgroundRepeat.NO_REPEAT,
                                        BackgroundRepeat.NO_REPEAT,
                                        BackgroundPosition.CENTER,
                                        BackgroundSize.DEFAULT)));
            }else {
                cell.setBackground(
                        new Background(
                                new BackgroundImage(
                                        new Image(FILE_URI_PREFIX + BLANK_CELL_ASSET,cellWidth,cellHeight,true,false),
                                        BackgroundRepeat.NO_REPEAT,
                                        BackgroundRepeat.NO_REPEAT,
                                        BackgroundPosition.CENTER,
                                        BackgroundSize.DEFAULT)));


            }
            if(c.hasDiceOn()){
                DiceButton diceButton = new DiceButton(c.getDiceOn(),cellWidth*.80,cellHeight*.80);
                diceButton.setMouseTransparent(true);
                diceButton.setMinSize(50,50);
                cell.setCenter(diceButton);


            }


            cell.addEventHandler(MouseEvent.MOUSE_CLICKED, this);
            this.add(cell,col,row);
            if(col < PATTERN_COL-1){
                col++;
            }else {
                col = 0;
                row++;
            }
        }
    }

    public void setPanel(WindowPanel panel) {
        this.panel = panel;
        draw();
    }
    public void setObserver(WindowPanelEventBus windowPanelEventBus){
        this.eventBus = windowPanelEventBus;
    }

    /**
     * @implNote handling mouse event on a cell
     */
    @Override
    public void handle(MouseEvent event) {
        CellPane cell = ((CellPane) event.getSource());
        System.out.println("col: "+cell.col + " row: " +cell.row);
        if(eventBus !=  null) {
            if(panel.getCell(cell.row,cell.col).hasDiceOn()){
                System.out.println("has dice on!");
                DiceButton diceButton = (DiceButton) cell.getChildren().get(0);
                if (diceButton != null)
                    eventBus.onDiceClicked(cell.row,cell.col);
            }else {
                eventBus.onCellClicked(cell.row, cell.col);
            }
        }
    }

    private class CellPane extends BorderPane{


        private int row,col;

        public CellPane(int row, int col) {
            this.row = row;
            this.col = col;
        }


    }
}

