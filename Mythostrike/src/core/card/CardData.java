package core.card;

import core.game.Effect;
import core.game.Player;
import core.game.management.GameManager;
import core.skill.events.handle.*;
import core.skill.events.type.EventTypeAttack;

import java.util.ArrayList;
import java.util.function.Function;


public enum CardData {
    ATTACK("Attack", "pick a player, he has to play defend or get 1 damage", CardType.BASIC,    new Effect<>(new Function<CardUseHandle, Boolean>() {
        @Override
        public Boolean apply(CardUseHandle handle) {
            Player player = handle.getFrom();
            ArrayList<Player> targets = new ArrayList<>();
            for (Player target : handle.getGameManager().getGame().getOtherPlayers(player)) {
                if (!target.equals(player) && target.isAlive() && Boolean.FALSE.equals(target.getImmunity().get(ATTACK))) {
                    targets.add(target);
                }
            }

            return !targets.isEmpty() && player.getRestrict().get(ATTACK) > 0;
        }
    }, new Function<CardUseHandle, Boolean>() {
        @Override
        public Boolean apply(CardUseHandle cardUseHandle) {
            Player player = cardUseHandle.getFrom();
            GameManager gameManager = cardUseHandle.getGameManager();
            //add targetAble enemy into targets
            ArrayList<Player> targets = new ArrayList<>();
            for (Player target : gameManager.getGame().getOtherPlayers(player)) {
                if (!target.equals(player) && target.isAlive() && Boolean.FALSE.equals(target.getImmunity().get(ATTACK))) {
                    targets.add(target);
                }
            }
            //ask player to pick a target from Attack
            ArrayList<Player> pickTarget = gameManager.getGameController().askForChosePlayer(player, targets, 1, 1, true, "pick a player to attack");
            if (pickTarget.isEmpty()) {
                return false;
            }
            if (!gameManager.getGameController().askForConfirm(player, "Confirm your Attack")) {
                return false;
            }
            cardUseHandle.setCardUseConfirmed(true);
            for (Player target : pickTarget) {
                AttackHandle attackHandle = new AttackHandle(gameManager, cardUseHandle.getCard(), "", player, target, 0);
                gameManager.getEventManager().triggerEvent(EventTypeAttack.ATTACK_EFFECTED, attackHandle);
                if (attackHandle.isPrevented()) {
                    return true;
                }
                gameManager.getEventManager().triggerEvent(EventTypeAttack.ATTACK_HIT, attackHandle);
                CardAskHandle cardAskHandle = attackHandle.getDefendAskHandle();
                if (gameManager.getGameController().askForDiscard(cardAskHandle)) {
                    attackHandle.setPrevented(true);
                } else {
                    attackHandle.setPrevented(false);
                    DamageHandle damageHandle = new DamageHandle(cardUseHandle.getGameManager(), cardUseHandle.getCard(), "attack damaged", player, target, 1 + attackHandle.getExtraDamage(), DamageType.NORMAL);
                    attackHandle.setDamageHandle(damageHandle);
                    gameManager.getPlayerManager().applyDamage(damageHandle);
                }
            }
            return true;
        }
    })),
    DEFEND("Defend", "use when you are getting Attack, prevent the damage of Attack", CardType.BASIC, new Effect<>(false, false)),
    BLESS_OF_HECATE("Bless of Hecate", "draw 2 cards", CardType.SKILL, new Effect<>(new Function<CardUseHandle, Boolean>() {
        @Override
        public Boolean apply(CardUseHandle cardUseHandle) {
            return cardUseHandle.getFrom().getRestrict().get(BLESS_OF_HECATE) > 0;
        }
    }, new Function<CardUseHandle, Boolean>() {
        @Override
        public Boolean apply(CardUseHandle cardUseHandle) {
            cardUseHandle.getGameManager().getCardManager().drawCard(new CardDrawHandle(cardUseHandle.getGameManager(), null, "uses bless of hecate, draw 2 cards", cardUseHandle.getFrom(), 2, cardUseHandle.getGameManager().getGame().getDrawDeck()));
            cardUseHandle.setCardUseConfirmed(true);
            return true;
        }
    })),
    GOLDEN_APPLE("Golden Apple", "everyone in game heals 1 hp", CardType.SKILL, new Effect<>(new Function<CardUseHandle, Boolean>() {
        @Override
        public Boolean apply(CardUseHandle cardUseHandle) {
            return cardUseHandle.getFrom().getRestrict().get(GOLDEN_APPLE) > 0;
        }
    }, new Function<CardUseHandle, Boolean>() {
        @Override
        public Boolean apply(CardUseHandle cardUseHandle) {
            for (Player player : cardUseHandle.getGameManager().getGame().getPlayers()){
                cardUseHandle.getGameManager().getPlayerManager().applyDamage(new DamageHandle(cardUseHandle.getGameManager(), cardUseHandle.getCard(), "heals 1 hp by getting golden apple", cardUseHandle.getFrom(), player, -1, DamageType.NORMAL));
            }
            return true;
        }
    }))
    ;

    private final String name;
    private final String description;
    private final CardType type;
    private Effect<CardUseHandle> effect;

    CardData(String name, String description, CardType type, Effect<CardUseHandle> effect) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.effect = effect;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CardType getType() {
        return type;
    }

    public boolean isPlayable(CardUseHandle cardUseHandle) {
        return effect.checkCondition(cardUseHandle);
    }

    public boolean apply(CardUseHandle cardUseHandle) {
        return effect.effect(cardUseHandle);
    }

}
