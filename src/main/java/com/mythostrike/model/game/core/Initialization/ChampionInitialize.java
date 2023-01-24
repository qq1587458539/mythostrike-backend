package com.mythostrike.model.game.core.Initialization;

import com.mythostrike.model.game.core.Champion;
import com.mythostrike.model.game.core.management.EventManager;
import com.mythostrike.model.game.skill.Skill;

import java.util.ArrayList;
import java.util.List;

public class ChampionInitialize {


    public static List<Champion> initialize(EventManager eventManager) {
        List<Champion> list = new ArrayList<>();
        List<Skill> skillList = SkillInitialize.initialize(eventManager);
        List<Skill> skills = new ArrayList<>();
        skills.add(SkillInitialize.getSkillByName(skillList, "revenge"));
        list.add(new Champion("Achilles", "", 4, skills));
        skills = new ArrayList<>();
        list.add(new Champion("Kratos", "", 5, skills));
        skills = new ArrayList<>();
        list.add(new Champion("Terpsichore", "", 3, skills));
        skills = new ArrayList<>();
        list.add(new Champion("Poseidon", "", 3, skills));
        skills = new ArrayList<>();
        list.add(new Champion("Mars", "", 4, skills));
        skills = new ArrayList<>();
        list.add(new Champion("Heracles", "", 4, skills));


        return list;
    }

}
