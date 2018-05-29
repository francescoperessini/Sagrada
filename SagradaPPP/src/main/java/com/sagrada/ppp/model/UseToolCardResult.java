package com.sagrada.ppp.model;

import java.io.Serializable;
import java.util.ArrayList;

public class UseToolCardResult implements Serializable {

    public boolean result;
    public ArrayList<Dice> draftpool;
    public RoundTrack roundTrack;
    public ArrayList<Player> players;

    public UseToolCardResult(boolean result, ArrayList<Dice> draftpool, RoundTrack roundTrack,ArrayList<Player> players ) {
        this.result = result;
        this.draftpool = draftpool;
        this.roundTrack = roundTrack;
        this.players = players;
    }
}
