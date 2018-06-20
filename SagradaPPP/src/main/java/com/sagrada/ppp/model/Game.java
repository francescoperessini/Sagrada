package com.sagrada.ppp.model;

import com.sagrada.ppp.cards.publicobjectivecards.*;
import com.sagrada.ppp.cards.toolcards.*;
import com.sagrada.ppp.network.server.Service;
import com.sagrada.ppp.utils.StaticValues;
import javafx.util.Pair;

import java.io.Serializable;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core class of the game
 * Each game has a unique instance of this class.
 * Allows to have multiple games on the same server.
 */
public class Game implements Serializable{
    private HashMap<Integer, LobbyObserver> lobbyObservers;
    private HashMap<Integer, ArrayList<GameObserver>> gameObservers;
    private ArrayList<Player> players;
    private DiceBag diceBag;
    private ArrayList<Dice> draftPool;
    private RoundTrack roundTrack;
    private GameStatus gameStatus;
    private LobbyTimer lobbyTimer;
    private long lobbyTimerStartTime;
    public volatile boolean waitingForPanelChoice;
    public volatile boolean panelChoiceTimerExpired;
    public Integer chosenPanelIndex;
    private ArrayList<ToolCard> toolCards;
    private ArrayList<PublicObjectiveCard> publicObjectiveCards;
    private int turn = 1;
    private volatile boolean dicePlaced;
    private volatile boolean usedToolCard;
    private volatile boolean isSpecialTurn;
    private volatile boolean endTurn;
    private volatile boolean turnTimeout;
    private MyTimerTask currentTimerTask;
    private volatile boolean gameEnded;
    private Service service;

    /*
    TODO: Add a method that given the username string returns the desired players
    TODO: Add overloading methods that take a Player as a parameter instead of a String
    */

    public Game(String username, Service service) {
        this.service = service;
        diceBag = new DiceBag();
        players = new ArrayList<>();
        draftPool = new ArrayList<>();
        roundTrack = new RoundTrack();
        gameStatus = GameStatus.INIT;
        if(username != null) players.add(new Player(username));
        lobbyObservers = new HashMap<>();
        waitingForPanelChoice = false;
        panelChoiceTimerExpired = false;
        chosenPanelIndex = null;
        gameObservers = new HashMap<>();
        toolCards = new ArrayList<>();
        publicObjectiveCards = new ArrayList<>();
        chosenPanelIndex = -1;
        dicePlaced = false;
        usedToolCard = false;
        isSpecialTurn = false;
        endTurn = false;
        turnTimeout = false;
        gameEnded = false;
    }

    /**
     * Handles the setup of a game
     */
    void init() {
        gameStatus = GameStatus.ACTIVE;
        assignPrivateObjectiveColors();
        //Shuffles the collection to have pseudo random turn orders!
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

            //TODO syncronize this!
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
        gameStatus = GameStatus.ACTIVE;
        extractPublicObjCards();
        extractToolCards();
        draftPool.addAll(diceBag.extractDices(players.size() *2+1));
        System.out.println("Game is starting.. notify users of that");
        notifyGameStart();
        gameHandler();

        if (service != null) {
            service.deleteGame(this.hashCode());
        }
    }

    /**
     * Handling turns and rounds mechanics.
     * From round 1 to round 10 and foreach round handles players turn, according to game rules
     */
    private void rmiPing(GameObserver gameObserver) throws RemoteException {
            gameObserver.rmiPing();
    }

    private void gameHandler() {
        Player justPlayedPlayer = null;

        for(int i = 1; i <= 10; i++){
            for (Player player : players) {
                player.setSkipSecondTurn(false);
            }

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
                justPlayedPlayer = players.get(getCurrentPlayerIndex());

                //check if player has to skip his second turn
                //only in this if statement justPlayedPlayer contains the current player
                //after call "toNextTurn" justPlayedPlayer contains the previous player
                if (justPlayedPlayer.hasToSkipSecondTurn() || justPlayedPlayer.getPlayerStatus().equals(PlayerStatus.INACTIVE)) {
                    if(justPlayedPlayer.hasToSkipSecondTurn()) {
                        System.out.println(players.get(getCurrentPlayerIndex()).getUsername() + " has to skip second turn " +
                                "this round due to toolCard8 behaviour");
                    }
                    else {
                        System.out.println("Player should be inactive, actual player status = " + justPlayedPlayer.getPlayerStatus());
                    }
                    toNextTurn();
                    if(j != players.size()*2) notifyEndTurn(justPlayedPlayer , players.get(getCurrentPlayerIndex()));
                    currentTimerTask.isValid = false;
                    continue;
                }
                while (!endTurn && !(dicePlaced && usedToolCard && !isSpecialTurn) && !turnTimeout && !gameEnded){
                    //wait for user action
                }
                //Sync end turn with eventual responses e.g. UseToolCardResponse
                synchronized (this) {
                    //handling timer

                    if (dicePlaced && usedToolCard) {
                        currentTimerTask.isValid = false;
                    }

                    if(turnTimeout && !(dicePlaced || usedToolCard)){
                        setPlayerAFK(justPlayedPlayer);
                    }

                    System.out.println("END TURN = " + j + ", ROUND = " + i);
                    System.out.println("turn ended by user = " + endTurn);
                    System.out.println("dice placed = " + dicePlaced);
                    System.out.println("used toolCard = " + usedToolCard);
                    System.out.println("is special turn = " + isSpecialTurn);
                    System.out.println("turn timeout " + turnTimeout);
                    toNextTurn();

                    if (j != players.size() * 2) notifyEndTurn(justPlayedPlayer, players.get(getCurrentPlayerIndex()));
                }
            }
            toNextRound();
            setTurn(1);
            if(i != 10) notifyEndTurn(justPlayedPlayer, players.get(getCurrentPlayerIndex()));
        }
        if (gameEnded) return;
        //handling end game and points calculation
        ArrayList<PlayerScore> playersScore = new ArrayList<>();
        for (Player player : players) {
            playersScore.add(getPlayerScore(player));
        }
        notifyEndGame(playersScore);
    }

    /**
     * @return returns a new instance of the draft pool
     */
    private ArrayList<Dice> getDraftPool() {
        ArrayList<Dice> h = new ArrayList<>();
        for(Dice dice : draftPool){
            h.add(new Dice(dice));
        }
        return h;
    }

    public void disableAFK(int playerHashCode){
        getPlayerByHashcode(playerHashCode).setPlayerStatus(PlayerStatus.ACTIVE);
        //TODO implements notification
    }

    private void setPlayerAFK(Player playerAFK){
        playerAFK.setPlayerStatus(PlayerStatus.INACTIVE);
        if (getActivePlayersNumber() < 2){
            Player activePlayer = players.stream().filter(x -> x.getPlayerStatus().equals(PlayerStatus.ACTIVE)).findFirst().orElse(null);
            notifyPlayerAFK(playerAFK, true, activePlayer);
            gameEnded = true;
        }
        else{
            notifyPlayerAFK(playerAFK , false, null);

        }
    }

    private void notifyPlayerAFK(Player playerAFK, boolean isLastPlayer, Player lastPlayer){
        pingAllGameObservers();
        for (ArrayList<GameObserver> observers : gameObservers.values()) {
            for (GameObserver observer : observers) {
                try {
                    observer.onPlayerAFK(playerAFK, isLastPlayer, lastPlayer);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * Handles end turn mechanics,
     * notify each client the updated game status
     * Calls the player reordering (for more information about player reordering see reorderPlayers() method)
     */
    private void toNextRound() {
        if (roundTrack.getCurrentRound() == 10){
            gameStatus = GameStatus.SCORE;
            System.out.println("WARNING --> 10 turns played");
            return;
        }
        else{
            roundTrack.setDicesOnRound(roundTrack.getCurrentRound(), getDraftPool());
            roundTrack.nextRound();
            draftPool.clear();
            draftPool.addAll(diceBag.extractDices(players.size() * 2 + 1));
            reorderPlayers();
        }
    }

    public long getLobbyTimerStartTime(){
        return lobbyTimerStartTime;
    }

    /**
     * @param username username received by the client
     * @param lobbyObserver observer that handles the lobby notifications(local for socket, remote for rmi)
     * @return returns the player's hash code
     */
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
        attachLobbyObserver(lobbyObserver, h.getHashCode());
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

    /**
     * @return true if players.size is lower than MAX_USER_PER_GAME (default = 4) and game is in INIT STATUS
     */
    public boolean isJoinable(){
        return players.size() < StaticValues.MAX_USER_PER_GAME && gameStatus.equals(GameStatus.INIT);
    }

    /**
     * @param hashCode unique hashCode of the player (identifies a player)
     * @return string containing player's username
     */
    public String getPlayerUsername(int hashCode){
        for(Player player : players){
            if (player.hashCode() == hashCode) return player.getUsername();
        }
        return null;
    }


    /**
     * @param username unique username of the player
     * @return true if username is in the players list
     */
    private boolean isInMatch(String username) {
        for(Player player : players){
            if (player.getUsername().equals(username)) return true;
        }
        return false;
    }

    /**
     * @return returns a copy of the players list
     */
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

    /**
     * @return list of strings each containing the username of a player in this game
     */
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

    /**
     * If a player leaves the lobby this method is called, its StartGameView Observer is removed, and the lobby timer is interrupted if less than 2 players remains in this game's lobby.
     * @param username identify the player who wants to leave the lobby
     * @param observer observer to remove from observers list.
     */
    public void leaveLobby(String username, LobbyObserver observer){
        for(Player player : players){
            if (player.getUsername().equals(username)){
                players.remove(player);
                detachLobbyObserver(player.getHashCode());
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

    /**
     * @param username identify a player
     * @return returns player's hash code
     */
    public int getPlayerHashCode(String username){
        for(Player player : players){
            if (player.getUsername().equals(username)){
                return player.hashCode();
            }
        }
        return -1;
    }


    public void attachGameObserver(GameObserver observer, int playerHashCode){
        if(observer == null) return;
        if(gameObservers.get(playerHashCode) == null){
            gameObservers.put(playerHashCode, new ArrayList<>());
        }
        gameObservers.get(playerHashCode).add(observer);
    }
    public void detachAllGameObservers(int playerHashCode){
        gameObservers.remove(playerHashCode);
    }

    public void attachLobbyObserver(LobbyObserver observer, int playerHashCode){
        if(observer == null) return;
        lobbyObservers.put(playerHashCode, observer);
    }

    public void detachLobbyObserver(int playerHashCode) {
        lobbyObservers.remove(playerHashCode);
    }

    private int getPlayerHashCodeByGameObserver(GameObserver gameObserver){
        for(Integer hash : gameObservers.keySet()){
            for ( GameObserver obs : gameObservers.get(hash)){
                if (obs.equals(gameObserver)) return hash;
            }
        }
        return -1;
    }

    private int getPlayerHashCodeByLobbyObserver(LobbyObserver lobbyObserver){
        for(Integer hash : lobbyObservers.keySet()){
            if(lobbyObservers.get(hash).equals(lobbyObserver)) return hash;
        }
        return -1;
    }

    private synchronized void notifyPanelChoice(int playerHashCode, ArrayList<WindowPanel> panels, HashMap<String, WindowPanel> panelsAlreadyChosen, Color color) {
        pingAllGameObservers();
        for (ArrayList<GameObserver> observers : gameObservers.values()) {
            for (GameObserver observer : observers) {
                try {
                    observer.onPanelChoice(playerHashCode, panels, panelsAlreadyChosen, color);
                } catch (ConnectException e){

                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    void notifyTimerChanges(TimerStatus timerStatus) {
        for (LobbyObserver observer : lobbyObservers.values()) {
            try {
                System.out.println("I'm notifying a new LobbyTimerStatus = " + timerStatus);
                observer.onTimerChanges(lobbyTimerStartTime, timerStatus);
            }catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void notifyPlayerJoin(String username, ArrayList<String> players, int numOfPlayers){
        for (LobbyObserver observer: lobbyObservers.values()) {
            try {
                observer.onPlayerJoined(username , players,numOfPlayers);
            } catch (ConnectException e){
                System.out.println("Connection Exception");
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void notifyGameStart(){
        pingAllGameObservers();
        for (ArrayList<GameObserver> observers : gameObservers.values()) {
            for (GameObserver observer : observers) {
                try {
                    observer.onGameStart(new GameStartMessage(draftPool, toolCards,
                            publicObjectiveCards,players,
                            roundTrack, players.get(getCurrentPlayerIndex())));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void notifyPlayerLeave(String username, ArrayList<String> players, int numOfPlayers) {
        for (LobbyObserver observer: lobbyObservers.values()) {
            try {
                observer.onPlayerLeave(username ,players, numOfPlayers);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void notifyEndTurn(Player previousPlayer, Player currentPlayer){
        EndTurnMessage endTurnMessage = new EndTurnMessage(previousPlayer, currentPlayer, players, turn, draftPool, roundTrack);
        pingAllGameObservers();
        for (ArrayList<GameObserver> observers : gameObservers.values()) {
            for (GameObserver observer : observers) {
                try {
                    observer.onEndTurn(endTurnMessage);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void notifyEndGame(ArrayList<PlayerScore> playersScore) {
        pingAllGameObservers();
        for (ArrayList<GameObserver> observers : gameObservers.values()) {
            for (GameObserver observer : observers) {
                try {
                    observer.onEndGame(playersScore);
                }catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private HashMap<Integer, ArrayList<WindowPanel>> extractPanels() {
        HashMap<Integer, ArrayList<WindowPanel>> temp = new HashMap<>();
        ArrayList<Integer> notUsedPanel = new ArrayList<>();
        for(int i = 1; i <= WindowPanel.getNumberOfPanels(); i++ ){
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

    private void assignPrivateObjectiveColors(){
        ArrayList<Integer> notUsedColor = new ArrayList<>();
        for(int i = 0; i < Color.values().length; i++){
            notUsedColor.add(i);
        }
        for (Player player : players){
            int index = ThreadLocalRandom.current().nextInt(0, notUsedColor.size());
            player.setPrivateColor(Color.values()[notUsedColor.remove(index)]);
        }
    }


    private Color getPlayerPrivateColor(int playerHashCode){
        for(Player player : players){
            if(player.hashCode() == playerHashCode) return player.getPrivateColor();
        }
        return null;
    }

    private void extractToolCards(){
        Random r = new Random();
        ArrayList<ToolCard> allToolCards = new ArrayList<>();

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
        allToolCards.add(new ToolCard11());
        allToolCards.add(new ToolCard12());

        for(int i = 0; i < 3 ; i++){
            toolCards.add(allToolCards.remove( r.nextInt(allToolCards.size()) ));
        }
    }

    private void extractPublicObjCards(){
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

    public boolean disconnect(int playerHashCode) {
        if(gameStatus.equals(GameStatus.INIT)){
            Player player = getPlayerByHashcode(playerHashCode);
            if (player == null) return false;
            leaveLobby(player.getUsername(),lobbyObservers.get(playerHashCode));
            return true;
        }else {
            Player player = getPlayerByHashcode(playerHashCode);
            if (player == null) return false;
            System.out.println("detaching : " + playerHashCode);
            detachLobbyObserver(playerHashCode);
            detachAllGameObservers(playerHashCode);
            player.setPlayerStatus(PlayerStatus.INACTIVE);
            int numOfActivePlayers = getActivePlayersNumber();
            if (numOfActivePlayers < 2) {
                notifyDisconnection(player, true);
                gameStatus = GameStatus.CLOSE;
                gameObservers.clear();
                lobbyObservers.clear();
                gameEnded = true;
            }
            else {
                notifyDisconnection(player, false);
            }
            return true;
        }
    }

    private void toNextTurn() {
        setTurn(turn + 1);
        //reorderPlayers();
    }

    private void reorderPlayers() {
        if(players.size() != 0) {
            ArrayList<Player> h = new ArrayList<>();
            players.add(players.get(0));
            for (int i = 1; i < players.size() - 1; i++) {
                players.set(i - 1, players.get(i));
                h.add(players.get(i));
            }
            players.remove(players.size() - 2);
        }
    }

    private void setTurn(int turn){
        this.turn = turn;
    }

    private int getCurrentPlayerIndex() {
        if(turn > players.size()){
            return (players.size()-1) - (turn - players.size() - 1);
        }
        else{
            return turn - 1;
        }
    }

    public synchronized PlaceDiceResult placeDice(int playerHashCode, int diceIndex, int row, int col) {
        Player currentPlayer = getPlayerByHashcode(playerHashCode);
        if (currentPlayer == null) return new PlaceDiceResult("Player not found!",false,null,draftPool);
        if (dicePlaced) return new PlaceDiceResult("Can't place two dice in the same turn!", false,currentPlayer.getPanel(), draftPool);
        if(players.get(getCurrentPlayerIndex()).hashCode() != playerHashCode) return new PlaceDiceResult(
                "Can't do game actions during others players turn", false,currentPlayer.getPanel(), draftPool);
        int index = (row * StaticValues.PATTERN_COL) + col;
        boolean result;
        if(draftPool.size() != 0) {
             result = currentPlayer.getPanel().addDice(index, draftPool.get(diceIndex));
        }else result = false;
        System.out.println("place dice result = " + result);
        if(result) {
            dicePlaced = true;
            draftPool.remove(diceIndex);
            pingAllGameObservers();
            for (ArrayList<GameObserver> observers : gameObservers.values()) {
                for (GameObserver observer : observers) {
                    try {
                        observer.onDicePlaced(new DicePlacedMessage(currentPlayer.getUsername(),new WindowPanel(currentPlayer.getPanel()),draftPool));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
            return new PlaceDiceResult("risiko è meglio",true,new WindowPanel(currentPlayer.getPanel()), draftPool);
        }
        else {
            return new PlaceDiceResult("Invalid position, pay attention to game rules!" , false,
                    new WindowPanel(currentPlayer.getPanel()), draftPool);
        }
    }

    private Player getPlayerByHashcode(int hashCode) {
        for(Player player : players){
            if(player.getHashCode() == hashCode) return player;
        }
        return null;
    }

    public void setEndTurn(int playerHashCode){
        if(players.get(getCurrentPlayerIndex()).hashCode() == playerHashCode){
            endTurn = true;
            if(currentTimerTask != null)
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

    public synchronized boolean isToolCardUsable(int playerHashCode, int toolCardIndex) {
        ToolCard toolCard;
        if (toolCards.size() != 0 && toolCardIndex < toolCards.size())
            toolCard = toolCards.get(toolCardIndex);
        else
            return false;
        Player player = getPlayerByHashcode(playerHashCode);
        if (toolCard != null && player != null) {
            if (!players.get(getCurrentPlayerIndex()).equals(player)){
                return false;
            }
            if ((toolCard.getId() == 2 || toolCard.getId() == 3) && getPlayerByHashcode(playerHashCode).getPanel().getEmptyCells() > 19){
                System.out.println("You haven't placed enough dices to use this toolcard!\nOperation denied.");
                return false;
            }
            else if (toolCard.getId() == 4 && getPlayerByHashcode(playerHashCode).getPanel().getEmptyCells() > 18){
                System.out.println("You haven't placed enough dices to use this toolcard!\nOperation denied.");
                return false;
            }
            else if (toolCard.getId() == 5 && roundTrack.getCurrentRound() == 1) {
                System.out.println("Trying to use tool card number 5 during first round.\nOperation denied.");
                return false;
            }
            else if (toolCard.getId() == 6 && dicePlaced) {
                System.out.println("Trying to use tool card number 6 usable only on drafting.\nOperation denied.");
                return false;
            }
            else if (toolCard.getId() == 7 && (turn <= players.size() || dicePlaced)) {
                System.out.println("Trying to use tool card number 7 during the first turn of the round OR " +
                        "after placing a dice.\nOperation denied.");
                return false;
            }
            else if (toolCard.getId() == 8 && (turn > players.size() || !dicePlaced)) {
                System.out.println("Trying to use tool card number 8 during second turn of the round OR " +
                        "before placing a dice.\nOperation denied.");
                return false;
            }
            else if (toolCard.getId() == 9 && dicePlaced) {
                System.out.println("Trying to use tool card number 9 usable only on drafting.\nOperation denied.");
                return false;
            }
            else if (toolCard.getId() == 11 && dicePlaced){
                System.out.println("Trying to use tool card number 11 usable only on drafting.\nOperation denied.");
                return false;
            }
            else if (toolCard.getId() == 12 && roundTrack.getCurrentRound() == 1){
                System.out.println("Can't use this toolcard during the first round!\nOperation denied.");
                return false;
            }
           return player.getFavorTokens() >= toolCard.getCost();
        }
        return false;
    }

    public int getToolCardID(int toolCardIndex){
        if(toolCards.size() != 0)
            return toolCards.get(toolCardIndex).getId();
        else
            return -1;
    }

    public synchronized UseToolCardResult useToolCard(int playerHashCode, ToolCardParameters toolCardParameters) {
        if(players.get(getCurrentPlayerIndex()).hashCode() != playerHashCode) return new UseToolCardResult(false,0,0,draftPool , roundTrack , players, null, null);
        ToolCard toolCard = toolCards.stream().filter( x -> x.getId() == toolCardParameters.toolCardID).findFirst().orElse(null);
        if(toolCard != null){
            Player player = getPlayerByHashcode(playerHashCode);
            if (player != null) {
                System.out.println(player.getUsername() + " is using toolCard ID = " + toolCard.getId());
                usedToolCard = true;
                switch (toolCard.getId()){
                    case 1:
                        if(!toolCard1ParamsOk(toolCardParameters)){
                            usedToolCard = false;
                            return new UseToolCardResult(false,toolCard.getId(),toolCard.getCost(), draftPool, roundTrack, players, null,null);
                        }
                        //Building command
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard1(draftPool.get(toolCardParameters.draftPoolDiceIndex), toolCardParameters.actionSign));
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        notifyAll();
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(), draftPool, roundTrack, players, null,null);
                    case 2:
                        System.out.println("Using toolCard2 Dice: " + toolCardParameters.panelDiceIndex + " Cell: " + toolCardParameters.panelCellIndex);
                        if (!toolCard2ParamsOk(new Player(player),toolCardParameters)){
                            usedToolCard = false;
                            notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                            return new UseToolCardResult(false,toolCard.getId(),toolCard.getCost(), draftPool,roundTrack,players, null, null);
                        }
                        //Building command
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard2(new Pair<>(toolCardParameters.panelDiceIndex,toolCardParameters.panelCellIndex),player.getPanel()));
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(), draftPool, roundTrack, players, null, null);
                    case 3:
                        System.out.println("Using toolCard3 Dice: " + toolCardParameters.panelDiceIndex + " Cell: " + toolCardParameters.panelCellIndex);
                        if (!toolCard3ParamsOk(new Player(player),toolCardParameters)){
                            usedToolCard = false;
                            return new UseToolCardResult(false,toolCard.getId(),toolCard.getCost(), draftPool,roundTrack,players, null, null);
                        }
                        //Building command
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard3(new Pair<>(toolCardParameters.panelDiceIndex,toolCardParameters.panelCellIndex),player.getPanel()));
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(), draftPool, roundTrack, players, null, null);
                    case 4:
                        System.out.println("Using toolCard4 Dice: " + toolCardParameters.panelDiceIndex + " in Cell: " + toolCardParameters.panelCellIndex);
                        System.out.println("and Dice: " + toolCardParameters.secondPanelDiceIndex + " in Cell: " + toolCardParameters.secondPanelCellIndex);
                        if (!toolCard4ParamsOk(new Player(player),toolCardParameters)){
                            usedToolCard = false;
                            return new UseToolCardResult(false,toolCard.getId(),toolCard.getCost(), draftPool,roundTrack,players, null, null);
                        }
                        //Building command
                        LinkedHashMap<Integer,Integer> linkedHashMap = new LinkedHashMap<>();
                        linkedHashMap.put(toolCardParameters.panelDiceIndex,toolCardParameters.panelCellIndex);
                        linkedHashMap.put(toolCardParameters.secondPanelDiceIndex,toolCardParameters.secondPanelCellIndex);
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard4(linkedHashMap,player.getPanel()));
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(), draftPool, roundTrack, players, null, null);

                    case 5:
                        System.out.println("Using toolCard5 Draft pool dice: " + toolCardParameters.draftPoolDiceIndex + "Round: " +
                                toolCardParameters.roundTrackRoundIndex + "Selected round dice: " + toolCardParameters.roundTrackDiceIndex);
                        Dice draftPoolDice = draftPool.get(toolCardParameters.draftPoolDiceIndex);
                        if (!toolCard5ParamsOk(draftPoolDice)){
                            usedToolCard = false;
                            return new UseToolCardResult(false,toolCard.getId(),toolCard.getCost(), draftPool,roundTrack,players, null, null);
                        }
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard5(draftPoolDice, roundTrack,
                                toolCardParameters.roundTrackRoundIndex, toolCardParameters.roundTrackDiceIndex));
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(), draftPool, roundTrack, players, null, null);
                    case 6:
                        System.out.println("Using toolCard6");
                        Dice reRoll = draftPool.get(toolCardParameters.draftPoolDiceIndex);
                        draftPool.remove((int) toolCardParameters.draftPoolDiceIndex);
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard6(reRoll));
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(),draftPool,roundTrack,players, reRoll, null);
                    case 7:
                        System.out.println("Using toolCard7: re-rolling draft pool dices");
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard7(draftPool));
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(), draftPool, roundTrack, players, null, null);
                    case 8:
                        System.out.println("Using toolCard8");
                        players.get(getCurrentPlayerIndex()).setSkipSecondTurn(true);
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard8(toolCardParameters.panelCellIndex, player.getPanel(),
                                toolCardParameters.draftPoolDiceIndex, draftPool));
                        isSpecialTurn = true;
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(), draftPool, roundTrack, players, null, null);
                    case 9:
                        System.out.println("Using toolCard9 Dice: " + toolCardParameters.draftPoolDiceIndex + " in Cell: " + toolCardParameters.panelCellIndex);
                        if (!toolCard9ParamsOk(player,toolCardParameters) && !dicePlaced){
                            usedToolCard = false;
                            return new UseToolCardResult(false,toolCard.getId(),toolCard.getCost(), draftPool,roundTrack,players, null, null);
                        }
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard9(player,toolCardParameters.panelCellIndex,draftPool.get(toolCardParameters.draftPoolDiceIndex)));
                        draftPool.remove((int) toolCardParameters.draftPoolDiceIndex);
                        dicePlaced = true;
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(),draftPool,roundTrack,players, null, null);
                    case 10:
                        Dice dice = new Dice(draftPool.get(toolCardParameters.draftPoolDiceIndex));
                        System.out.println("Using toolCard10 with dice = " + dice);

                        //TODO, remove this check
                        if (!toolCard10ParamsOk(dice)){
                            usedToolCard = false;
                            return new UseToolCardResult(false,toolCard.getId(),toolCard.getCost(), draftPool,roundTrack,players, null, null);
                        }
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard10(dice));
                        draftPool.set(toolCardParameters.draftPoolDiceIndex, dice);
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(), draftPool, roundTrack, players, null, null);
                    case 11:
                        System.out.println("Using toolCard 11");
                        if (!toolCard11ParamsOk()){
                            usedToolCard = false;
                            return new UseToolCardResult(false,toolCard.getId(),toolCard.getCost(), draftPool,roundTrack,players, null, null);
                        }
                        Dice draftDice = draftPool.get(toolCardParameters.draftPoolDiceIndex);
                        draftPool.remove((int) toolCardParameters.draftPoolDiceIndex);
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard11(diceBag,draftDice));
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(),draftPool,roundTrack,players, draftDice, null);
                    case 12:
                        System.out.println("Using toolCard12 Dice: " + toolCardParameters.panelDiceIndex + " in Cell: " + toolCardParameters.panelCellIndex);
                        System.out.println("and Dice: " + toolCardParameters.secondPanelDiceIndex + " in Cell: " + toolCardParameters.secondPanelCellIndex);
                        Player playerCopy = new Player(player);
                        if (!toolCard12ParamsOk(toolCardParameters, playerCopy)){
                            usedToolCard = false;
                            return new UseToolCardResult(false,toolCard.getId(),toolCard.getCost(), draftPool,roundTrack,players, null, null);
                        }
                        WindowPanel panel = new WindowPanel(player.getPanel());
                        LinkedHashMap<Integer, Integer> positions = new LinkedHashMap<>();
                        positions.put(toolCardParameters.panelDiceIndex , toolCardParameters.panelCellIndex);
                        if(toolCardParameters.twoDiceAction) {
                            positions.put(toolCardParameters.secondPanelDiceIndex, toolCardParameters.secondPanelCellIndex);
                        }
                        player.setFavorTokens(player.getFavorTokens() - toolCard.getCost());
                        toolCard.use(new CommandToolCard12(positions, panel));
                        player.setPanel(panel);
                        notifyUsedToolCard(toolCardParameters.toolCardID, player, draftPool, roundTrack, toolCard.getCost());
                        return new UseToolCardResult(true,toolCard.getId(),toolCard.getCost(), draftPool, roundTrack, players, null, null);
                    default:
                        break;
                }
            }
            return new UseToolCardResult(false,toolCard.getId(),toolCard.getCost(), draftPool , roundTrack , players, null, null);
        }else {
            return new UseToolCardResult(false,0,0, draftPool , roundTrack , players, null, null);
        }

    }
    //todo move param check on toolcard

    private boolean toolCard9ParamsOk(Player player, ToolCardParameters toolCardParameters) {
        WindowPanel windowPanel = player.getPanel();
        if (windowPanel == null) return false;
        Cell cell = windowPanel.getCell(toolCardParameters.panelCellIndex);
        if (cell == null) return false;
        Dice dice = draftPool.get(toolCardParameters.draftPoolDiceIndex);
        if (dice == null) return false;
        if (!(windowPanel.diceOk(dice,toolCardParameters.panelCellIndex,false,false,true))) return false;
        return true;

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
        windowPanel.removeDice(toolCardParameters.panelDiceIndex);
        if (!windowPanel.addDice(toolCardParameters.panelCellIndex,dice)) return false;
        Cell secondDiceCell = windowPanel.getCell(toolCardParameters.secondPanelDiceIndex);
        if (secondDiceCell == null) return false;
        Dice secondDice = secondDiceCell.getDiceOn();
        if (secondDice == null) return false;
        windowPanel.removeDice(toolCardParameters.secondPanelDiceIndex);
        if (!windowPanel.addDice(toolCardParameters.secondPanelCellIndex,secondDice)) return false;
        return true;
    }

    private boolean toolCard3ParamsOk(Player player, ToolCardParameters toolCardParameters) {
        WindowPanel windowPanel = player.getPanel();
        if (windowPanel == null) return false;
        Cell cell = windowPanel.getCell(toolCardParameters.panelCellIndex);
        if (cell == null) return false;
        Cell diceCell = windowPanel.getCell(toolCardParameters.panelDiceIndex);
        if (diceCell == null) return false;
        Dice dice = diceCell.getDiceOn();
        if (dice == null) return false;
        windowPanel.removeDice(toolCardParameters.panelDiceIndex);
        if (!player.getPanel().diceOk(dice,toolCardParameters.panelCellIndex,false,true,false)) return false;
        return true;
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
        windowPanel.removeDice(toolCardParameters.panelDiceIndex);

        if (!player.getPanel().diceOk(dice,toolCardParameters.panelCellIndex,true,false,false)) return false;
        return true;
    }

    private boolean toolCard5ParamsOk(Dice draftPoolDice) {
        return draftPoolDice != null;
    }

    private boolean toolCard10ParamsOk(Dice dice){
       return dice != null;
    }

    private boolean toolCard11ParamsOk() {
        return diceBag.size() >= 1;
    }

    private boolean toolCard12ParamsOk(ToolCardParameters toolCardParameters , Player player){
        Cell cell1start = player.getPanel().getCell(toolCardParameters.panelDiceIndex);
        Cell cell1end = player.getPanel().getCell(toolCardParameters.panelCellIndex);
        Dice dice = roundTrack.getDice(toolCardParameters.roundTrackRoundIndex, toolCardParameters.roundTrackDiceIndex);
        if (dice == null || cell1end == null || cell1start == null) return false;
        System.out.println("prima print");
        Color color = dice.getColor();
        if((toolCardParameters.secondPanelCellIndex == toolCardParameters.panelCellIndex)||(toolCardParameters.panelCellIndex == toolCardParameters.secondPanelDiceIndex)) return false;
        System.out.println("seconda print");
        if(!cell1start.hasDiceOn() || cell1end.hasDiceOn() || !cell1start.getDiceOn().getColor().equals(color)) return false;
        WindowPanel panel = player.getPanel();
        Dice movingDice1 = panel.removeDice(toolCardParameters.panelDiceIndex);
        if(!panel.addDice(toolCardParameters.panelCellIndex,movingDice1)) return false;
        System.out.println(" terza print");
        if(toolCardParameters.twoDiceAction){
            Cell cell2start = player.getPanel().getCell(toolCardParameters.secondPanelDiceIndex);
            Cell cell2end = player.getPanel().getCell(toolCardParameters.secondPanelCellIndex);
            Dice movingDice2 = panel.removeDice(toolCardParameters.secondPanelDiceIndex);
            if(!panel.addDice(toolCardParameters.secondPanelCellIndex, movingDice2)) return false;
            if(cell2end == null || cell2start == null) return false;
            System.out.println("quarta print");
            if(!cell2start.hasDiceOn() || cell2end.hasDiceOn() || !cell2start.getDiceOn().getColor().equals(color)) return false;
        }
        System.out.println("ultima print");
        return true;
    }

    public PlaceDiceResult specialDicePlacement(int playerHashCode, int cellIndex, Dice dice){
        boolean result;
        if (players.get(getCurrentPlayerIndex()).hashCode() != playerHashCode) return new PlaceDiceResult("Somenthing went wrong!", false, null, null);
        Player player = getPlayerByHashcode(playerHashCode);
        WindowPanel panel = player.getPanel();
        if (panel != null)
            result = panel.addDice(cellIndex, dice);
        else
            result = false;

        if (result) {
            player.setPanel(panel);
            dicePlaced = true;
        }
        return new PlaceDiceResult("Special dice placement" , true, panel, draftPool);
    }

    public ArrayList<Integer> getLegalPositions(int playerHashCode, Dice dice){
        Player player = getPlayerByHashcode(playerHashCode);
        if(player != null)
            if(player.getPanel() != null)
                return player.getPanel().getLegalPosition(dice);
        return null;
    }

    public void putDiceInDraftPool(Dice dice){
        draftPool.add(dice);
    }

    private void notifyUsedToolCard(int toolCardID, Player player, ArrayList<Dice> draftPool, RoundTrack roundTrack,int toolCardCost){
        ToolCardNotificationMessage toolCardNotificationMessage = new ToolCardNotificationMessage(toolCardID, player, draftPool, roundTrack, toolCardCost);
        pingAllGameObservers();
        for (ArrayList<GameObserver> observers : gameObservers.values()) {
            for (GameObserver observer : observers) {
                try {
                    observer.onToolCardUsed(toolCardNotificationMessage);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void notifyDisconnection(Player disconnectingPlayer, boolean isLastPlayer){
        pingAllGameObservers();
        for (ArrayList<GameObserver> observers : gameObservers.values()) {
            for (GameObserver observer : observers) {
                try {
                    observer.onPlayerDisconnection(disconnectingPlayer, isLastPlayer);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void notifyReconnection(Player reconnectingPlayer){
        pingAllGameObservers();
        for (ArrayList<GameObserver> observers : gameObservers.values()) {
            for (GameObserver observer : observers) {
                try {
                    observer.onPlayerReconnection(reconnectingPlayer);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public ReconnectionResult reconnection(int playerHashCode, GameObserver gameObserver) {
        System.out.println(playerHashCode + " is trying to reconnect");

        if (gameStatus.equals(GameStatus.PANEL_CHOICE)) return new ReconnectionResult(false,
                "You can't reconnect during game initialization. Try again later!", null);
        if (!gameStatus.equals(GameStatus.ACTIVE)) return new ReconnectionResult(false,
                "The game you're trying to reconnect has already ended.", null);
        Player player = getPlayerByHashcode(playerHashCode);
        System.out.println("active " + player.getPlayerStatus());
        if (player == null || !player.getPlayerStatus().equals(PlayerStatus.INACTIVE))
            return new ReconnectionResult(false, "Permission denied.", null);
        //TODO notify other users
        player.setPlayerStatus(PlayerStatus.ACTIVE);
        notifyReconnection(player);
        attachGameObserver(gameObserver, playerHashCode);
        return new ReconnectionResult(true, "Reconnection completed!",
                new GameStartMessage(draftPool, toolCards, publicObjectiveCards,
                        players, roundTrack, players.get(getCurrentPlayerIndex())));
    }

    public boolean pingAllLobbyObservers(){
        boolean result = true;
        for(Integer playerHashCode : lobbyObservers.keySet()){
            try {
                lobbyObservers.get(playerHashCode).rmiPing();
            } catch (ConnectException e) {
                System.out.println("--> detected player disconnection, username = " + getPlayerByHashcode(playerHashCode).getUsername());
                leaveLobby(getPlayerByHashcode(playerHashCode).getUsername(), lobbyObservers.get(playerHashCode));
                if(players.size() < 2){
                    result = false;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public void stopWaitingForPanelChoice(){
        waitingForPanelChoice = false;
    }

    public synchronized void pingAllGameObservers(){
        for (ArrayList<GameObserver> obs : gameObservers.values()){
            for(GameObserver gameObserver : obs){
                try {
                    rmiPing(gameObserver);
                } catch (ConnectException e){
                    System.out.println("DISCONNECTING DUE TO PING DETECT");
                    int hash = getPlayerHashCodeByGameObserver(gameObserver);
                    disconnect(hash);
                }
                catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
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
