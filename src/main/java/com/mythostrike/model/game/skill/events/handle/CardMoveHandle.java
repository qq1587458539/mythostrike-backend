package com.mythostrike.model.game.skill.events.handle;


import com.mythostrike.model.game.core.CardList;
import com.mythostrike.model.game.core.Player;
import com.mythostrike.model.game.core.activity.Card;
import com.mythostrike.model.game.core.management.GameManager;

public class CardMoveHandle extends EventHandle {
    Player to;
    CardList fromList;
    CardList toList;

    public CardMoveHandle(GameManager gameController, Card card, String reason, Player from, Player to,
                          CardList fromList, CardList toList) {
        super(gameController, card, reason, from);
        this.to = to;
        this.fromList = fromList;
        this.toList = toList;
    }

    public boolean isPlayerFromRelevant() {
        return super.getFrom() == null;
    }

    public boolean isPlayerToRelevant() {
        return to == null;
    }

    public Player getTo() {
        return to;
    }

    public void setTo(Player to) {
        this.to = to;
    }

    public CardList getFromList() {
        return fromList;
    }

    public void setFromList(CardList fromList) {
        this.fromList = fromList;
    }

    public CardList getToList() {
        return toList;
    }

    public void setToList(CardList toList) {
        this.toList = toList;
    }
}
