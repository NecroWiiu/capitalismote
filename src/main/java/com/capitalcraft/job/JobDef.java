package com.capitalcraft.job;

import java.util.List;

public final class JobDef {
    public record KitEntry(String item, int count) {}

    public final String id;
    public final int tier;
    public final long wage;
    public final boolean requiresGpu;
    public final List<KitEntry> kit;
    public final List<Objective> objectives;

    public JobDef(String id, int tier, long wage, boolean requiresGpu, List<KitEntry> kit, List<Objective> objectives) {
        this.id = id;
        this.tier = tier;
        this.wage = wage;
        this.requiresGpu = requiresGpu;
        this.kit = kit;
        this.objectives = objectives;
    }
}
