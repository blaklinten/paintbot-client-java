package se.cygni.paintbot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.WebSocketSession;
import se.cygni.paintbot.api.event.*;
import se.cygni.paintbot.api.exception.InvalidPlayerName;
import se.cygni.paintbot.api.model.CharacterAction;
import se.cygni.paintbot.api.model.CharacterInfo;
import se.cygni.paintbot.api.model.GameMode;
import se.cygni.paintbot.api.model.GameSettings;
import se.cygni.paintbot.api.model.PlayerPoints;
import se.cygni.paintbot.api.response.PlayerRegistered;
import se.cygni.paintbot.api.util.GameSettingsUtils;
import se.cygni.paintbot.client.AnsiPrinter;
import se.cygni.paintbot.client.BasePaintbotClient;
import se.cygni.paintbot.client.MapCoordinate;
import se.cygni.paintbot.client.MapUtility;
import se.cygni.paintbot.client.MapUtilityImpl;

public class LickanBot extends BasePaintbotClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(LickanBot.class);

    // Set to false if you want to start the game from a GUI
    private static final boolean AUTO_START_GAME = true;

    // Personalise your game ...
    private static final String SERVER_NAME = "server.paintbot.cygni.se";
    private static final int SERVER_PORT = 80;
//    private static final String SERVER_NAME = "localhost";
//    private static final int SERVER_PORT = 8080;

    private static final GameMode GAME_MODE = GameMode.TOURNAMENT;
    private static final String BOT_NAME = "Lickan Super Awesome Bot";

    // Set to false if you don't want the game world printed every game tick.
    private static final boolean ANSI_PRINTER_ACTIVE = false;
    private AnsiPrinter ansiPrinter = new AnsiPrinter(ANSI_PRINTER_ACTIVE, true);
    
    public static boolean isAvoidingObstacle = false;
    public static CharacterAction tempAction = null;
    public static int avoidTick = 0;

    public static void main(String[] args) {
        LickanBot lickanBot = new LickanBot();

        try {
            ListenableFuture<WebSocketSession> connect = lickanBot.connect();
            connect.get();
        } catch (Exception e) {
            LOGGER.error("Failed to connect to server", e);
            System.exit(1);
        }

        startTheBot(lickanBot);
    }

    /**
     * The Paintbot client will continue to run ...
     * : in TRAINING mode, until the single game ends.
     * : in TOURNAMENT mode, until the server tells us its all over.
     */
    private static void startTheBot(final LickanBot lickanBot) {
        Runnable task = () -> {
            do {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (lickanBot.isPlaying());

            LOGGER.info("Shutting down");
        };

        Thread thread = new Thread(task);
        thread.start();
    }

    @Override
    public void onMapUpdate(MapUpdateEvent mapUpdateEvent) {
        // Do your implementation here! (or at least start from here, entry point for updates)
        //ansiPrinter.printMap(mapUpdateEvent);

		//List<Integer> moves = new ArrayList<Integer>(5);

        if(isAvoidingObstacle && avoidTick > 0){
            avoidTick -= 1;
            if (avoidTick == 0) {
                isAvoidingObstacle = false;
                registerMove(mapUpdateEvent.getGameTick(), tempAction);
                return;
            }
        }

        // MapUtil contains lot's of useful methods for querying the map!
        MapUtility mapUtil = new MapUtilityImpl(mapUpdateEvent.getMap(), getPlayerId());

		CharacterAction action = getBestAction(mapUtil);

        registerMove(mapUpdateEvent.getGameTick(), action);
    }

	CharacterAction getActionFromIndex(int index){
		switch (index){
		case 0: return CharacterAction.LEFT;
		case 1: return CharacterAction.RIGHT;
		case 2: return CharacterAction.DOWN;
		case 3: return CharacterAction.UP;
		case 4: return CharacterAction.EXPLODE;
		default: return CharacterAction.STAY;
		}
	}

	/* Our functions
 	 *
 	 */

	List<Integer> calculateActionValues(MapUtility mapUtil){
		List<Integer> actionValues = new ArrayList<Integer>(5);
		Pair<MapCoordinate, Integer> closestPowerUp = findClosestpowerUp(mapUtil);
		MapCoordinate closestPowerUpCoordinate = closestPowerUp.getLeft();
		Integer closestPowerUpDistance = closestPowerUp.getRight();

		actionValues.add(0, evaluateDirection(CharacterAction.LEFT, closestPowerUpCoordinate, closestPowerUpDistance,  mapUtil));
		actionValues.add(1, evaluateDirection(CharacterAction.RIGHT, closestPowerUpCoordinate, closestPowerUpDistance, mapUtil));
		actionValues.add(2, evaluateDirection(CharacterAction.DOWN, closestPowerUpCoordinate, closestPowerUpDistance, mapUtil));
		actionValues.add(3, evaluateDirection(CharacterAction.UP, closestPowerUpCoordinate, closestPowerUpDistance, mapUtil));
		actionValues.add(4, evaluateExplode(mapUtil));

		return actionValues;
	}

	Integer evaluateDirection(CharacterAction direction, MapCoordinate closestPowerUpCoordinate, Integer closestPowerUpDistance, MapUtility mapUtil){
	
		Integer totalScore = 0;
		MapCoordinate possibleNextCoordinate = mapUtil.getMyCoordinate().translateByAction(direction);
		if (possibleNextCoordinate.getManhattanDistanceTo(closestPowerUpCoordinate) < closestPowerUpDistance){totalScore = totalScore + 2;}
		return totalScore;
	}

	Integer evaluateExplode(MapUtility mapUtil){
		// can I explode? if so, is it a good time?
		Integer totalScore = 0;

		if(mapUtil.getMyCharacterInfo().isCarryingPowerUp()){
			totalScore = totalScore + 5;
		}
		return totalScore;
	}

	Pair<MapCoordinate, Integer> findClosestpowerUp(MapUtility mapUtil){
        MapCoordinate[] powerUpList = mapUtil.getCoordinatesContainingPowerUps();
		Integer min = Integer.MAX_VALUE;
		MapCoordinate currentPosition = mapUtil.getMyCoordinate();
		MapCoordinate closestPowerUpCoordinate = new MapCoordinate(0,0);

		for (MapCoordinate powerUpCoordinate : powerUpList){
			int currentDistance = currentPosition.getManhattanDistanceTo(powerUpCoordinate);
			if (currentDistance < min){
				min = currentDistance;
				closestPowerUpCoordinate = powerUpCoordinate;
			}
		}
		return Pair.of(closestPowerUpCoordinate, min);
	}

	CharacterAction getBestAction(MapUtility mapUtil){
        List<Integer> actionValues = calculateActionValues(mapUtil);

        int     maxIndex = 0;
        Integer maxValue = 0;

        for (int i  = 0; i < actionValues.size(); i++){
            Integer currentvalue = actionValues.get(i);
            if ( currentvalue > maxValue){
                maxValue = currentvalue;
                maxIndex = i;
            }
        }

        CharacterAction bestAction = getActionFromIndex(maxIndex);
        if(bestAction == CharacterAction.LEFT || bestAction == CharacterAction.RIGHT || bestAction == CharacterAction.DOWN || bestAction == CharacterAction.UP){
            if(!mapUtil.canIMoveInDirection(bestAction)){

                isAvoidingObstacle = true;
                avoidTick = 4;

                actionValues.set(maxIndex, 0);
                tempAction = getBestAction(mapUtil);
                return tempAction;
            }
        }
        return getActionFromIndex(maxIndex);
	}

	/* End our functions
 	 *
 	 */

    @Override
    public void onPaintbotDead(CharacterStunnedEvent characterStunnedEvent) {
        // Wrong name, does not die. Might be stunned though by crashing or getting caught in explosion
    }


    @Override
    public void onInvalidPlayerName(InvalidPlayerName invalidPlayerName) {
        LOGGER.debug("InvalidPlayerNameEvent: " + invalidPlayerName);
    }

    @Override
    public void onGameResult(GameResultEvent gameResultEvent) {
        LOGGER.info("Game result:");
        gameResultEvent.getPlayerRanks().forEach(playerRank -> LOGGER.info(playerRank.toString()));
    }

    @Override
    public void onGameEnded(GameEndedEvent gameEndedEvent) {
        LOGGER.debug("GameEndedEvent: " + gameEndedEvent);
    }

    @Override
    public void onGameStarting(GameStartingEvent gameStartingEvent) {
        LOGGER.debug("GameStartingEvent: " + gameStartingEvent);
    }

    @Override
    public void onPlayerRegistered(PlayerRegistered playerRegistered) {
        LOGGER.info("PlayerRegistered: " + playerRegistered);

        if (AUTO_START_GAME) {
            startGame();
        }
    }

    @Override
    public void onTournamentEnded(TournamentEndedEvent tournamentEndedEvent) {
        LOGGER.info("Tournament has ended, winner playerId: {}", tournamentEndedEvent.getPlayerWinnerId());
        int c = 1;
        for (PlayerPoints pp : tournamentEndedEvent.getGameResult()) {
            LOGGER.info("{}. {} - {} points", c++, pp.getName(), pp.getPoints());
        }
    }

    @Override
    public void onGameLink(GameLinkEvent gameLinkEvent) {
        LOGGER.info("The game can be viewed at: {}", gameLinkEvent.getUrl());
    }

    @Override
    public void onSessionClosed() {
        LOGGER.info("Session closed");
    }

    @Override
    public void onConnected() {
        LOGGER.info("Connected, registering for training...");
        GameSettings gameSettings = GameSettingsUtils.trainingWorld();
        registerForGame(gameSettings);
    }

    @Override
    public String getName() {
        return BOT_NAME;
    }

    @Override
    public String getServerHost() {
        return SERVER_NAME;
    }

    @Override
    public int getServerPort() {
        return SERVER_PORT;
    }

    @Override
    public GameMode getGameMode() {
        return GAME_MODE;
    }
}
