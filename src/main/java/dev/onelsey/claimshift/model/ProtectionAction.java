
package dev.onelsey.claimshift.model;

public enum ProtectionAction {
    BLOCK_BREAK("block-break"),
    BLOCK_PLACE("block-place"),
    CONTAINERS("containers"),
    CONTAINER_AUTOMATION("container-automation"),
    INTERACTIONS("interactions"),
    ENTITY_DAMAGE("entity-damage"),
    ENTITY_INTERACT("entity-interact"),
    ENTITY_GRIEF("entity-grief"),
    HANGING("hanging"),
    BUCKETS("buckets"),
    EXPLOSIONS("explosions"),
    PISTONS("pistons"),
    FLUIDS("fluids"),
    FIRE("fire");

    private final String configKey;

    ProtectionAction(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
