package core.management;

import skill.events.handle.DamageHandle;
import skill.events.handle.DamageType;
import skill.events.type.EventTypeDamage;

public class PlayerManager {

    private GameManager gameManager;

    public PlayerManager(GameManager gameManager){
        this.gameManager = gameManager;
    }
    public void applyDamage(DamageHandle damageHandle){
        if (damageHandle.getDamage() > 0) {
                    /*
    CONFIRM_DAMAGE,
    DAMAGE_FORESEEN,
    DAMAGE_CAUSED,
    DAMAGE_INFLICTED,
    BEFORE_DAMAGE_DONE,
    DAMAGE_DONE,
    DAMAGE,
    DAMAGED,
    DAMAGE_COMPLETE,
         */
            gameManager.getEventManager().triggerEvent(EventTypeDamage.CONFIRM_DAMAGE, damageHandle);
            gameManager.getEventManager().triggerEvent(EventTypeDamage.DAMAGE_FORESEEN, damageHandle);
            gameManager.getEventManager().triggerEvent(EventTypeDamage.DAMAGE_CAUSED, damageHandle);




            if (!damageHandle.isPrevented()) {
                gameManager.getEventManager().triggerEvent(EventTypeDamage.DAMAGE_INFLICTED, damageHandle);
                gameManager.getEventManager().triggerEvent(EventTypeDamage.BEFORE_DAMAGE_DONE, damageHandle);
                gameManager.getEventManager().triggerEvent(EventTypeDamage.DAMAGE_DONE, damageHandle);
                //reduce hp
                damageHandle.getTo().setCurrentHp(damageHandle.getTo().getCurrentHp() - damageHandle.getDamage());

                //output message
                String hint = "Player " + damageHandle.getFrom().getName();
                hint += " deals " + damageHandle.getDamage() + " ";
                if (!damageHandle.getDamageType().equals(DamageType.NORMAL)) {
                    hint += damageHandle.getDamageType().toString();
                }
                hint += " damage to Player " + damageHandle.getTo().getName();
                hint += ", ouch! And he has now " + damageHandle.getTo().getCurrentHp() + " HP.";
                gameManager.getGame().output(hint);
                gameManager.getEventManager().triggerEvent(EventTypeDamage.DAMAGE, damageHandle);
                gameManager.getEventManager().triggerEvent(EventTypeDamage.DAMAGED, damageHandle);
                gameManager.getEventManager().triggerEvent(EventTypeDamage.DAMAGE_COMPLETE, damageHandle);
            }

        } else {
            //TODO heal
        }

    }
}