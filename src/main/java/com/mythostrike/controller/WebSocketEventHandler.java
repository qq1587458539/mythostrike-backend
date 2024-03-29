package com.mythostrike.controller;

import com.mythostrike.controller.message.game.CardMoveMessage;
import com.mythostrike.model.game.activity.cards.HandCards;
import com.mythostrike.model.game.management.GameManager;
import com.mythostrike.model.lobby.Lobby;
import com.mythostrike.model.lobby.LobbyList;
import com.mythostrike.model.lobby.LobbyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Thread.sleep;

@Slf4j
@RequiredArgsConstructor
@Component
public class WebSocketEventHandler {

    public static final int SLEEP_BEFORE_RESPONSE = 50;
    private final GameController gameController;

    private final LobbyController lobbyController;

    private final LobbyList lobbyList = LobbyList.getLobbyList();

    @EventListener
    /**
     * If a client subscribes to a lobby, all players get an update lobby message.
     * If a client subscribes to a game, all players get an update game message.
     *   The connected players are increased.
     *   If all players are connected after the championselection, the game starts.
     * If a client subscribes to a game private, the player gets his current handcards send.
     */
    private void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        //get path where client subscribed
        String path = (String) event.getMessage().getHeaders().get("simpDestination");
        if (path == null) return;
        log.debug("client subscribed to '{}'", path);


        //check if path matches /lobbies/{lobbyId} and extract lobbyId
        Pattern lobbyPattern = Pattern.compile("/lobbies/(\\d+)");
        Matcher lobbyMatcher = lobbyPattern.matcher(path);

        //or if it matches /games/{lobbyId} and extract lobbyId
        Pattern gamePattern = Pattern.compile("/games/(\\d+)");
        Matcher gameMatcher = gamePattern.matcher(path);
        //or if it matches /lobbies/{lobbyId}/{username} and extract lobbyId and username
        Pattern gamePrivatePattern = Pattern.compile("/games/(\\d+)/(.+)");
        Matcher gamePrivateMatcher = gamePrivatePattern.matcher(path);

        if (lobbyMatcher.matches()) {
            //send update Lobby message to client
            int lobbyId = Integer.parseInt(lobbyMatcher.group(1));

            //shortly wait, sometimes the client is not ready to receive the game update
            try {
                sleep(SLEEP_BEFORE_RESPONSE);
            } catch (InterruptedException e) {
                log.error("could not sleep", e);
            }
            lobbyController.updateLobby(lobbyId);
        } else if (gameMatcher.matches()) {
            //send update Game message to client and increase the number of connected players
            int lobbyId = Integer.parseInt(gameMatcher.group(1));

            //shortly wait, sometimes the client is not ready to receive the game update
            try {
                sleep(SLEEP_BEFORE_RESPONSE);
            } catch (InterruptedException e) {
                log.error("could not sleep", e);
            }
            //send an game update to client
            gameController.updateGame(lobbyId);
        } else if (gamePrivateMatcher.matches()) {
            //increase the number of connected players
            int lobbyId = Integer.parseInt(gamePrivateMatcher.group(1));
            addUserToGame(lobbyId);

            //send current Handcards to client
            /* currently not needed. Maybe in the future to enable reconnecting
            String username = gamePrivateMatcher.group(2);
            sendCurrentHandcards(lobbyId, username);
            */
        }
    }

    /**
     * If all players are connected after the championselection, the game starts.
     *
     * @param lobbyId id of the lobby
     */
    private void addUserToGame(int lobbyId) {
        lobbyList.increaseUserInGame(lobbyId);

        //when all players are connected start the normal game procedure
        if (lobbyList.isGameReadyToStart(lobbyId)) {
            lobbyList.setLobbyGameRunning(lobbyId);
            log.debug("all players are connected");
            GameManager gameManager = lobbyList.getGameManager(lobbyId);
            if (gameManager == null) {
                log.error("gameManager is null, but enough players are in game");
                return;
            }
            gameManager.allPlayersConnected();
            gameController.updateGame(lobbyId);
        }
    }

    /**
     * Send the client his current handcards.
     *
     * @param lobbyId  id of the lobby
     * @param username username of the client
     */
    private void sendCurrentHandcards(int lobbyId, String username) {
        //shortly wait, sometimes the client is not ready to receive the game update
        try {
            sleep(SLEEP_BEFORE_RESPONSE);
        } catch (InterruptedException e) {
            log.error("could not sleep");
        }
        GameManager gameManager = lobbyList.getGameManager(lobbyId);
        if (gameManager == null) throw new IllegalStateException("gameManager is null, but game is running");

        HandCards handCards = gameManager.getPlayerByName(username).getHandCards();
        //if no handcards are available, return
        if (handCards.size() == 0) return;

        //otherwise send his current handcards to the client
        CardMoveMessage cardMoveMessage = new CardMoveMessage(
            username, username, handCards.size(),
            GameManager.convertCardsToInteger(handCards.getCards())
        );

        gameController.cardMove(lobbyId, List.of(username), cardMoveMessage);
    }

    @EventListener
    /**
     * If a client unsubscribes from a lobby, the client is removed from the lobby.
     * If a client unsubscribes from a game, the client still stays in the game and can reconnect.
     *   While he is disconnected, the game will continue without him. His rounds will be terminated with the timebar.
     *   The number of connected players is counted down. If no players are connected, the game will be terminated.
     */
    private void handleSessionUnsubscribeEvent(SessionUnsubscribeEvent event) {
        //get path where client subscribed
        String path = (String) event.getMessage().getHeaders().get("simpSubscriptionId");
        if (path == null) return;
        log.debug("client unsubscribed from '{}'", path);

        //check if path matches /lobbies/{lobbyId}/{username} and extract lobbyId and username
        Pattern lobbyPrivatePattern = Pattern.compile("/lobbies/(\\d+)/(.+)");
        Matcher lobbyPrivateMatcher = lobbyPrivatePattern.matcher(path);
        //or if it matches /games/{lobbyId}/{username} and extract lobbyId
        Pattern gamePrivatePattern = Pattern.compile("/games/(\\d+)/(.+)");
        Matcher gamePrivateMatcher = gamePrivatePattern.matcher(path);


        if (lobbyPrivateMatcher.matches()) {
            //remove player from lobby
            int lobbyId = Integer.parseInt(lobbyPrivateMatcher.group(1));
            String username = lobbyPrivateMatcher.group(2);
            Lobby lobby = lobbyList.getLobby(lobbyId);

            //lobby could be closed if player left the lobby
            //don't remove player from lobby if champion selection is running,
            //because client disconnects and reconnects to the game websocket
            if (lobby == null || lobby.getStatus() == LobbyStatus.CHAMPION_SELECTION) {
                return;
            }
            //otherwise remove player from lobby, if he was removed from the lobby, send an update to the client
            if (!lobbyList.removeUser(lobbyId, username)) {
                lobbyController.updateLobby(lobbyId);
            }
        } else if (gamePrivateMatcher.matches()) {
            //decrease number of connected players
            int lobbyId = Integer.parseInt(gamePrivateMatcher.group(1));
            lobbyList.decreaseUserInGame(lobbyId);
        }
    }

    @EventListener
    /**
     * If a client unsubscribes from a lobby, the client is removed from the lobby.
     * If a client unsubscribes from a game, the client still stays in the game and can reconnect.
     *   While he is disconnected, the game will continue without him. His rounds will be terminated with the timebar.
     *   The number of connected players is counted down. If no players are connected, the game will be terminated.
     */
    private void handleSessionDisconnectEvent(SessionDisconnectEvent event) {
        //get path where client subscribed
        String path = (String) event.getMessage().getHeaders().get("simpDestination");
        if (path == null) return;
        log.debug("client disconnected from '{}'", path);

        /*//check if path matches /lobbies/{lobbyId}/{username} and extract lobbyId and username
        Pattern lobbyPrivatePattern = Pattern.compile("/lobbies/(\\d+)/(.+)");
        Matcher lobbyPrivateMatcher = lobbyPrivatePattern.matcher(path);
        //or if it matches /games/{lobbyId} and extract lobbyId
        Pattern gamePattern = Pattern.compile("/games/(\\d+)");
        Matcher gameMatcher = gamePattern.matcher(path);


        if (lobbyPrivateMatcher.matches()) {
            //remove player from lobby
            int lobbyId = Integer.parseInt(lobbyPrivateMatcher.group(1));
            String username = lobbyPrivateMatcher.group(2);
            if (!lobbyList.removeUser(lobbyId, username)) {
                lobbyController.updateLobby(lobbyId);
            }
        } else if (gameMatcher.matches()) {
            //decrease number of connected players
            int lobbyId = Integer.parseInt(gameMatcher.group(1));
            lobbyList.decreaseUserInGame(lobbyId);
        }*/
    }
}
