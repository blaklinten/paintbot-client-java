package se.cygni.paintbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.WebSocketSession;
import se.cygni.paintbot.api.event.*;
import se.cygni.paintbot.api.exception.InvalidPlayerName;
import se.cygni.paintbot.api.model.CharacterAction;
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
    //private static final String SERVER_NAME = "server.paintbot.cygni.se";
    //private static final int SERVER_PORT = 80;
    private static final String SERVER_NAME = "localhost";
    private static final int SERVER_PORT = 8080;

    private static final GameMode GAME_MODE = GameMode.TRAINING;
    private static final String BOT_NAME = "Lickan Super Awesome Bot";

    // Set to false if you don't want the game world printed every game tick.
    private static final boolean ANSI_PRINTER_ACTIVE = false;
    private AnsiPrinter ansiPrinter = new AnsiPrinter(ANSI_PRINTER_ACTIVE, true);

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

        // MapUtil contains lot's of useful methods for querying the map!
        MapUtility mapUtil = new MapUtilityImpl(mapUpdateEvent.getMap(), getPlayerId());

        MapCoordinate[] powerUpList = mapUtil.getCoordinatesContainingPowerUps();
		int min = Integer.MAX_VALUE;
		MapCoordinate currentPosition = mapUtil.getMyCoordinate();
		MapCoordinate closestPowerUp = new MapCoordinate(0,0);
		for (MapCoordinate powerUp : powerUpList){
			int currentDistance = currentPosition.getManhattanDistanceTo(powerUp);
			if (currentDistance < min){
				min = currentDistance;
				closestPowerUp = powerUp;
			}
		}


        // Register action here!currenDistance
        registerMove(mapUpdateEvent.getGameTick(), CharacterAction.RIGHT);
    }

	/* Our functions
 	 *
 	 */


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
