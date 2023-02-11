package com.mythostrike.model.game.activity.cards;

import com.mythostrike.model.exception.IllegalInputException;
import com.mythostrike.model.game.activity.Card;
import com.mythostrike.model.game.activity.cards.cardtype.Attack;
import com.mythostrike.model.lobby.Mode;
import com.mythostrike.model.lobby.ModeData;
import com.mythostrike.model.lobby.ModeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CardList {

    private static CardList instance;
    private final List<Card> cards;

    /**
     * This is a Singleton Class. The Constructor is private.
     * Use the getModeList() method to get the instance.
     * The ModeList is created from the Enum Values ModeData.
     */
    private CardList() {
        this.cards = new ArrayList<>();

        //initilize a complete card deck in the card list
        int id = 1000;
        //TODO: doppelte entfernen
        /*cards.add(new GoldenApple(id++, CardSymbol.HEART, 1));
        cards.add(new GoldenApple(id++, CardSymbol.HEART, 3));
        cards.add(new GoldenApple(id++, CardSymbol.HEART, 4));*/
        /*cards.add(new GodArena(id++, CardSymbol.CLUB, 7));
        cards.add(new GodArena(id++, CardSymbol.CLUB, 13));
        cards.add(new GodArena(id++, CardSymbol.CLUB, 12));
        cards.add(new GodArena(id++, CardSymbol.SPADE, 7));
        cards.add(new GodArena(id++, CardSymbol.SPADE, 13));*/
        cards.add(new Attack(id++, CardSymbol.HEART, 10));
        cards.add(new Attack(id++, CardSymbol.HEART, 10)); //doppelt
        cards.add(new Attack(id++, CardSymbol.HEART, 12));
        cards.add(new Attack(id++, CardSymbol.DIAMOND, 6));
        cards.add(new Attack(id++, CardSymbol.DIAMOND, 7));
        cards.add(new Attack(id++, CardSymbol.DIAMOND, 8));
        cards.add(new Attack(id++, CardSymbol.DIAMOND, 9));
        cards.add(new Attack(id++, CardSymbol.DIAMOND, 10));
        cards.add(new Attack(id++, CardSymbol.DIAMOND, 13));
        cards.add(new Attack(id++, CardSymbol.CLUB, 1));
        cards.add(new Attack(id++, CardSymbol.CLUB, 2));
        cards.add(new Attack(id++, CardSymbol.CLUB, 3));
        cards.add(new Attack(id++, CardSymbol.CLUB, 4));
        cards.add(new Attack(id++, CardSymbol.CLUB, 5));
        cards.add(new Attack(id++, CardSymbol.CLUB, 6));
        cards.add(new Attack(id++, CardSymbol.CLUB, 7));
        cards.add(new Attack(id++, CardSymbol.CLUB, 8));
        cards.add(new Attack(id++, CardSymbol.CLUB, 8)); //doppelt
        cards.add(new Attack(id++, CardSymbol.CLUB, 9));
        cards.add(new Attack(id++, CardSymbol.CLUB, 9)); //doppelt
        cards.add(new Attack(id++, CardSymbol.CLUB, 10));
        cards.add(new Attack(id++, CardSymbol.CLUB, 10)); //doppelt
        cards.add(new Attack(id++, CardSymbol.CLUB, 11));
        cards.add(new Attack(id++, CardSymbol.CLUB, 11)); //doppelt
        cards.add(new Attack(id++, CardSymbol.SPADE, 7));
    }

    public static CardList getCardList() {
        if (instance == null) {
            instance = new CardList();
        }
        return instance;
    }

    public @NotNull @UnmodifiableView List<Card> getCards() {
        return Collections.unmodifiableList(cards);
    }

    public CardPile getFullCardDeck() {
        CardPile cardPile = new CardPile();
        for (Card card : cards) {
            cardPile.add(card.deepCopy());
        }
        return cardPile;
    }

    public Card getCard(int id) throws IllegalInputException {
        if (id < 0 || id >= cards.size()) {
            throw new IllegalInputException(String.format("The id %d is not valid.", id));
        }
        return cards.get(id);
    }

}
