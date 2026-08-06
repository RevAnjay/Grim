package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.player.GrimPlayer;

@CheckData(name = "EntityPierce", configName = "EntityPierce", setback = 30)
public class EntityPierce extends Check {
    public EntityPierce(GrimPlayer player) {
        super(player);
    }
}
