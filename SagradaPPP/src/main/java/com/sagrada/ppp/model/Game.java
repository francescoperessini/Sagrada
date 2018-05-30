package com.sagrada.ppp.model;

import com.sagrada.ppp.cards.publicobjectivecards.*;
import com.sagrada.ppp.cards.toolcards.*;
import com.sagrada.ppp.controller.RemoteController;
import com.sagrada.ppp.utils.StaticValues;
import javafx.util.Pair;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Game implements Serializable{
    private ArrayList<Player> players;
    private Player activePlayer;
    private DiceBag diceBag;
    private ArrayList<WindowPanel> panels;
    private ArrayList<Dice> draftPool;
    private RoundTrack roundTrack;
    private GameStatus gameStatus;
    private ArrayList<LobbyObserver> lobbyObservers;
    private LobbyTimer lobbyTimer;
    private long lobbyTimerStartTime;
    public volatile boolean waitingForPanelChoice;
    public volatile boolean panelChoiceTimerExpired;
    public Integer chosenPanelIndex;
    public ArrayList<GameObserver> gameObservers;
    private ArrayList<ToolCard> toolCards;
    private ArrayList<PublicObjectiveCard> publicObjectiveCards;
    private int turn;
    private volatile boolean dicePlaced;
    private volatile boolean usedToolCard;
    private volatile boolean isSpecialTurn;
    private volatile boolean endTurn;
    private volatile boolean turnTimeout;
    private MyTimerTask currentTimerTask;

    /*
    TODO: Add a method that given the username string returns the desired players
    TODO: Add overloading methods that take a Player as a parameter instead of a String
    */

    public Game(String username){
        diceBag = new DiceBag();
        players = new ArrayList<>();
        draftPool = new ArrayList<>();
        roundTrack = new RoundTrack(StaticValues.NUMBER_OF_TURNS);
        gameStatus = GameStatus.INIT;
        if(username != null) players.add(new Player(username));
        lobbyObservers = new ArrayList<>();
        waitingForPanelChoice = false;
        panelChoiceTimerExpired = false;
        chosenPanelIndex = null;
        gameObservers = new ArrayList<>();
        toolCards = new ArrayList<>();
        publicObjectiveCards = new ArrayList<>();
        chosenPanelIndex = -1;
        dicePlaced = false;
        usedToolCard = false;
        isSpecialTurn = false;
        endTurn = false;
        turnTimeout = false;
    }

    public void init(){
        gameStatus = GameStatus.ACTIVE;
        assignPrivateObjectiveColors();
        Collections.shuffle(players);
        turn = 1;
        HashMap<Integer, ArrayList<WindowPanel>> panels = extractPanels();
        for(int playerHashCode : panels.keySet()){
            chosenPanelIndex = -1;
            waitingForPanelChoice = true;
            panelChoiceTimerExpired = false;
            System.out.println("Sending panels to " + playerHashCode);
            HashMap<String, WindowPanel> usernameToPanelHashMap = new HashMap<>();
            for(Player player : players){
                if(player.getPanel() != null) {
                    usernameToPanelHashMap.put(player.getUsername(), player.getPanel());
                }
            }
            //TODO change getPrivateColor with getPlayerByHashCode.getPrivateColor
            notifyPanelChoice(playerHashCode, panels.get(playerHashCode),usernameToPanelHashMap, getPlayerPrivateColor(playerHashCode));
            PanelChoiceTimer panelChoiceTimer = new PanelChoiceTimer(System.currentTimeMillis(), this);
            panelChoiceTimer.start();
            while(waitingForPanelChoice && !panelChoiceTimerExpired){
            }
            System.out.println("Proceeding due to flag change.");
            System.out.println("---> waitingForPanelChoice = " + waitingForPanelChoice);
            System.out.println("---> panelChoiceTimerExpired = " + panelChoiceTimerExpired);
            panelChoiceTimer.interrupt();
            if(chosenPanelIndex == -1) chosenPanelIndex = 0;
            System.out.println("panel index assigned = " + chosenPanelIndex);
            getPlayerByHashcode(playerHashCode).setPanel(panels.get(playerHashCode).get(chosenPanelIndex));
            getPlayerByHashcode(playerHashCode).setFavorTokens(getPlayerByHashcode(playerHashCode).getPanel().getFavorTokens());
        }
        extractPublicObjCards();
        extractToolCards();
        roundTrack.setCurrentRound(1);
        draftPool.addAll(diceBag.extractDices(players.size() *2+1));
        System.out.println("Game is starting.. notify users of that");
        notifyGameStart();
        gameHandler();
    }

    public void gameHandler(){
        Player justPlayedPlayer = null;
        for(int i = 1; i <= 10; i++){
            for(int j = 1; j <= players.size()*2; j++){
                System.out.println("BEGIN TURN = " + j + ", ROUND = " + i);
                endTurn = false;
                dicePlaced = false;
                usedToolCard = false;
                isSpecialTurn = false;
                turnTimeout = false;
                Timer turnTimer = new Timer();
                currentTimerTask = new MyTimerTask();
                turnTimer.schedule(currentTimerTask, StaticValues.TURN_DURATION);
                while (!endTurn && !(dicePlaced && usedToolCard && !isSpecialTurn) && !turnTimeout){
                    //wait for user action
                }
                System.out.println("END TURN = " + j + ", ROUND = " + i);
                System.out.println("turn ended by user = " + endTurn);
                System.out.println("dice placed= " + dicePlaced);
                System.out.println("used toolcard = " + usedToolCard);
                System.out.println("is special turn = " + isSpecialTurn);
                System.out.println("turn timeout" + turnTimeout);
                justPlayedPlayer = players.get(getCurrentPlayerIndex());
                toNextTurn();
                if(j != players.size()*2) notifyEndTurn(justPlayedPlayer , players.get(getCurrentPlayerIndex()));
            }
            toNextRound();
            setTurn(1);
            if(i != 10) notifyEndTurn(justPlayedPlayer, players.get(getCurrentPlayerIndex()));
        }
        ArrayList<PlayerScore> playersScore = new ArrayList<>();
        for (Player player : players) {
            playersScore.add(getPlayerScore(player));
        }
        notifyEndGame(playersScore);
    }



    public ArrayList<Dice> getDraftPool(){
        ArrayList<Dice> h = new ArrayList<>();
        for(Dice dice : draftPool){
            h.add(new Dice(dice));
        }
        return h;
    }

    //TODO check if enum should be passed as a copy and not as a reference --> private invariant
    public GameStatus getGameStatus() {
        return gameStatus;
    }

    public Player getActivePlayer() {
        return activePlayer;
    }

    public Player getPlayer(String username){
        return players.stream().filter(x -> x.getUsername().equals(username)).findFirst().orElse(null);
    }

    public void toNextRound(){
        if (roundTrack.getCurrentRound() == 10){
            gameStatus = GameStatus.SCORE;
            System.out.println("WARNING --> 10 turns played");
            return;
        }
        else{
            roundTrack.setDicesOnTurn(roundTrack.getCurrentRound(), getDraftPool());
            roundTrack.nextRound();
            draftPool.clear();
            draftPool.addAll(diceBag.extractDices(players.size() *2 + 1));
            reorderPlayers();
        }
    }

    public long getLobbyTimerStartTime(){
        return lobbyTimerStartTime;
    }

    public int joinGame(String username, LobbyObserver lobbyObserver) {
        int i = 1;
        String user = username;
        while(isInMatch(user)){
            user = username + "(" + i + ")";
            i++;
        }
        Player h = new Player(user);
        players.add(h);
        notifyPlayerJoin(user,getUsernames(),players.size());
        attachLobbyObserver(lobbyObserver);
        if(players.size() == 2){
            //start timer
            lobbyTimerStartTime = System.currentTimeMillis();
            lobbyTimer = new LobbyTimer(lobbyTimerStartTime, this);
            lobbyTimer.start();
            notifyTimerChanges(TimerStatus.START);
        }
        else{
            if(players.size() == 4){
                //starting game
                lobbyTimer.interrupt();
                notifyTimerChanges(TimerStatus.FINISH);
                Runnable myrunnable = () -> {
                    init();
                };
                new Thread(myrunnable).start();

            }
        }
        return h.hashCode();
    }

    public boolean isJoinable(){
        return players.size() < StaticValues.MAX_USER_PER_GAME && gameStatus.equals(GameStatus.INIT);
    }

    public String getPlayerUsername(int hashCode){
        for(Player player : players){
            if (player.hashCode() == hashCode) return player.getUsername();
        }
        return null;
    }


    public boolean isInMatch(String username){
        for(Player player : players){
            if (player.getUsername().equals(username)) return true;
        }
        return false;
    }

    public ArrayList<Player> getPlayers(){
        ArrayList<Player> playersCopy = new ArrayList<>();
        for(Player player : players){
            playersCopy.add(new Player(player));
        }
        return playersCopy;
    }
    public int getActivePlayersNumber(){
        return (int) players.stream().filter(x -> x.getPlayerStatus()==PlayerStatus.ACTIVE).count();
    }

    public ArrayList<String> getUsernames(){
        ArrayList<String> playersUsername = new ArrayList<>();
        for(Player player : players){
            playersUsername.add(player.getUsername());
        }
        return playersUsername;
        //Commented cause im not sure this keep the array ordered
        //naive way doesn't fail
        //return this.getPlayers().stream().map(Player::getUsername).collect(Collectors.toCollection(ArrayList::new));
    }

    public void leaveLobby(String username, LobbyObserver observer){
        for(Player player : players){
            if (player.getUsername().equals(username)){
                players.remove(player);
                detachLobbyObserver(observer);
                notifyPlayerLeave(username,getUsernames(),players.size());
                if(players.size() < 2){
                    if(lobbyTimer != null) {
                        lobbyTimer.interrupt();
                    }
                    lobbyTimerStartTime = 0;
                    notifyTimerChanges(TimerStatus.INTERRUPT);
                }
                return;
            }
        }
    }

    public int getPlayerHashCode(String username){
        for(Player player : players){
            if (player.getUsername().equals(username)){
                return player.hashCode();
            }
        }
        return -1;
    }


    public void attachGameObserver(GameObserver observer){
        if(observer == null) return;
        gameObservers.add(observer);
    }

    public void detachGameObserver(GameObserver observer){
        lobbyObservers.remove(observer);
    }

    public void attachLobbyObserver(LobbyObserver observer){
        if(observer == null) return;
        lobbyObservers.add(observer);
    }

    public void detachLobbyObserver(LobbyObserver observer){
        lobbyObservers.remove(observer);
    }

    public void notifyPanelChoice(int playerHashCode, ArrayList<WindowPanel> panels, HashMap<String, WindowPanel> panelsAlreadyChosen, Color color){
        for(GameObserver observer : gameObservers){
            try {
                observer.onPanelChoice(playerHashCode, panels, panelsAlreadyChosen, color);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }



    public void notifyTimerChanges(TimerStatus timerStatus) {
        for (LobbyObserver observer : lobbyObservers) {
            try {
                System.out.println("I'm notifying a new LobbyTimerStatus = " + timerStatus);
                observer.onTimerChanges(lobbyTimerStartTime, timerStatus);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }


    public void notifyPlayerJoin (String username,ArrayList<String> players, int numOfPlayers){
        for (LobbyObserver observer: lobbyObservers) {
            try {

                observer.onPlayerJoined(username , players,numOfPlayers);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void notifyGameStart(){
        HashMap<String, WindowPanel> usernameToPanel = new HashMap<>();
        for(Player player : players){
            usernameToPanel.put(player.getUsername(), player.getPanel());
        }
        for(GameObserver gameObserver : gameObservers){
            try {
                gameObserver.onGameStart(new GameStartMessage(usernameToPanel, draftPool, toolCards, publicObjectiveCards, getUsernames(),players));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void notifyPlayerLeave(String username,ArrayList<String> players,int numOfPlayers) {
        for (LobbyObserver observer: lobbyObservers) {
            try {
                observer.onPlayerLeave(username ,players, numOfPlayers);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void notifyEndTurn(Player previousPlayer, Player currentPlayer){
        EndTurnMessage endTurnMessage = new EndTurnMessage(previousPlayer, currentPlayer, players, turn, draftPool, roundTrack);
        for(GameObserver gameObserver : gameObservers){
            try {
                gameObserver.onEndTurn(endTurnMessage);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void notifyEndGame(ArrayList<PlayerScore> playersScore) {
        for(GameObserver gameObserver : gameObservers){
            try {
                gameObserver.onEndGame(playersScore);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public HashMap<Integer, ArrayList<WindowPanel>> extractPanels() {
        HashMap<Integer, ArrayList<WindowPanel>> temp = new HashMap<>();
        ArrayList<Integer> notUsedPanel = new ArrayList<>();
        for(int i = 1; i <= StaticValues.NUMBER_OF_CARDS; i++ ){
            notUsedPanel.add(i);
        }
        for(Player player : players){
            int randomNum = ThreadLocalRandom.current().nextInt(0, notUsedPanel.size());
            ArrayList<WindowPanel> panels = new ArrayList<>();
            panels.add(new WindowPanel(notUsedPanel.get(randomNum), 1));
            panels.add(new WindowPanel(notUsedPanel.get(randomNum), 0));
            notUsedPanel.remove(randomNum);
            randomNum = ThreadLocalRandom.current().nextInt(0, notUsedPanel.size());
            panels.add(new WindowPanel(notUsedPanel.get(randomNum), 1));
            panels.add(new WindowPanel(notUsedPanel.get(randomNum), 0));
            notUsedPanel.remove(randomNum);
            temp.put(player.hashCode() , panels);
        }
        return temp;
    }

    public void assignPrivateObjectiveColors(){
        ArrayList<Integer> notUsedColor = new ArrayList<>();
        for(int i = 0; i < Color.values().length; i++){
            notUsedColor.add(i);
        }
        for (Player player : players){
            int index = ThreadLocalRandom.current().nextInt(0, notUsedColor.size());
            player.setPrivateColor(Color.values()[notUsedColor.remove(index)]);
        }
    }


    public Color getPlayerPrivateColor(int playerHashCode){
        for(Player player : players){
            if(player.hashCode() == playerHashCode) return player.getPrivateColor();
        }
        return null;
    }

    public HashMap<Integer, Color> getPrivateObjectiveColors(){
        HashMap<Integer, Color> result = new HashMap<>();
        for(Player player : players){
            result.put(player.hashCode(), player.getPrivateColor());
        }
        return result;
    }

    public void extractToolCards(){
        Random r = new Random();
        ArrayList<ToolCard> allToolCards = new ArrayList<>();
/*
        allToolCards.add(new ToolCard1());
        allToolCards.add(new ToolCard2());
        allToolCards.add(new ToolCard3());
        allToolCards.add(new ToolCard4());
        allToolCards.add(new ToolCard5());
        allToolCards.add(new ToolCard6());
        allToolCards.add(new ToolCard7());
        allToolCards.add(new ToolCard8());
        allToolCards.add(new ToolCard9());
        allToolCards.add(new ToolCard10());
        allToolCards.add(new ToolCard11());rmi

        allToolCards.add(new ToolCard12());
*/
        allToolCards.add(new ToolCard7());
        allToolCards.add(new ToolCard5());
        allToolCards.add(new ToolCard5());
        for(int i = 0; i < 3 ; i++){
            toolCards.add(allToolCards.remove( r.nextInt(allToolCards.size()) ));
        }
    }

    public void extractPublicObjCards(){
        Random r = new Random();
        ArrayList<PublicObjectiveCard> allPubObjCards = new ArrayList<>();

        allPubObjCards.add(new PublicObjectiveCard1());
        allPubObjCards.add(new PublicObjectiveCard2());
        allPubObjCards.add(new PublicObjectiveCard3());
        allPubObjCards.add(new PublicObjectiveCard4());
        allPubObjCards.add(new PublicObjectiveCard5());
        allPubObjCards.add(new PublicObjectiveCard6());
        allPubObjCards.add(new PublicObjectiveCard7());
        allPubObjCards.add(new PublicObjectiveCard8());
        allPubObjCards.add(new PublicObjectiveCard9());
        allPubObjCards.add(new PublicObjectiveCard10());

        for(int i = 0; i < 3 ; i++){
            publicObjectiveCards.add(allPubObjCards.remove( r.nextInt(allPubObjCards.size()) ));
        }
    }

    public void pairPanelToPlayer(int playerHashCode, int panelIndex) {
        chosenPanelIndex = panelIndex;
    }

    public boolean disconnect(int playerHashCode, LobbyObserver lobbyObserver, GameObserver gameObserver){
        for(Player player : players){
            if(player.hashCode() == playerHashCode) {
                detachLobbyObserver(lobbyObserver);
                detachGameObserver(gameObserver);
                player.setPlayerStatus(PlayerStatus.INACTIVE);
                return true;
            }
        }
        return false;
    }

    private void toNextTurn(){
        setTurn(turn + 1);
        //reorderPlayers();
    }

    private void reorderPlayers(){

        ArrayList<Player> h = new ArrayList<>();
        players.add(players.get(0));
        for(int i = 1; i < players.size() - 1; i++){
            players.set(i-1, players.get(i));
            h.add(players.get(i));
        }
        players.remove(players.size()-2);
    }

    public void setTurn(int turn){
        this.turn = turn;
    }


    public int getCurrentPlayerIndex(){
        if(turn > players.size()){
            return (players.size()-1) - (turn - players.size() - 1);
        }
        else{
            return turn - 1;
        }
    }

    public synchronized PlaceDiceResult placeDice(int playerHashCode, int diceIndex, int row, int col){
        Player currentPlayer = getPlayerByHashcode(playerHashCode);
        if (dicePlaced) return new PlaceDiceResult("Can't place two dice in the same turn!", false,currentPlayer.getPanel());
        if(players.get(getCurrentPlayerIndex()).hashCode() != playerHashCode) return new PlaceDiceResult("Can't do game actions during others players turn", false,currentPlayer.getPanel());
        int index = (row * StaticValues.PATTERN_COL) + col;
        boolean result = currentPlayer.getPanel().addDice(index,draftPool.get(diceIndex));
        System.out.println("place dice result = " + result);
        if(result) {
            dicePlaced = true;
            draftPool.remove(diceIndex);
            gameObservers.forEach(x -> {
                try {
                    x.onDicePlaced(new DicePlacedMessage(currentPlayer.getUsername(),new WindowPanel(currentPlayer.getPanel()),draftPool));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
            notifyAll();
            return new PlaceDiceResult("risiko è meglio",true,new WindowPanel(currentPlayer.getPanel()));
        }
        else {
            notifyAll();
            return new PlaceDiceResult("Invalid position, pay attention to game rules!" , false, new WindowPanel(currentPlayer.getPanel()));
        }
    }

    private Player getPlayerByHashcode(int hashCode){
        for(Player player : players){
            if(player.hashCode() == hashCode) return player;
        }
        return null;
    }

    public void setEndTurn(int playerHashCode){
        if(players.get(getCurrentPlayerIndex()).hashCode() == playerHashCode){
            endTurn = true;
            currentTimerTask.isValid = false;
        }
    }

    private PlayerScore getPlayerScore(Player player) {
        PlayerScore playerScore = new PlayerScore();
        playerScore.setUsername(player.getUsername());
        playerScore.setPlayerHashCode(player.hashCode());
        playerScore.setPrivateColor(player.getPrivateColor());
        playerScore.setWindowPanel(player.getPanel());
        playerScore.setFavorTokenPoints(player.getFavorTokens());
        playerScore.setEmptyCellsPoints(player.getPanel().getEmptyCells());
        playerScore.setPublicObjectiveCard1Points(publicObjectiveCards.get(0).getScore(player.getPanel()));
        playerScore.setPublicObjectiveCard2Points(publicObjectiveCards.get(1).getScore(player.getPanel()));
        playerScore.setPublicObjectiveCard3Points(publicObjectiveCards.get(2).getScore(player.getPanel()));
        playerScore.setPrivateObjectiveCardPoints(player.getPanel().getPrivateScore(player.getPrivateColor()));
        playerScore.calculateTotalPoints();
        return playerScore;
    }


    public boolean isToolCardUsable(int playerHashCode, int toolCardIndex){
        ToolCard toolCard = toolCards.get(toolCardIndex);
        Player player = getPlayerByHashcode(playerHashCode);
        if (toolCard != null && player != null) {
            if (toolCard.getId() == 5 && roundTrack.getCurrentRound() == 1) {
                System.out.println("Trying to use tool card number 5 during first round.\nOperation denied.");
                return false;
            }
            if (toolCard.getId() == 7 && (turn <= players.size() || dicePlaced)) {
                System.out.println("Trying to use tool card number 7 during second turn of the round.\nOperation denied");
                return false;
            }
            if (toolCard.getId() == 9 && dicePlaced) {
                System.out.println("Trying to use tool card number 9 usable only on drafting.\nOperation denied");
                return false;
            }
           return player.getFavorTokens() >=
                   toolCard.getCost();
        }
        return false;
    }

    public int getToolCardID(int toolCardIndex){
        return toolCards.get(toolCardIndex).getId();
    }

    public UseToolCardResult useToolCard(int playerHashCode, ToolCardParameters toolCardParameters) {
        if(players.get(getCurrentPlayerIndex()).hashCode() != playerHashCode) return new UseToolCardResult(false, draftPool , roundTrack , players);
        ToolCard toolCard = toolCards.stream().filter( x -> x.getId() == toolCardParameters.toolCardID).findFirst().orElse(null);
        if(toolCard != null){
            Player player = getPlayerByHashcode(playerHashCode);
            if (player != null) {
                System.out.println(player.getUsername() + " is using toolcard ID = " + toolCard.getId());
                usedToolCard = true;
                switch (toolCard.getId()){
                    case 1:
                        if(!toolCard1ParamsOk(toolCardParameters)){
                            usedToolCard = false;
                            return new UseToolCardResult(false, draftPool, roundTrack, players);
                        }
                        //Building command
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard1(draftPool.get(toolCardParameters.draftPoolDiceIndex), toolCardParameters.actionSign));
                        return new UseToolCardResult(true, draftPool, roundTrack, players);
                    case 2:
                        System.out.println("Using toolCard2 Dice: " + toolCardParameters.panelDiceIndex + " Cell: " + toolCardParameters.panelCellIndex);
                        if (!toolCard2ParamsOk(player,toolCardParameters)){
                            usedToolCard = false;
                            return new UseToolCardResult(false, draftPool,roundTrack,players);
                        }
                        //Building command
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard2(new Pair<>(toolCardParameters.panelDiceIndex,toolCardParameters.panelCellIndex),player.getPanel()));
                        return new UseToolCardResult(true, draftPool, roundTrack, players);
                    case 3:
                        System.out.println("Using toolCard3 Dice: " + toolCardParameters.panelDiceIndex + " Cell: " + toolCardParameters.panelCellIndex);
                        if (!toolCard3ParamsOk(player,toolCardParameters)){
                            usedToolCard = false;
                            return new UseToolCardResult(false, draftPool,roundTrack,players);
                        }
                        //Building command
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard3(new Pair<>(toolCardParameters.panelDiceIndex,toolCardParameters.panelCellIndex),player.getPanel()));
                        return new UseToolCardResult(true, draftPool, roundTrack, players);
                    case 5:
                        System.out.println("Using toolCard5 Draft pool dice: " + toolCardParameters.draftPoolDiceIndex + "Round: " +
                                toolCardParameters.roundTrackRoundIndex + "Selected round dice: " + toolCardParameters.roundTrackDiceIndex);
                        Dice draftPoolDice = draftPool.get(toolCardParameters.draftPoolDiceIndex);
                        if (!toolCard5ParamsOk(draftPoolDice)){
                            usedToolCard = false;
                            return new UseToolCardResult(false, draftPool,roundTrack,players);
                        }
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard5(draftPoolDice, roundTrack,
                                toolCardParameters.roundTrackRoundIndex, toolCardParameters.roundTrackDiceIndex));
                        return new UseToolCardResult(true, draftPool, roundTrack, players);
                    case 4:
                        System.out.println("Using toolcard4 Dice: " + toolCardParameters.panelDiceIndex + " in Cell: " + toolCardParameters.panelCellIndex);
                        System.out.println("and Dice: " + toolCardParameters.secondPanelDiceIndex + " in Cell: " + toolCardParameters.secondPanelCellIndex);
                        if (!toolCard4ParamsOk(player,toolCardParameters)){
                            usedToolCard = false;
                            return new UseToolCardResult(false, draftPool,roundTrack,players);
                        }
                        //Building command
                        LinkedHashMap<Integer,Integer> linkedHashMap = new LinkedHashMap<>();
                        linkedHashMap.put(toolCardParameters.panelDiceIndex,toolCardParameters.panelCellIndex);
                        linkedHashMap.put(toolCardParameters.secondPanelDiceIndex,toolCardParameters.secondPanelCellIndex);
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard4(linkedHashMap,player.getPanel()));
                        return new UseToolCardResult(true, draftPool, roundTrack, players);
                    case 7:
                        System.out.println("Using toolCard7: re-rolling draft pool dices");
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard7(draftPool));
                        return new UseToolCardResult(true, draftPool, roundTrack, players);
                    default:
                        break;
                }
            }
        }
        return new UseToolCardResult(false, draftPool , roundTrack , players);
    }

    private boolean toolCard4ParamsOk(Player player, ToolCardParameters toolCardParameters) {
        WindowPanel windowPanel = player.getPanel();
        if (windowPanel == null) return false;
        Cell cell = windowPanel.getCell(toolCardParameters.panelCellIndex);
        if (cell == null) return false;
        Cell secondCell = windowPanel.getCell(toolCardParameters.secondPanelCellIndex);
        if (secondCell == null) return false;
        Cell diceCell = windowPanel.getCell(toolCardParameters.panelDiceIndex);
        if (diceCell == null) return false;
        Dice dice = diceCell.getDiceOn();
        if (dice == null) return false;
        Cell secondDiceCell = windowPanel.getCell(toolCardParameters.secondPanelDiceIndex);
        if (secondDiceCell == null) return false;
        Dice secondDice = diceCell.getDiceOn();
        if (secondDice == null) return false;
        return true;
    }

    private boolean toolCard3ParamsOk(Player player, ToolCardParameters toolCardParameters) {
        return toolCard2ParamsOk(player,toolCardParameters);
    }


    private boolean toolCard1ParamsOk(ToolCardParameters toolCardParameters){
        Dice dice = draftPool.get(toolCardParameters.draftPoolDiceIndex);
        if (dice == null) return false;
        if (dice.getValue() == 1 && toolCardParameters.actionSign == -1) return false;
        if (dice.getValue() == 6 && toolCardParameters.actionSign == +1) return false;
        return true;
    }
    private boolean toolCard2ParamsOk(Player player, ToolCardParameters toolCardParameters){
        WindowPanel windowPanel = player.getPanel();
        if (windowPanel == null) return false;
        Cell cell = windowPanel.getCell(toolCardParameters.panelCellIndex);
        if (cell == null) return false;
        Cell diceCell = windowPanel.getCell(toolCardParameters.panelDiceIndex);
        if (diceCell == null) return false;
        Dice dice = diceCell.getDiceOn();
        if (dice == null) return false;
        return true;
    }

    private boolean toolCard5ParamsOk(Dice draftPoolDice) {
        if (draftPoolDice == null) return false;
        return true;
    }


    private class MyTimerTask extends TimerTask{

        public volatile boolean isValid;

        public MyTimerTask(){
            isValid = true;
        }

        @Override
        public void run() {
            if(isValid){
                System.out.println("TURN TIMEOUT");
                turnTimeout = true;
            }
            else{
                System.out.println("TIMEOUT ALREADY ENDED TASK --> DO NOTHING");
            }
        }
    }

}
