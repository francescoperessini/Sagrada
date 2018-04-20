package com.sagrada.ppp;

import java.util.ArrayList;
import java.util.Random;

public class DiceBag {

    private ArrayList<Dice> bag;

    public DiceBag() {
        bag = new ArrayList<>();
        for (Color color: Color.values()) {
            for (int i = 0; i < StaticValues.NUMBER_OF_DICES_PER_COLOR; i++) {
                bag.add(new Dice(color));
            }
        }
    }

    public int size(){
        return bag.size();
    }

    public Dice getDice(int i){
        return bag.get(i);
    }

    public Dice getRandomDice() {
        return bag.get(new Random().nextInt(90));
    }

    public ArrayList<Dice> getDiceBag() {
        return new ArrayList<>(bag);
    }

    //Remove the first occurrence from the original bag and return a copy of the new bag
    public ArrayList<Dice> removeDices(ArrayList<Dice> pulledDices) {
        for (Dice dice : pulledDices) {
            bag.remove(dice);
        }
        return new ArrayList<>(bag);
    }

    public int numberOfValue (final int n) {
        return (int) bag.stream().filter(x -> x.getValue() == n).count();
    }

    public int numberOfColor (Color color){
        return (int) bag.stream().filter(x -> x.getColor() == color).count();
    }

    public int numberOf (int n, Color color) {
        return (int) bag.stream().filter(x -> x.getValue() == n && x.getColor() == color).count();
    }

}