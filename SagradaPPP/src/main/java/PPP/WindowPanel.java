package PPP;


import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;


public class WindowPanel {

    private String panelName;
    private int favorTokens;
    private int cardID;
    private ArrayList<Cell> cells;


    public WindowPanel(WindowPanel windowPanel){
        this.panelName = windowPanel.getPanelName();
        this.favorTokens = windowPanel.getFavorTokens();
        this.cardID = windowPanel.getCardID();
        this.cells = windowPanel.getCells();
    }

    //cardNumber from 1 to 12
    //side 1 for the front
    public WindowPanel(int cardNumber, int side) throws FileNotFoundException {

        int fileIndex = cardNumber * 2 - side;
        JSONTokener tokener = new JSONTokener(new FileReader("templates/panel" + fileIndex + ".json"));
        JSONObject jsonObject = new JSONObject(tokener);
        JSONArray jsonArrayCells = jsonObject.getJSONArray("cells");
        cells = new ArrayList<>();

        //getting card name and favor token from JSON
        cardID = jsonObject.getInt("cardID");
        favorTokens = jsonObject.getInt("favorTokens");
        panelName = jsonObject.getString("name");


        String color;
        String value;

        for (Object jsonArrayCell : jsonArrayCells) {
            JSONObject jsonCell = (JSONObject) jsonArrayCell;

            color = jsonCell.get("color").toString();
            value = jsonCell.get("value").toString();

            if (!color.equals(StaticValues.NULL_JSON_VALUE)) {
                //colored cell
                cells.add(new Cell(Color.getColor(color)));

            } else if (!value.equals(StaticValues.NULL_JSON_VALUE)) {
                //value cell
                cells.add(new Cell(Integer.parseInt(value)));
            } else {
                //blank cell
                cells.add(new Cell());
            }
        }
    }

    public Cell getCellWithPosition(int row, int col) {
        if ((row < 0 || row > StaticValues.PATTERN_ROW) || (col < 0 || col > StaticValues.PATTERN_COL)) {
            //access denied to wrong cells
            return null;
        }
        return new Cell(cells.get(row * StaticValues.PATTERN_COL + col));
    }

    public Cell getCellWithIndex(int i) {
        if (i < 0 || i > cells.size()) return null;
        return new Cell(cells.get(i));
    }

    public String getPanelName() {
        return panelName;
    }

    public void setPanelName(String panelName) {
        this.panelName = panelName;
    }

    public int getFavorTokens() {
        return favorTokens;
    }

    public void setFavorTokens(int favorTokens) {
        this.favorTokens = favorTokens;
    }

    public int getCardID() {
        return cardID;
    }

    public void setCardID(int cardID) {
        this.cardID = cardID;
    }

    public ArrayList<Cell> getCells(){
        return new ArrayList<Cell>(cells);
    }
}


