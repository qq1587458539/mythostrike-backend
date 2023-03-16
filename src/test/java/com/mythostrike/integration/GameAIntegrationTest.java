package com.mythostrike.integration;


import com.mythostrike.controller.message.game.PlayCardsRequest;
import com.mythostrike.controller.message.game.SelectChampionRequest;
import com.mythostrike.controller.message.game.UseSkillRequest;
import com.mythostrike.controller.message.lobby.ChampionSelectionMessage;
import com.mythostrike.controller.message.lobby.LobbyMessage;
import com.mythostrike.model.game.player.ChampionList;
import com.mythostrike.support.SimpleStompFrameHandler;
import com.mythostrike.support.StompFrameHandlerGame;
import com.mythostrike.support.TestUser;
import com.mythostrike.support.utility.LobbyUtils;
import com.mythostrike.support.utility.UserUtils;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.mythostrike.support.utility.GameUtils.*;
import static com.mythostrike.support.utility.LobbyUtils.WEB_SOCKET_WRONG_MESSAGE;
import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@Slf4j
class GameAIntegrationTest {
    private static final Integer PORT = 8080;
    private static final int I_ACHILLES = 0;
    private static final int I_HESTIA = 1;
    private static final int I_TERPISORE = 2;
    private static final int I_KRATOS = 3;
    private final List<TestUser> users = new ArrayList<>();
    private StompSession session;
    private int roundCounter;

    @BeforeAll
    static void restAssuredSetup() {
        RestAssured.requestSpecification = new RequestSpecBuilder().build()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
        RestAssured.port = PORT;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    void webSocketSetup() throws ExecutionException, InterruptedException, TimeoutException {
        WebSocketStompClient webSocketStompClient = new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));

        String webSocketPath = "ws://localhost:" + PORT + "/updates";
        webSocketStompClient.setMessageConverter(new MappingJackson2MessageConverter());
        this.session = webSocketStompClient
                .connectAsync(webSocketPath, new StompSessionHandlerAdapter() {
                })
                .get(1, SECONDS);
    }

    /**
     * before using the users, we need to create them and get their jwt tokens.
     * If the user does not exist, we register them.
     * Cannot be done in @BeforeAll because the server needs to be running.
     */
    @BeforeEach
    void setupUsers() {
        users.add(UserUtils.createUser("Achilles", "Achilles123"));
        assertFalse(users.get(I_ACHILLES).jwtToken().isEmpty());

        users.add(UserUtils.createUser("Hestia", "Hestia345"));
        assertFalse(users.get(I_HESTIA).jwtToken().isEmpty());

        users.add(UserUtils.createUser("Terpisore", "Terpisore678"));
        assertFalse(users.get(I_TERPISORE).jwtToken().isEmpty());

        users.add(UserUtils.createUser("Kratos", "Kratos901"));
        assertFalse(users.get(I_KRATOS).jwtToken().isEmpty());

        roundCounter = 1;
    }


    /**
     * Integration tests if we create a lobby and subscribe to it via websockets
     *
     * @throws Exception
     */
    @Test
    void wholeGameWith2Vs2() {
        int lobbyId = createLobbyAndStartGame();

        //subscribe to the public game Web Socket
        StompFrameHandlerGame publicGameWebSocket = new StompFrameHandlerGame();
        //wait for update game message and remove it from the queue
        session.subscribe(String.format("/games/%d", lobbyId), publicGameWebSocket);
        try {
            sleep(200);
        } catch (InterruptedException e) {
            log.debug("Interrupted while sleeping");
        }

        selectChampions(lobbyId, publicGameWebSocket);

        //connect to all private game web Sockets
        List<StompFrameHandlerGame> privateGameWebSockets = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            privateGameWebSockets.add(new StompFrameHandlerGame());
            session.subscribe(String.format("/games/%d/%s", lobbyId, users.get(i).username()),
                    privateGameWebSockets.get(i));
        }

        round1(lobbyId, privateGameWebSockets);
        round2(lobbyId, privateGameWebSockets);
        round3(lobbyId, privateGameWebSockets);
        round4(lobbyId, privateGameWebSockets);


    }

    private int createLobbyAndStartGame() {
        //create the lobby
        LobbyMessage expected = LobbyUtils.createLobby(users.get(I_ACHILLES), 0, false);

        int lobbyId = expected.id();

        //subscribe to the lobby
        SimpleStompFrameHandler<LobbyMessage> publicLobbyWebSocket = new SimpleStompFrameHandler<>(LobbyMessage.class);
        session.subscribe(String.format("/lobbies/%d", lobbyId), publicLobbyWebSocket);
        await()
                .atMost(1, SECONDS)
                .untilAsserted(() -> assertFalse(publicLobbyWebSocket.getMessages().isEmpty()));
        assertEquals(expected, publicLobbyWebSocket.getNextMessage(), WEB_SOCKET_WRONG_MESSAGE);

        List<SimpleStompFrameHandler<ChampionSelectionMessage>> privateLobbyWebSockets = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            privateLobbyWebSockets.add(new SimpleStompFrameHandler<>(ChampionSelectionMessage.class));
            session.subscribe(String.format("/lobbies/%d/%s", lobbyId, users.get(i).username()),
                    privateLobbyWebSockets.get(i));
        }

        //change the random seed of the lobby
        LobbyUtils.setRandomSeed(lobbyId, 300); //TODO: change to what seed you want

        //join the lobby
        expected = LobbyUtils.joinLobby(users.get(I_HESTIA), expected, false, publicLobbyWebSocket);

        expected = LobbyUtils.joinLobby(users.get(I_TERPISORE), expected, false, publicLobbyWebSocket);

        expected = LobbyUtils.joinLobby(users.get(I_KRATOS), expected, false, publicLobbyWebSocket);


        expected = LobbyUtils.changeMode(users.get(I_ACHILLES), expected, 2, false, publicLobbyWebSocket);


        //start the game
        LobbyUtils.startGame(users.get(I_ACHILLES), lobbyId, false, privateLobbyWebSockets);

        //TODO: check if for each user the champion selection message is received
        return lobbyId;
    }

    private void selectChampions(int lobbyId, StompFrameHandlerGame publicGameWebSocket) {
        //select champions for each user
        int championId = 1;
        String championName = ChampionList.getChampionList().getChampion(championId).getName();
        selectChampion(users.get(I_ACHILLES), new SelectChampionRequest(lobbyId, championId), championName,
                false, publicGameWebSocket);

        championId = 6;
        championName = ChampionList.getChampionList().getChampion(championId).getName();
        selectChampion(users.get(I_HESTIA), new SelectChampionRequest(lobbyId, championId), championName,
                false, publicGameWebSocket);

        championId = 3;
        championName = ChampionList.getChampionList().getChampion(championId).getName();
        selectChampion(users.get(I_TERPISORE), new SelectChampionRequest(lobbyId, championId), championName,
                false, publicGameWebSocket);

        championId = 4;
        championName = ChampionList.getChampionList().getChampion(championId).getName();
        selectChampion(users.get(I_KRATOS), new SelectChampionRequest(lobbyId, championId), championName,
                false, publicGameWebSocket);
    }


    private void round1(int lobbyId, List<StompFrameHandlerGame> privateGameWebSocketList) {
        startRound(lobbyId, privateGameWebSocketList.get(I_KRATOS), roundCounter++);

        //play extort error incorrect card ids
        PlayCardsRequest playExtorterror = new PlayCardsRequest(lobbyId, List.of(21,0), List.of(users.get(I_HESTIA).username()));
        requestPlayCards(users.get(I_KRATOS), playExtorterror, true, privateGameWebSocketList.get(I_KRATOS));
        //play extort error incorrect targets
        playExtorterror = new PlayCardsRequest(lobbyId, List.of(1057), List.of("Monkey","Gible","Relaxo"));
        requestPlayCards(users.get(I_KRATOS), playExtorterror, true, privateGameWebSocketList.get(I_KRATOS));
        //play extort
        playCardOnTarget(users.get(I_KRATOS), lobbyId, 1057, users.get(I_HESTIA).username(),
                privateGameWebSocketList.get(I_KRATOS));

        //play attack error incorrect card ids
        PlayCardsRequest playAttackError = new PlayCardsRequest(lobbyId, List.of(20,0), List.of(users.get(I_HESTIA).username()));
        requestPlayCards(users.get(I_KRATOS), playAttackError, true, privateGameWebSocketList.get(I_KRATOS));
        //play attack error incorrect targets
        playAttackError = new PlayCardsRequest(lobbyId, List.of(1057), List.of("pikachu","Infernape"));
        requestPlayCards(users.get(I_KRATOS), playAttackError, true, privateGameWebSocketList.get(I_KRATOS));
        //play attack
        playCardOnTarget(users.get(I_KRATOS), lobbyId, 1025, users.get(I_HESTIA).username(),
                privateGameWebSocketList.get(I_KRATOS));

        //play defend error incorrect card id
        PlayCardsRequest playDefendError = new PlayCardsRequest(lobbyId, List.of(3), List.of());
        requestPlayCards((users.get(I_HESTIA)), playDefendError, true, privateGameWebSocketList.get(I_HESTIA));
        //play defend error incorrect target
         playDefendError = new PlayCardsRequest(lobbyId, List.of(1049), List.of("Rayquaza"));
        requestPlayCards((users.get(I_HESTIA)), playDefendError, true, privateGameWebSocketList.get(I_HESTIA));
        //play defend from Hestia
        playCard(users.get(I_HESTIA), lobbyId, 1049, privateGameWebSocketList.get(I_HESTIA));

        //play Godarena error incorrect target
        PlayCardsRequest playGodarena = new PlayCardsRequest(lobbyId, List.of(1005), List.of("entai"));
        requestPlayCards((users.get(I_HESTIA)), playGodarena, true, privateGameWebSocketList.get(I_HESTIA));
        //Godarena
        playCard(users.get(I_KRATOS), lobbyId, 1005, privateGameWebSocketList.get(I_KRATOS));

        //play attack from Achilles
        playCard(users.get(I_ACHILLES), lobbyId, 1021, privateGameWebSocketList.get(I_ACHILLES));
        //skip from Hestia
        confirm(users.get(I_HESTIA), lobbyId, privateGameWebSocketList.get(I_HESTIA));
        //play attack from terpisore
        playCard(users.get(I_TERPISORE), lobbyId, 1012, privateGameWebSocketList.get(I_TERPISORE));

       //end turn
        endTurn(users.get(I_KRATOS), lobbyId, false, true, false, privateGameWebSocketList.get(I_KRATOS));

    }

    private void round2(int lobbyId, List<StompFrameHandlerGame> privateGameWebSocketList) {
        startRound(lobbyId, privateGameWebSocketList.get(I_ACHILLES), roundCounter++);

        //play drought error incorrect  target he picks himself
        PlayCardsRequest playDefendError = new PlayCardsRequest(lobbyId, List.of(1073), List.of(users.get(I_ACHILLES).username()));
        requestPlayCards((users.get(I_ACHILLES)), playDefendError, true, privateGameWebSocketList.get(I_ACHILLES));
        //play Drought on Kratos
        playCardOnTarget(users.get(I_ACHILLES), lobbyId, 1073, users.get(I_KRATOS).username(),
                privateGameWebSocketList.get(I_ACHILLES));

        //play Heart of Terra
        playCard(users.get(I_ACHILLES), lobbyId, 1087, privateGameWebSocketList.get(I_ACHILLES));

        //play attack on Kratos
        playCardOnTarget(users.get(I_ACHILLES), lobbyId, 1036, users.get(I_KRATOS).username(),
                privateGameWebSocketList.get(I_ACHILLES));

        //play heal as defend from kratos
        playCard(users.get(I_KRATOS), lobbyId, 1061, privateGameWebSocketList.get(I_KRATOS));

        //end turn
        endTurn(users.get(I_ACHILLES), lobbyId, false, true, false, privateGameWebSocketList.get(I_ACHILLES));
    }

    private void round3(int lobbyId, List<StompFrameHandlerGame> privateGameWebSocketList) {
        startRound(lobbyId, privateGameWebSocketList.get(I_HESTIA), roundCounter++);

        //play equip Spear of Mars
        playCard(users.get(I_HESTIA), lobbyId, 1080, privateGameWebSocketList.get(I_HESTIA));

        //play Vulcanic eruption
        playCard(users.get(I_HESTIA), lobbyId, 1075, privateGameWebSocketList.get(I_HESTIA));

        //skip from Terpisore
        confirm(users.get(I_TERPISORE), lobbyId, privateGameWebSocketList.get(I_TERPISORE));

        //skip from Kratos
        confirm(users.get(I_KRATOS), lobbyId, privateGameWebSocketList.get(I_KRATOS));

        //skip from Achilles
        confirm(users.get(I_ACHILLES), lobbyId, privateGameWebSocketList.get(I_ACHILLES));

        //error skill synergy order wrong skillId
        UseSkillRequest errorSkill = new UseSkillRequest(lobbyId, 100000, List.of());
        requestUseSkill(users.get(I_HESTIA), errorSkill, true, privateGameWebSocketList.get(I_HESTIA));
        //error skill synergy order wrong target
        errorSkill = new UseSkillRequest(lobbyId, 0, List.of("Dragonite"));
        requestUseSkill(users.get(I_HESTIA), errorSkill, true, privateGameWebSocketList.get(I_HESTIA));
        //skill synergy order
        useSkill(users.get(I_HESTIA), lobbyId, 0, privateGameWebSocketList.get(I_HESTIA));


        //play attack error wrong card Ids
        PlayCardsRequest playAttackError = new PlayCardsRequest(lobbyId, List.of(-1,-2), List.of());
        requestPlayCards(users.get(I_HESTIA), playAttackError, true, privateGameWebSocketList.get(I_HESTIA));
        //play attack error incorrect targets should have no target
        playAttackError = new PlayCardsRequest(lobbyId, List.of(1031, 1041), List.of("pikachu","Infernape"));
        requestPlayCards(users.get(I_HESTIA), playAttackError, true, privateGameWebSocketList.get(I_HESTIA));
        //play cards which are used to swap new cards because of skill
        playMultipleCards(users.get(I_HESTIA), lobbyId, List.of(1031, 1041),
                privateGameWebSocketList.get(I_HESTIA));

        //play Heal
        playCard(users.get(I_HESTIA), lobbyId, 1067, privateGameWebSocketList.get(I_HESTIA));

        //play attack on Kratos
        playCardOnTarget(users.get(I_HESTIA), lobbyId, 1015, users.get(I_KRATOS).username(),
                privateGameWebSocketList.get(I_HESTIA));

        //skip from Kratos
        confirm(users.get(I_KRATOS), lobbyId, privateGameWebSocketList.get(I_KRATOS));

        //end turn
        endTurn(users.get(I_HESTIA), lobbyId, false, true, false, privateGameWebSocketList.get(I_HESTIA));
    }

    private void round4(int lobbyId, List<StompFrameHandlerGame> privateGameWebSocketList) {
        startRound(lobbyId, privateGameWebSocketList.get(I_TERPISORE), roundCounter++);

        //play Attack on Achilles
        playCardOnTarget(users.get(I_TERPISORE), lobbyId, 1016, users.get(I_ACHILLES).username(),
                privateGameWebSocketList.get(I_TERPISORE));

        //skip from Achilles
        confirm(users.get(I_ACHILLES), lobbyId, privateGameWebSocketList.get(I_ACHILLES));

        //skill revenge von Achilles aktiviert
        useSkill(users.get(I_ACHILLES), lobbyId, 103, privateGameWebSocketList.get(I_ACHILLES));

        //play heal
        playCard(users.get(I_TERPISORE), lobbyId, 1063, privateGameWebSocketList.get(I_TERPISORE));

        //end turn
        endTurn(users.get(I_TERPISORE), lobbyId, false, true, true, privateGameWebSocketList.get(I_TERPISORE));
        //play disacrd error wrong card Ids
        PlayCardsRequest playDiscardError = new PlayCardsRequest(lobbyId, List.of(-1,-2), List.of());
        requestPlayCards(users.get(I_HESTIA), playDiscardError, true, privateGameWebSocketList.get(I_HESTIA));
        //play attack error incorrect targets should have no target
        playDiscardError = new PlayCardsRequest(lobbyId, List.of(1066), List.of("pikachu","Infernape"));
        requestPlayCards(users.get(I_HESTIA), playDiscardError, true, privateGameWebSocketList.get(I_HESTIA));
        //play cards which are used to swap new cards because of skill
        discardCard(users.get(I_TERPISORE), lobbyId, List.of(1066), privateGameWebSocketList.get(I_TERPISORE));
    }








}
